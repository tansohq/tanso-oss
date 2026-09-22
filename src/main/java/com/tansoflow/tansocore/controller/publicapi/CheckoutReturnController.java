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

import com.tansoflow.tansocore.integration.stripe.CheckoutReturnUrls;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The pages a human lands on after a Stripe Checkout page when the operator has not set their own
 * return URLs. Fixed text only: nothing from the query string is written into the page.
 */
@RestController
@Tag(name = "Checkout Return", description = "Pages shown after Stripe Checkout, no authentication")
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "true", matchIfMissing = true)
public class CheckoutReturnController {

    private static final String SAVED = "Your payment details were saved.";
    private static final String PAID = "Your payment went through.";
    private static final String CLOSE_TAB = " You can close this tab; your agent will see the change within a minute.";

    @GetMapping(CheckoutReturnUrls.COMPLETE_PATH)
    @Operation(summary = "Shown after a finished checkout",
            description = "kind=payment for a purchase, kind=setup (or anything else) for saved payment details.")
    public ResponseEntity<String> complete(@RequestParam(name = "kind", required = false) String kind) {
        String first = CheckoutReturnUrls.KIND_PAYMENT.equals(kind) ? PAID : SAVED;
        return html(page("You're all set", first + CLOSE_TAB));
    }

    @GetMapping(CheckoutReturnUrls.CANCELLED_PATH)
    @Operation(summary = "Shown when the human leaves checkout before finishing")
    public ResponseEntity<String> cancelled() {
        return html(page("Nothing was charged",
                "You left the payment page before finishing. You can close this tab, or go back to your agent "
                        + "for a new link."));
    }

    private static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(MediaType.valueOf("text/html; charset=utf-8")).body(body);
    }

    private static String page(String heading, String text) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="light dark">
                <meta name="robots" content="noindex">
                <title>%1$s</title>
                <style>
                :root { --bg: #ffffff; --fg: #1a1a1a; --muted: #4d4d4d; }
                @media (prefers-color-scheme: dark) { :root { --bg: #151515; --fg: #f2f2f2; --muted: #bdbdbd; } }
                body { margin: 0; background: var(--bg); color: var(--fg);
                  font: 18px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                main { max-width: 32rem; margin: 0 auto; padding: 18vh 16px 2rem; }
                h1 { font-size: 1.6rem; line-height: 1.25; margin: 0 0 0.75rem; }
                p { margin: 0; color: var(--muted); }
                </style>
                </head>
                <body>
                <main>
                <h1>%1$s</h1>
                <p>%2$s</p>
                </main>
                </body>
                </html>
                """.formatted(heading, text);
    }
}
