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

/**
 * Auto-detects AI model provider from the model name.
 */
public final class ModelProviderResolver {

    private ModelProviderResolver() {}

    public static String resolveProvider(String modelName) {
        if (modelName == null || modelName.isBlank()) return null;

        String lower = modelName.toLowerCase();

        if (lower.startsWith("gpt-") || lower.startsWith("o1-") || lower.startsWith("o3-")
                || lower.startsWith("o4-") || lower.startsWith("dall-e")
                || lower.startsWith("text-embedding") || lower.startsWith("whisper")) {
            return "openai";
        }
        if (lower.startsWith("claude-")) {
            return "anthropic";
        }
        if (lower.startsWith("gemini-") || lower.startsWith("gemma-")) {
            return "google";
        }
        if (lower.startsWith("llama-") || lower.startsWith("llama3")) {
            return "meta";
        }
        if (lower.startsWith("mistral-") || lower.startsWith("mixtral-")
                || lower.startsWith("codestral") || lower.startsWith("pixtral")) {
            return "mistral";
        }
        if (lower.startsWith("command-") || lower.startsWith("embed-")) {
            return "cohere";
        }
        if (lower.startsWith("deepseek-")) {
            return "deepseek";
        }

        return null;
    }
}
