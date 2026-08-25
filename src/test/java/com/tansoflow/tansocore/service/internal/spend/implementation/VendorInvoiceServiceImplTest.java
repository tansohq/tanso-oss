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
import com.tansoflow.tansocore.model.spend.VendorInvoiceDto;
import com.tansoflow.tansocore.model.spend.type.InvoiceLineKind;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.repository.VendorInvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorInvoiceServiceImplTest {

    @Mock
    private VendorInvoiceRepository invoiceRepository;

    private static ByteArrayInputStream csv(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesQuotedDescriptionsDollarSignsAndKinds() {
        List<VendorInvoiceLine> lines = VendorInvoiceServiceImpl.parseLines(csv(
                "\uFEFFDescription,Amount,Kind,Model,Quantity\n"
                        + "\"Claude Sonnet 4.5, input\",\"$1,234.50\",token,claude-sonnet-4-5,\"411,500,000\"\n"
                        + "Claude Team seats,90.00,SEAT,,3\n"
                        + "\n"
                        + "Credit,(5.00),,,\n"));
        assertEquals(3, lines.size());
        assertEquals("Claude Sonnet 4.5, input", lines.get(0).getDescription());
        assertEquals(0, new BigDecimal("123450.00").compareTo(lines.get(0).getAmountCents()));
        assertEquals(InvoiceLineKind.TOKEN, lines.get(0).getKind());
        assertEquals("claude-sonnet-4-5", lines.get(0).getModel());
        assertEquals(0, new BigDecimal("411500000").compareTo(lines.get(0).getQuantity()));
        assertEquals(InvoiceLineKind.SEAT, lines.get(1).getKind());
        assertNull(lines.get(1).getModel());
        assertEquals(InvoiceLineKind.OTHER, lines.get(2).getKind());
        assertEquals(0, new BigDecimal("-500.00").compareTo(lines.get(2).getAmountCents()));
    }

    @Test
    void missingColumnsAndBadAmountsAreNamed() {
        IllegalArgumentException noCols = assertThrows(IllegalArgumentException.class,
                () -> VendorInvoiceServiceImpl.parseLines(csv("name,price\nx,1\n")));
        assertTrue(noCols.getMessage().contains("description"));
        IllegalArgumentException badAmount = assertThrows(IllegalArgumentException.class,
                () -> VendorInvoiceServiceImpl.parseLines(csv("description,amount\nx,lots\n")));
        assertTrue(badAmount.getMessage().contains("Row 2"));
    }

    @Test
    void importSumsLinesAndStampsThePeriod() {
        when(invoiceRepository.save(any())).thenAnswer(inv -> {
            VendorInvoice i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });
        VendorInvoiceServiceImpl service = new VendorInvoiceServiceImpl(invoiceRepository);
        VendorInvoiceDto dto = service.importCsv(UUID.randomUUID().toString(), VendorProvider.ANTHROPIC,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "usd", "july.csv",
                csv("description,amount\nInput,10.00\nOutput,2.50\n"));
        assertEquals(0, new BigDecimal("1250.00").compareTo(dto.getTotalCents()));
        assertEquals("USD", dto.getCurrency());
        assertEquals(2, dto.getLines().size());
        assertEquals("july.csv", dto.getImportedFrom());
        assertEquals(LocalDate.of(2026, 7, 31), dto.getPeriodEnd());
    }

    @Test
    void emptyCsvAndInvertedPeriodAreRejected() {
        VendorInvoiceServiceImpl service = new VendorInvoiceServiceImpl(invoiceRepository);
        assertThrows(IllegalArgumentException.class, () -> service.importCsv("a", VendorProvider.OPENAI,
                LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1), "USD", "x.csv", csv("description,amount\n")));
        assertThrows(IllegalArgumentException.class, () -> service.importCsv(UUID.randomUUID().toString(), VendorProvider.OPENAI,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "USD", "x.csv", csv("description,amount\n")));
    }
}
