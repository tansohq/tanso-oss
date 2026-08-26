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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundUrlPolicyTest {
    private final OutboundUrlPolicy selfHosted = new OutboundUrlPolicy(true, false);
    private final OutboundUrlPolicy hosted = new OutboundUrlPolicy(false, false);

    @Test
    void cloudMetadataAndLoopbackAreAlwaysRefused() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> selfHosted.check("http://169.254.169.254/latest/meta-data/", "URL")).getMessage().contains("reserved"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> selfHosted.check("http://localhost:8080/actuator", "URL")).getMessage().contains("this machine"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> selfHosted.check("http://127.0.0.1/", "URL")).getMessage().contains("this machine"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> selfHosted.check("http://0.0.0.0/", "URL")).getMessage().contains("reserved"));
    }

    @Test
    void privateRangesDependOnTheInstall() {
        assertEquals("http://10.0.0.5:4000", selfHosted.check("http://10.0.0.5:4000/", "The LiteLLM proxy URL"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> hosted.check("http://10.0.0.5:4000", "The LiteLLM proxy URL")).getMessage().contains("private"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> hosted.check("http://192.168.1.9/", "URL")).getMessage().contains("private"));
    }

    @Test
    void onlyPlainHttpUrls() {
        assertThrows(IllegalArgumentException.class, () -> selfHosted.check("javascript:alert(1)", "URL"));
        assertThrows(IllegalArgumentException.class, () -> selfHosted.check("ftp://10.0.0.5/", "URL"));
        assertThrows(IllegalArgumentException.class, () -> selfHosted.check("http://user:pw@10.0.0.5/", "URL"));
        assertThrows(IllegalArgumentException.class, () -> selfHosted.check("", "URL"));
        assertThrows(IllegalArgumentException.class, () -> selfHosted.check("http://this-host-does-not-exist.invalid/", "URL"));
        assertEquals("http://localhost:8080", new OutboundUrlPolicy(true, true).check("http://localhost:8080/", "URL"));
    }
}
