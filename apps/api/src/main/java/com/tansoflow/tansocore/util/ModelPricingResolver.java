package com.tansoflow.tansocore.util;

import com.tansoflow.tansocore.entity.ModelPricing;
import com.tansoflow.tansocore.repository.ModelPricingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModelPricingResolver {

    private final ModelPricingRepository modelPricingRepository;

    private final ConcurrentMap<String, Optional<ResolvedPricing>> resolutionCache = new ConcurrentHashMap<>();
    private volatile Map<String, List<String>> knownModelsByProvider = null;

    public record ResolvedPricing(ModelPricing pricing, boolean fuzzyMatched) {}

    public ResolvedPricing resolve(String modelName) {
        if (modelName == null || modelName.isBlank()) return null;

        Optional<ResolvedPricing> cached = resolutionCache.get(modelName);
        if (cached != null) {
            return cached.orElse(null);
        }

        // Try exact match first
        Optional<ModelPricing> exact = modelPricingRepository.findById(modelName);
        if (exact.isPresent()) {
            ResolvedPricing result = new ResolvedPricing(exact.get(), false);
            resolutionCache.put(modelName, Optional.of(result));
            return result;
        }

        // Fuzzy fallback: scope by provider to prevent cross-provider matches
        String provider = ModelProviderResolver.resolveProvider(modelName);
        if (provider == null) {
            resolutionCache.put(modelName, Optional.empty());
            return null;
        }

        Map<String, List<String>> modelsByProvider = loadKnownModels();
        List<String> candidates = modelsByProvider.get(provider);
        if (candidates == null || candidates.isEmpty()) {
            resolutionCache.put(modelName, Optional.empty());
            return null;
        }

        String match = findLongestPrefixMatch(modelName, candidates);
        if (match == null) {
            resolutionCache.put(modelName, Optional.empty());
            return null;
        }

        Optional<ModelPricing> matched = modelPricingRepository.findById(match);
        if (matched.isEmpty()) {
            resolutionCache.put(modelName, Optional.empty());
            return null;
        }

        ResolvedPricing result = new ResolvedPricing(matched.get(), true);
        resolutionCache.put(modelName, Optional.of(result));
        log.info("Fuzzy model pricing match: '{}' resolved to '{}'", modelName, match);
        return result;
    }

    public void invalidateCache() {
        knownModelsByProvider = null;
        resolutionCache.clear();
    }

    private Map<String, List<String>> loadKnownModels() {
        Map<String, List<String>> local = knownModelsByProvider;
        if (local != null) return local;

        List<ModelPricing> all = modelPricingRepository.findAll();
        local = all.stream().collect(Collectors.groupingBy(
                ModelPricing::getProvider,
                Collectors.mapping(ModelPricing::getModel, Collectors.toList())
        ));
        knownModelsByProvider = local;
        return local;
    }

    static String findLongestPrefixMatch(String input, List<String> candidates) {
        String bestMatch = null;
        int bestLength = 0;
        for (String candidate : candidates) {
            if (candidate.length() > bestLength
                    && input.startsWith(candidate)
                    && isDelimiterBoundary(input, candidate.length())) {
                bestMatch = candidate;
                bestLength = candidate.length();
            }
        }
        return bestMatch;
    }

    private static boolean isDelimiterBoundary(String input, int position) {
        if (position >= input.length()) return true;
        char c = input.charAt(position);
        return c == '-' || c == '.';
    }
}
