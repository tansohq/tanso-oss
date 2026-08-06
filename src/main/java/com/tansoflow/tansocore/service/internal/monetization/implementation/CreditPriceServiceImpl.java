/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.CreditPrice;
import com.tansoflow.tansocore.model.credit.CreditPriceDto;
import com.tansoflow.tansocore.model.credit.request.PublishCreditPricesRequest;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.exception.TariffConflictException;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.CreditModelRepository;
import com.tansoflow.tansocore.repository.CreditPriceRepository;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreditPriceServiceImpl implements CreditPriceService {

    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("[A-Z]{3}");
    static final String DEFAULT_CURRENCY = "USD";

    private final CreditPriceRepository creditPriceRepository;
    private final CreditModelRepository creditModelRepository;
    private final AccountRepository accountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedPrice> resolvePrice(UUID accountId, String denomination, Instant at) {
        return creditPriceRepository
                .findTopByAccountIdAndDenominationAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        accountId, denomination, at)
                .map(row -> new ResolvedPrice(row.getPricePerCredit(), row.getCurrency(), row.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditPriceDto> getPrices(String accountId) {
        return creditPriceRepository
                .findByAccountIdOrderByDenominationAscEffectiveFromDesc(UUID.fromString(accountId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditPriceDto> getCurrentPrices(String accountId) {
        Instant now = Instant.now();
        Map<String, CreditPrice> latestByDenomination = new LinkedHashMap<>();
        for (CreditPrice row : creditPriceRepository
                .findByAccountIdOrderByDenominationAscEffectiveFromDesc(UUID.fromString(accountId))) {
            if (row.getEffectiveFrom().isAfter(now)) continue;
            // Rows arrive newest-first per denomination, so the first effective row wins
            latestByDenomination.putIfAbsent(row.getDenomination(), row);
        }
        return latestByDenomination.values().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditPriceDto> getHistory(String accountId, String denomination) {
        return creditPriceRepository
                .findByAccountIdAndDenominationOrderByEffectiveFromDesc(UUID.fromString(accountId), denomination)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public List<CreditPriceDto> publishPrices(PublishCreditPricesRequest request, String accountId, UUID publishedBy) {
        UUID accountUuid = UUID.fromString(accountId);
        Account account = accountRepository.findById(accountUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (request.getEffectiveFrom().isBefore(Instant.now())) {
            throw new IllegalArgumentException(
                    "effectiveFrom must not be in the past — the settled price book is never rewritten");
        }

        // Serialize publishes per account so two concurrent price books can't interleave into a half-A-half-B state.
        // Namespaced apart from the weight tariff lock so the two dials don't contend.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:acct))")
                .setParameter("acct", accountId + ":credit-prices")
                .getSingleResult();

        List<CreditPrice> rows = new ArrayList<>();
        Set<String> seenDenominations = new HashSet<>();
        for (int i = 0; i < request.getEntries().size(); i++) {
            PublishCreditPricesRequest.Entry entry = request.getEntries().get(i);

            String denomination = entry.getDenomination() != null ? entry.getDenomination().trim() : "";
            if (denomination.isEmpty()) {
                throw new IllegalArgumentException("Entry " + i + ": denomination is required");
            }
            // Never trust a raw denomination from a request body — verify it belongs to this tenant's catalog
            if (creditModelRepository.findByAccountIdAndDenomination(accountUuid, denomination).isEmpty()) {
                throw new IllegalArgumentException(
                        "Entry " + i + ": no credit model with denomination '" + denomination + "' on this account");
            }

            BigDecimal price = entry.getPricePerCredit();
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Entry " + i + ": pricePerCredit must be positive");
            }
            if (price.compareTo(MAX_PRICE) > 0) {
                throw new IllegalArgumentException("Entry " + i + ": pricePerCredit exceeds maximum " + MAX_PRICE);
            }
            if (price.stripTrailingZeros().scale() > 6) {
                throw new IllegalArgumentException("Entry " + i + ": pricePerCredit allows at most 6 decimals");
            }

            String currency = normalizeCurrency(entry.getCurrency());
            if (!CURRENCY_PATTERN.matcher(currency).matches()) {
                throw new IllegalArgumentException("Entry " + i + ": currency must be a 3-letter ISO 4217 code");
            }

            if (!seenDenominations.add(denomination)) {
                throw new IllegalArgumentException("Entry " + i + ": duplicate denomination in batch");
            }

            CreditPrice row = new CreditPrice();
            row.setAccount(account);
            row.setDenomination(denomination);
            row.setCurrency(currency);
            row.setPricePerCredit(price);
            row.setEffectiveFrom(request.getEffectiveFrom());
            row.setCreatedBy(publishedBy);
            rows.add(row);
        }

        // Idempotent replay: identical batch at the same effectiveFrom returns the existing rows.
        // Anything else already published at that instant is a conflict, not a merge.
        if (creditPriceRepository.existsByAccountIdAndEffectiveFrom(accountUuid, request.getEffectiveFrom())) {
            List<CreditPrice> existing = creditPriceRepository
                    .findByAccountIdOrderByDenominationAscEffectiveFromDesc(accountUuid)
                    .stream()
                    .filter(p -> p.getEffectiveFrom().equals(request.getEffectiveFrom()))
                    .toList();
            if (sameBatch(existing, rows)) {
                log.info("Idempotent price-book replay for account {} at {}", accountId, request.getEffectiveFrom());
                return existing.stream().map(this::toDto).toList();
            }
            throw new TariffConflictException(
                    "A different price book is already published at " + request.getEffectiveFrom() + " — pick another effective time");
        }

        creditPriceRepository.saveAllAndFlush(rows);
        log.info("Published price book of {} prices for account {} effective {}", rows.size(), accountId, request.getEffectiveFrom());
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void deleteScheduledPrice(String priceId, String accountId) {
        CreditPrice row = creditPriceRepository
                .findByIdAndAccountId(UUID.fromString(priceId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Price row not found: " + priceId));

        if (!row.getEffectiveFrom().isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Only scheduled rows (effectiveFrom in the future) can be deleted — effective rows are the settled price book");
        }
        creditPriceRepository.delete(row);
        log.info("Deleted scheduled price row {} (was effective {})", priceId, row.getEffectiveFrom());
    }

    private boolean sameBatch(List<CreditPrice> existing, List<CreditPrice> proposed) {
        if (existing.size() != proposed.size()) return false;
        Set<String> existingKeys = new HashSet<>();
        for (CreditPrice p : existing) {
            existingKeys.add(p.getDenomination() + "|" + p.getCurrency() + "|" + p.getPricePerCredit().stripTrailingZeros());
        }
        for (CreditPrice p : proposed) {
            if (!existingKeys.contains(
                    p.getDenomination() + "|" + p.getCurrency() + "|" + p.getPricePerCredit().stripTrailingZeros())) {
                return false;
            }
        }
        return true;
    }

    static String normalizeCurrency(String currency) {
        if (currency == null) return DEFAULT_CURRENCY;
        String trimmed = currency.trim().toUpperCase();
        return trimmed.isEmpty() ? DEFAULT_CURRENCY : trimmed;
    }

    private CreditPriceDto toDto(CreditPrice row) {
        CreditPriceDto dto = new CreditPriceDto();
        dto.setId(row.getId().toString());
        dto.setDenomination(row.getDenomination());
        dto.setCurrency(row.getCurrency());
        dto.setPricePerCredit(row.getPricePerCredit());
        dto.setEffectiveFrom(row.getEffectiveFrom());
        dto.setCreatedBy(row.getCreatedBy() != null ? row.getCreatedBy().toString() : null);
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }
}
