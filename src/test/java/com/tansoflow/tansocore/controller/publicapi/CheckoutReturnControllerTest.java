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
package com.tansoflow.tansocore.controller.publicapi;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutReturnControllerTest {

    private final CheckoutReturnController controller = new CheckoutReturnController();

    private static void assertHtmlPage(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody()).startsWith("<!doctype html>");
        assertThat(response.getBody()).contains("prefers-color-scheme: dark");
        assertThat(response.getBody()).doesNotContain("<script", "http://", "https://");
    }

    @Test
    void aFinishedSetupSaysTheDetailsWereSaved() {
        ResponseEntity<String> response = controller.complete("setup");

        assertHtmlPage(response);
        assertThat(response.getBody()).contains("<h1>You're all set</h1>");
        assertThat(response.getBody()).contains("Your payment details were saved. You can close this tab; "
                + "your agent will see the change within a minute.");
    }

    @Test
    void aFinishedPaymentSaysThePaymentWentThrough() {
        ResponseEntity<String> response = controller.complete("payment");

        assertHtmlPage(response);
        assertThat(response.getBody()).contains("Your payment went through. You can close this tab; "
                + "your agent will see the change within a minute.");
        assertThat(response.getBody()).doesNotContain("payment details were saved");
    }

    @Test
    void noKindReadsAsASetup() {
        ResponseEntity<String> response = controller.complete(null);

        assertHtmlPage(response);
        assertThat(response.getBody()).contains("Your payment details were saved.");
    }

    @Test
    void theQueryStringIsNeverWrittenIntoThePage() {
        String injected = "<script>alert(1)</script><b>$500 charged to 4242</b>";

        ResponseEntity<String> response = controller.complete(injected);

        assertHtmlPage(response);
        assertThat(response.getBody()).doesNotContain(injected, "alert(1)", "<b>", "$500", "4242");
        assertThat(response.getBody()).contains("Your payment details were saved.");
    }

    @Test
    void theCancelPageSaysNothingWasCharged() {
        ResponseEntity<String> response = controller.cancelled();

        assertHtmlPage(response);
        assertThat(response.getBody()).contains("<h1>Nothing was charged</h1>");
        assertThat(response.getBody()).contains("You left the payment page before finishing. You can close this "
                + "tab, or go back to your agent for a new link.");
    }
}
