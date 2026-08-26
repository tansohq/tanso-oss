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
package com.tansoflow.tansocore.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Where the server may be pointed by an operator-typed URL (a LiteLLM proxy, a
 * webhook). Loopback, link-local (cloud metadata lives at 169.254.169.254) and
 * unspecified addresses are refused; private ranges are allowed by default
 * because a self-hosted proxy is exactly what sits there — a hosted install
 * turns that off with app.spend.outbound.allow-private=false.
 */
@Component
public class OutboundUrlPolicy {
    private final boolean allowPrivate;
    private final boolean allowLoopback;

    public OutboundUrlPolicy(@Value("${app.spend.outbound.allow-private:true}") boolean allowPrivate,
                             @Value("${app.spend.outbound.allow-loopback:false}") boolean allowLoopback) {
        this.allowPrivate = allowPrivate;
        this.allowLoopback = allowLoopback;
    }

    /** @return the URL, trimmed, with a trailing slash removed */
    public String check(String url, String what) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(what + " is required");
        }
        String trimmed = url.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(what + " is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException(what + " must start with https:// (or http:// on a private network)");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(what + " must have a plain host, without credentials");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(what + ": " + uri.getHost() + " does not resolve");
        }
        for (InetAddress a : addresses) {
            if (a.isAnyLocalAddress() || a.isMulticastAddress() || a.isLinkLocalAddress()) {
                throw new IllegalArgumentException(what + ": " + uri.getHost() + " resolves to a reserved address (" + a.getHostAddress() + ")");
            }
            if (a.isLoopbackAddress() && !allowLoopback) {
                throw new IllegalArgumentException(what + ": " + uri.getHost() + " is this machine (set app.spend.outbound.allow-loopback=true to allow)");
            }
            if (a.isSiteLocalAddress() && !allowPrivate) {
                throw new IllegalArgumentException(what + ": " + uri.getHost() + " is a private address and this install does not allow those");
            }
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
