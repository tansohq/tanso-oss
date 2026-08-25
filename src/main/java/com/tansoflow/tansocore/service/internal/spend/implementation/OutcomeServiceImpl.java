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
package com.tansoflow.tansocore.service.internal.spend.implementation;

import com.tansoflow.tansocore.entity.Outcome;
import com.tansoflow.tansocore.entity.OutcomeSourceConnection;
import com.tansoflow.tansocore.entity.SpendUnit;
import com.tansoflow.tansocore.integration.spend.OutcomePuller;
import com.tansoflow.tansocore.integration.spend.OutcomeRecord;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.OutcomeDto;
import com.tansoflow.tansocore.model.spend.OutcomeSourceDto;
import com.tansoflow.tansocore.model.spend.SpendAllocationReportDto;
import com.tansoflow.tansocore.model.spend.SpendOutcomeReportDto;
import com.tansoflow.tansocore.model.spend.VendorProbeResultDto;
import com.tansoflow.tansocore.model.spend.VendorSyncResultDto;
import com.tansoflow.tansocore.model.spend.request.OutcomeRequest;
import com.tansoflow.tansocore.model.spend.request.OutcomeSourceRequest;
import com.tansoflow.tansocore.model.spend.type.OutcomeKind;
import com.tansoflow.tansocore.model.spend.type.OutcomeSource;
import com.tansoflow.tansocore.model.spend.type.SpendUnitType;
import com.tansoflow.tansocore.model.spend.type.VendorConnectionStatus;
import com.tansoflow.tansocore.repository.OutcomeRepository;
import com.tansoflow.tansocore.repository.OutcomeSourceConnectionRepository;
import com.tansoflow.tansocore.repository.SpendUnitRepository;
import com.tansoflow.tansocore.service.internal.spend.OutcomeService;
import com.tansoflow.tansocore.service.internal.spend.SpendAllocationService;
import com.tansoflow.tansocore.service.internal.spend.SpendSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class OutcomeServiceImpl implements OutcomeService {
    static final int DEFAULT_WINDOW_DAYS = 30;
    static final int JOB_WINDOW_DAYS = 3;

    private final OutcomeSourceConnectionRepository sourceRepository;
    private final OutcomeRepository outcomeRepository;
    private final SpendUnitRepository unitRepository;
    private final SpendAllocationService allocationService;
    private final SpendSettingsService settingsService;
    private final Map<OutcomeSource, OutcomePuller> pullers = new EnumMap<>(OutcomeSource.class);
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OutcomeServiceImpl(OutcomeSourceConnectionRepository sourceRepository, OutcomeRepository outcomeRepository,
                              SpendUnitRepository unitRepository, SpendAllocationService allocationService,
                              SpendSettingsService settingsService, List<OutcomePuller> pullers,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.sourceRepository = sourceRepository;
        this.outcomeRepository = outcomeRepository;
        this.unitRepository = unitRepository;
        this.allocationService = allocationService;
        this.settingsService = settingsService;
        for (OutcomePuller p : pullers) {
            this.pullers.put(p.source(), p);
        }
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ─── Sources ───

    @Override
    @Transactional(readOnly = true)
    public List<OutcomeSourceDto> listSources(String accountId) {
        return sourceRepository.findAllByAccountIdOrderByCreatedAtAsc(UUID.fromString(accountId)).stream().map(OutcomeServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public OutcomeSourceDto createSource(String accountId, OutcomeSourceRequest request) {
        UUID account = UUID.fromString(accountId);
        if (request.getSource() == OutcomeSource.MANUAL) {
            throw new IllegalArgumentException("MANUAL outcomes are posted to /outcomes; they do not need a source");
        }
        OutcomePuller puller = puller(request.getSource());
        String scope = request.getSource() == OutcomeSource.GITHUB
                ? String.join(", ", com.tansoflow.tansocore.integration.spend.GitHubOutcomePuller.repos(request.getScope()))
                : normaliseLinearScope(request.getScope());
        OutcomeSourceConnection source = new OutcomeSourceConnection();
        source.setAccountId(account);
        source.setSource(request.getSource());
        source.setLabel(request.getLabel().trim());
        source.setToken(request.getToken().trim());
        source.setScope(scope);
        if (request.getDefaultSpendUnitId() != null && !request.getDefaultSpendUnitId().isBlank()) {
            source.setDefaultSpendUnitId(requireUnit(account, request.getDefaultSpendUnitId()).getId());
        }
        source = sourceRepository.saveAndFlush(source);
        // Check the token now so the row never shows "OK" for a credential nobody has tried.
        try {
            puller.probe(source.getToken(), source.getScope());
            markHealthy(source);
        } catch (VendorApiException e) {
            markFailed(source, e);
        }
        return toDto(source);
    }

    @Override
    @Transactional
    public void deleteSource(String accountId, String sourceId) {
        OutcomeSourceConnection source = requireSource(UUID.fromString(accountId), sourceId);
        outcomeRepository.deleteBySourceConnectionId(source.getId());
        source.setToken("");
        source.setDeletedAt(clock.instant());
        sourceRepository.save(source);
    }

    @Override
    @Transactional
    public VendorProbeResultDto probe(String accountId, String sourceId) {
        OutcomeSourceConnection source = requireSource(UUID.fromString(accountId), sourceId);
        try {
            puller(source.getSource()).probe(source.getToken(), source.getScope());
            markHealthy(source);
            return VendorProbeResultDto.builder().ok(true).message("The token works for " + source.getScope() + ".").build();
        } catch (VendorApiException e) {
            markFailed(source, e);
            return VendorProbeResultDto.builder().ok(false).message(e.getMessage()).build();
        }
    }

    @Override
    @Transactional(noRollbackFor = VendorApiException.class)
    public VendorSyncResultDto sync(String accountId, String sourceId, LocalDate from, LocalDate to) {
        OutcomeSourceConnection source = requireSource(UUID.fromString(accountId), sourceId);
        LocalDate end = to != null ? to : LocalDate.now(clock).plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be before to");
        }
        int rows = pullWindow(source, start, end);
        return VendorSyncResultDto.builder().connectionId(source.getId().toString()).from(start).to(end).rowsWritten(rows).build();
    }

    @Override
    public void syncAll() {
        LocalDate end = LocalDate.now(clock).plusDays(1);
        LocalDate from = end.minusDays(JOB_WINDOW_DAYS);
        for (OutcomeSourceConnection listed : sourceRepository.findAll()) {
            UUID id = listed.getId();
            try {
                transactionTemplate.executeWithoutResult(status -> sourceRepository.findById(id).ifPresent(s -> pullWindow(s, from, end)));
            } catch (VendorApiException e) {
                transactionTemplate.executeWithoutResult(status -> sourceRepository.findById(id).ifPresent(s -> markFailed(s, e)));
                log.warn("Outcome sync failed for source {}: {}", id, e.getMessage());
            }
        }
    }

    private int pullWindow(OutcomeSourceConnection source, LocalDate from, LocalDate to) {
        List<OutcomeRecord> records;
        try {
            records = puller(source.getSource()).pull(source.getToken(), source.getScope(),
                    from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (VendorApiException e) {
            markFailed(source, e);
            throw e;
        }
        Attribution attribution = attribution(source.getAccountId());
        int written = 0;
        for (OutcomeRecord r : records) {
            Outcome o = outcomeRepository.findByAccountIdAndSourceAndExternalId(source.getAccountId(), source.getSource(), r.externalId())
                    .orElseGet(Outcome::new);
            o.setAccountId(source.getAccountId());
            o.setSourceConnectionId(source.getId());
            o.setSource(source.getSource());
            o.setKind(r.kind());
            o.setExternalId(r.externalId());
            o.setTitle(r.title());
            o.setUrl(r.url());
            o.setActorEmail(r.actorEmail());
            o.setActorLogin(r.actorLogin());
            o.setOccurredAt(r.occurredAt());
            o.setSpendUnitId(attribution.resolve(r.actorEmail(), r.actorLogin(), source.getDefaultSpendUnitId()));
            outcomeRepository.save(o);
            written++;
        }
        markHealthy(source);
        source.setLastSyncedAt(clock.instant());
        sourceRepository.save(source);
        return written;
    }

    // ─── Manual ───

    @Override
    @Transactional
    public OutcomeDto record(String accountId, OutcomeRequest request) {
        UUID account = UUID.fromString(accountId);
        UUID explicit = request.getSpendUnitId() == null || request.getSpendUnitId().isBlank()
                ? null : requireUnit(account, request.getSpendUnitId()).getId();
        Outcome o = outcomeRepository.findByAccountIdAndSourceAndExternalId(account, OutcomeSource.MANUAL, request.getExternalId().trim())
                .orElseGet(Outcome::new);
        boolean fresh = o.getId() == null;
        o.setAccountId(account);
        o.setSource(OutcomeSource.MANUAL);
        o.setKind(request.getKind());
        o.setExternalId(request.getExternalId().trim());
        // A re-post updates what it sends and leaves the rest alone.
        if (fresh || request.getTitle() != null) {
            o.setTitle(request.getTitle());
        }
        if (fresh || request.getUrl() != null) {
            o.setUrl(request.getUrl());
        }
        if (fresh || request.getActorEmail() != null) {
            o.setActorEmail(request.getActorEmail() == null ? null : request.getActorEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (fresh || request.getActorLogin() != null) {
            o.setActorLogin(request.getActorLogin() == null ? null : request.getActorLogin().trim());
        }
        if (fresh || request.getOccurredAt() != null) {
            o.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : clock.instant());
        }
        UUID fallback = explicit != null ? explicit : o.getSpendUnitId();
        o.setSpendUnitId(attribution(account).resolve(o.getActorEmail(), o.getActorLogin(), fallback));
        return toDto(outcomeRepository.save(o), unitName(account, o.getSpendUnitId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutcomeDto> recent(String accountId) {
        UUID account = UUID.fromString(accountId);
        Map<UUID, String> names = names(account);
        return outcomeRepository.findTop200ByAccountIdOrderByOccurredAtDesc(account).stream()
                .map(o -> toDto(o, o.getSpendUnitId() == null ? null : names.get(o.getSpendUnitId()))).toList();
    }

    // ─── Report ───

    @Override
    @Transactional(readOnly = true)
    public SpendOutcomeReportDto report(String accountId, LocalDate from, LocalDate to) {
        UUID account = UUID.fromString(accountId);
        LocalDate end = to != null ? to : LocalDate.now(clock).plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be before to");
        }
        SpendAllocationReportDto allocation = allocationService.allocate(accountId, start, end);
        List<SpendUnit> units = unitRepository.findAllByAccountIdOrderByNameAsc(account);
        Map<UUID, SpendUnit> unitById = new HashMap<>();
        for (SpendUnit u : units) {
            unitById.put(u.getId(), u);
        }
        Map<UUID, long[]> counts = new HashMap<>(); // [prs, issues, custom]
        long total = 0;
        long unattributed = 0;
        for (Outcome o : outcomeRepository.findAllByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                account, start.atStartOfDay(ZoneOffset.UTC).toInstant(), end.atStartOfDay(ZoneOffset.UTC).toInstant())) {
            total++;
            if (o.getSpendUnitId() == null || !unitById.containsKey(o.getSpendUnitId())) {
                unattributed++;
                continue;
            }
            int slot = switch (o.getKind()) { case PR_MERGED -> 0; case ISSUE_DONE -> 1; case CUSTOM -> 2; };
            // Count on the unit and every ancestor, each once.
            UUID cursor = o.getSpendUnitId();
            Set<UUID> seen = new HashSet<>();
            while (cursor != null && seen.add(cursor)) {
                counts.computeIfAbsent(cursor, k -> new long[3])[slot]++;
                SpendUnit parent = unitById.get(cursor);
                cursor = parent == null ? null : parent.getParentId();
            }
        }
        Map<String, BigDecimal> spendByUnit = new HashMap<>();
        Map<String, BigDecimal> estimateByUnit = new HashMap<>();
        for (SpendAllocationReportDto.AllocationRow r : allocation.getRows()) {
            spendByUnit.put(r.getUnitId(), r.getTotalCents());   // metered only: one basis for every row
            if (r.getPersonEstimateCents() != null) {
                estimateByUnit.put(r.getUnitId(), r.getPersonEstimateCents());
            }
        }
        List<SpendOutcomeReportDto.OutcomeRow> rows = new ArrayList<>();
        for (SpendUnit u : units) {
            long[] c = counts.getOrDefault(u.getId(), new long[3]);
            long n = c[0] + c[1] + c[2];
            BigDecimal spend = spendByUnit.getOrDefault(u.getId().toString(), BigDecimal.ZERO);
            rows.add(SpendOutcomeReportDto.OutcomeRow.builder()
                    .unitId(u.getId().toString()).name(u.getName()).type(u.getType())
                    .parentId(u.getParentId() == null ? null : u.getParentId().toString())
                    .prsMerged(c[0]).issuesDone(c[1]).custom(c[2]).outcomes(n)
                    .spendCents(spend)
                    .personEstimateCents(estimateByUnit.get(u.getId().toString()))
                    .costPerOutcomeCents(n == 0 || spend.signum() == 0 ? null : spend.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP))
                    .build());
        }
        BigDecimal totalSpend = allocation.getTotalMeteredCents();
        return SpendOutcomeReportDto.builder()
                .from(start).to(end).rows(rows)
                .totalOutcomes(total).unattributedOutcomes(unattributed)
                .totalSpendCents(totalSpend)
                .costPerOutcomeCents(total == 0 || totalSpend.signum() == 0 ? null : totalSpend.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP))
                .build();
    }

    // ─── Attribution ───

    /** Person by email, then by GitHub login (only while person level is on); else the caller's fallback. */
    private Attribution attribution(UUID account) {
        boolean personLevel = settingsService.personLevelEnabled(account.toString());
        Map<String, UUID> byEmail = new HashMap<>();
        Map<String, UUID> byLogin = new HashMap<>();
        if (personLevel) {
            for (SpendUnit u : unitRepository.findAllByAccountIdOrderByNameAsc(account)) {
                if (u.getType() != SpendUnitType.PERSON) {
                    continue;
                }
                if (u.getEmail() != null) {
                    byEmail.put(u.getEmail().toLowerCase(Locale.ROOT), u.getId());
                }
                if (u.getGithubLogin() != null) {
                    byLogin.put(u.getGithubLogin().toLowerCase(Locale.ROOT), u.getId());
                }
            }
        }
        return new Attribution(byEmail, byLogin);
    }

    record Attribution(Map<String, UUID> byEmail, Map<String, UUID> byLogin) {
        UUID resolve(String email, String login, UUID fallback) {
            if (email != null && byEmail.containsKey(email.toLowerCase(Locale.ROOT))) {
                return byEmail.get(email.toLowerCase(Locale.ROOT));
            }
            if (login != null && byLogin.containsKey(login.toLowerCase(Locale.ROOT))) {
                return byLogin.get(login.toLowerCase(Locale.ROOT));
            }
            return fallback;
        }
    }

    static String normaliseLinearScope(String raw) {
        List<String> teams = com.tansoflow.tansocore.integration.spend.LinearOutcomePuller.teams(raw);
        return teams.isEmpty() ? "*" : String.join(", ", teams);
    }

    // ─── plumbing ───

    private OutcomePuller puller(OutcomeSource source) {
        OutcomePuller p = pullers.get(source);
        if (p == null) {
            throw new IllegalArgumentException("No puller for " + source);
        }
        return p;
    }

    private void markHealthy(OutcomeSourceConnection s) {
        s.setStatus(VendorConnectionStatus.ACTIVE);
        s.setLastError(null);
        sourceRepository.save(s);
    }

    private void markFailed(OutcomeSourceConnection s, VendorApiException e) {
        s.setStatus(VendorConnectionStatus.ERROR);
        s.setLastError(e.getMessage());
        sourceRepository.save(s);
    }

    private OutcomeSourceConnection requireSource(UUID account, String id) {
        return sourceRepository.findByIdAndAccountId(UUID.fromString(id), account)
                .orElseThrow(() -> new ResourceNotFoundException("Outcome source not found: " + id));
    }

    private SpendUnit requireUnit(UUID account, String id) {
        return unitRepository.findByIdAndAccountId(UUID.fromString(id), account)
                .orElseThrow(() -> new ResourceNotFoundException("Spend unit not found: " + id));
    }

    private Map<UUID, String> names(UUID account) {
        Map<UUID, String> names = new HashMap<>();
        for (SpendUnit u : unitRepository.findAllByAccountIdOrderByNameAsc(account)) {
            names.put(u.getId(), u.getName());
        }
        return names;
    }

    private String unitName(UUID account, UUID unitId) {
        return unitId == null ? null : unitRepository.findByIdAndAccountId(unitId, account).map(SpendUnit::getName).orElse(null);
    }

    static OutcomeSourceDto toDto(OutcomeSourceConnection s) {
        return OutcomeSourceDto.builder()
                .id(s.getId().toString()).source(s.getSource()).label(s.getLabel()).scope(s.getScope())
                .defaultSpendUnitId(s.getDefaultSpendUnitId() == null ? null : s.getDefaultSpendUnitId().toString())
                .status(s.getStatus()).lastError(s.getLastError()).lastSyncedAt(s.getLastSyncedAt()).createdAt(s.getCreatedAt())
                .build();
    }

    static OutcomeDto toDto(Outcome o, String unitName) {
        return OutcomeDto.builder()
                .id(o.getId() == null ? null : o.getId().toString()).source(o.getSource()).kind(o.getKind())
                .externalId(o.getExternalId()).title(o.getTitle()).url(o.getUrl())
                .actorEmail(o.getActorEmail()).actorLogin(o.getActorLogin())
                .spendUnitId(o.getSpendUnitId() == null ? null : o.getSpendUnitId().toString()).unitName(unitName)
                .occurredAt(o.getOccurredAt())
                .build();
    }
}
