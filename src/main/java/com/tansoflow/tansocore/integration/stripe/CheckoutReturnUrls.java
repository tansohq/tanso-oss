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
package com.tansoflow.tansocore.integration.stripe;

import com.tansoflow.tansocore.entity.AccountSetting;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Where Stripe Checkout sends the human afterwards. The operator's URLs win; without them the human
 * lands on this instance's own return pages ({@code CheckoutReturnController}).
 *
 * <p>The base URL comes from the request that asked for the checkout, the same way the poll URL in the
 * 402 envelope and the signup links are built. Behind a proxy that needs
 * {@code server.forward-headers-strategy}, which the prod, staging and sandbox profiles set.
 */
public final class CheckoutReturnUrls {

    public static final String COMPLETE_PATH = "/public/checkout/complete";
    public static final String CANCELLED_PATH = "/public/checkout/cancelled";
    public static final String KIND_SETUP = "setup";
    public static final String KIND_PAYMENT = "payment";

    private CheckoutReturnUrls() {
    }

    /** {@code kind} is {@link #KIND_SETUP} or {@link #KIND_PAYMENT}; it picks the wording on the page. */
    public static String successUrl(AccountSetting settings, String kind) {
        if (settings != null && settings.getStripeCheckoutSuccessUrl() != null
                && !settings.getStripeCheckoutSuccessUrl().isBlank()) {
            return settings.getStripeCheckoutSuccessUrl();
        }
        return currentBaseUrl() + COMPLETE_PATH + "?kind=" + kind;
    }

    public static String cancelUrl(AccountSetting settings) {
        if (settings != null && settings.getStripeCheckoutCancelUrl() != null
                && !settings.getStripeCheckoutCancelUrl().isBlank()) {
            return settings.getStripeCheckoutCancelUrl();
        }
        return currentBaseUrl() + CANCELLED_PATH;
    }

    private static String currentBaseUrl() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new IllegalStateException("No HTTP request to take this instance's address from. Set "
                    + "stripeCheckoutSuccessUrl and stripeCheckoutCancelUrl in the account settings.");
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getRequestURL().toString().replace(request.getRequestURI(), "");
    }
}
