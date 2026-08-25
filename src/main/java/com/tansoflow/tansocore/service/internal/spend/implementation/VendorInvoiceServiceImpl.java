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

import com.tansoflow.tansocore.entity.VendorInvoice;
import com.tansoflow.tansocore.entity.VendorInvoiceLine;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.spend.VendorInvoiceDto;
import com.tansoflow.tansocore.model.spend.type.InvoiceLineKind;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.repository.VendorInvoiceRepository;
import com.tansoflow.tansocore.service.internal.spend.VendorInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class VendorInvoiceServiceImpl implements VendorInvoiceService {
    private final VendorInvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public VendorInvoiceDto importCsv(String accountId, VendorProvider provider, LocalDate periodStart, LocalDate periodEnd,
                                      String currency, String fileName, InputStream csv) {
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd is before periodStart");
        }
        VendorInvoice invoice = new VendorInvoice();
        invoice.setAccountId(UUID.fromString(accountId));
        invoice.setProvider(provider);
        invoice.setPeriodStart(periodStart);
        invoice.setPeriodEnd(periodEnd);
        invoice.setCurrency(currency.toUpperCase(Locale.ROOT));
        invoice.setImportedFrom(fileName);
        BigDecimal total = BigDecimal.ZERO;
        for (VendorInvoiceLine line : parseLines(csv)) {
            line.setInvoice(invoice);
            invoice.getLines().add(line);
            total = total.add(line.getAmountCents());
        }
        if (invoice.getLines().isEmpty()) {
            throw new IllegalArgumentException("The CSV has no line rows");
        }
        invoice.setTotalCents(total);
        return toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorInvoiceDto> list(String accountId) {
        return invoiceRepository.findAllByAccountIdOrderByPeriodStartDesc(UUID.fromString(accountId))
                .stream().map(VendorInvoiceServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public void delete(String accountId, String invoiceId) {
        VendorInvoice invoice = invoiceRepository.findByIdAndAccountId(UUID.fromString(invoiceId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        invoiceRepository.delete(invoice);
    }

    static List<VendorInvoiceLine> parseLines(InputStream csv) {
        List<VendorInvoiceLine> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("The CSV is empty");
            }
            Map<String, Integer> columns = new HashMap<>();
            List<String> header = splitCsv(headerLine.replace("\uFEFF", ""));
            for (int i = 0; i < header.size(); i++) {
                columns.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
            }
            if (!columns.containsKey("description") || !columns.containsKey("amount")) {
                throw new IllegalArgumentException("The CSV needs 'description' and 'amount' columns; found: " + header);
            }
            String row;
            int number = 1;
            while ((row = reader.readLine()) != null) {
                number++;
                if (row.isBlank()) {
                    continue;
                }
                List<String> cells = splitCsv(row);
                VendorInvoiceLine line = new VendorInvoiceLine();
                line.setDescription(cell(cells, columns, "description"));
                line.setAmountCents(parseAmount(cell(cells, columns, "amount"), number));
                line.setKind(parseKind(cell(cells, columns, "kind")));
                line.setModel(blankToNull(cell(cells, columns, "model")));
                String quantity = blankToNull(cell(cells, columns, "quantity"));
                line.setQuantity(quantity == null ? null : new BigDecimal(quantity.replace(",", "")));
                lines.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lines;
    }

    /** "1,234.50", "$12.30", "(5.00)" → cents. Major units in, cents out. */
    static BigDecimal parseAmount(String raw, int rowNumber) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Row " + rowNumber + ": amount is empty");
        }
        String cleaned = raw.trim().replace(",", "").replaceAll("[^0-9.()\\-]", "");
        boolean negative = cleaned.startsWith("(") && cleaned.endsWith(")");
        cleaned = cleaned.replace("(", "").replace(")", "");
        try {
            BigDecimal major = new BigDecimal(cleaned);
            if (negative) {
                major = major.negate();
            }
            return major.movePointRight(2).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Row " + rowNumber + ": amount '" + raw + "' is not a number");
        }
    }

    private static InvoiceLineKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return InvoiceLineKind.OTHER;
        }
        try {
            return InvoiceLineKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("kind '" + raw + "' must be one of TOKEN, SEAT, TOOL, OTHER");
        }
    }

    private static String cell(List<String> cells, Map<String, Integer> columns, String name) {
        Integer i = columns.get(name);
        return i == null || i >= cells.size() ? null : cells.get(i).trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Minimal RFC 4180: commas, double quotes, doubled quotes inside quotes. No embedded newlines. */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    static VendorInvoiceDto toDto(VendorInvoice invoice) {
        return VendorInvoiceDto.builder()
                .id(invoice.getId() == null ? null : invoice.getId().toString())
                .provider(invoice.getProvider())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .currency(invoice.getCurrency())
                .totalCents(invoice.getTotalCents())
                .importedFrom(invoice.getImportedFrom())
                .createdAt(invoice.getCreatedAt())
                .lines(invoice.getLines().stream().map(l -> VendorInvoiceDto.Line.builder()
                        .description(l.getDescription()).kind(l.getKind()).model(l.getModel())
                        .quantity(l.getQuantity()).amountCents(l.getAmountCents()).build()).toList())
                .build();
    }
}
