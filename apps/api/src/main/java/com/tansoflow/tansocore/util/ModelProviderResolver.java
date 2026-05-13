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
