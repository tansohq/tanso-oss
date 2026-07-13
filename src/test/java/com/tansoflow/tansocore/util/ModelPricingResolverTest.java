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

import com.tansoflow.tansocore.entity.ModelPricing;
import com.tansoflow.tansocore.repository.ModelPricingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelPricingResolverTest {

    @Mock
    private ModelPricingRepository modelPricingRepository;

    private ModelPricingResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ModelPricingResolver(modelPricingRepository);
    }

    // --- Exact match tests ---

    @Test
    void resolve_exactMatch_returnsDirectly() {
        ModelPricing pricing = makeModelPricing("gpt-4o", "openai");
        when(modelPricingRepository.findById("gpt-4o")).thenReturn(Optional.of(pricing));

        ModelPricingResolver.ResolvedPricing result = resolver.resolve("gpt-4o");

        assertNotNull(result);
        assertEquals("gpt-4o", result.pricing().getModel());
        assertFalse(result.fuzzyMatched());
    }

    @Test
    void resolve_nullInput_returnsNull() {
        assertNull(resolver.resolve(null));
    }

    @Test
    void resolve_blankInput_returnsNull() {
        assertNull(resolver.resolve("  "));
    }

    // --- Fuzzy match tests ---

    @Test
    void resolve_versionedOpenAI_matchesBaseModel() {
        when(modelPricingRepository.findById("gpt-4o-2024-08-06")).thenReturn(Optional.empty());
        stubFindAllWithSeedModels();
        ModelPricing pricing = makeModelPricing("gpt-4o", "openai");
        when(modelPricingRepository.findById("gpt-4o")).thenReturn(Optional.of(pricing));

        ModelPricingResolver.ResolvedPricing result = resolver.resolve("gpt-4o-2024-08-06");

        assertNotNull(result);
        assertEquals("gpt-4o", result.pricing().getModel());
        assertTrue(result.fuzzyMatched());
    }

    @Test
    void resolve_longestPrefixWins_miniBeatsBase() {
        when(modelPricingRepository.findById("gpt-4o-mini-2024-07-18")).thenReturn(Optional.empty());
        stubFindAllWithSeedModels();
        ModelPricing pricing = makeModelPricing("gpt-4o-mini", "openai");
        when(modelPricingRepository.findById("gpt-4o-mini")).thenReturn(Optional.of(pricing));

        ModelPricingResolver.ResolvedPricing result = resolver.resolve("gpt-4o-mini-2024-07-18");

        assertNotNull(result);
        assertEquals("gpt-4o-mini", result.pricing().getModel());
        assertTrue(result.fuzzyMatched());
    }

    @Test
    void resolve_versionedClaude_matchesBaseModel() {
        when(modelPricingRepository.findById("claude-sonnet-4-5-20250514")).thenReturn(Optional.empty());
        stubFindAllWithSeedModels();
        ModelPricing pricing = makeModelPricing("claude-sonnet-4-5", "anthropic");
        when(modelPricingRepository.findById("claude-sonnet-4-5")).thenReturn(Optional.of(pricing));

        ModelPricingResolver.ResolvedPricing result = resolver.resolve("claude-sonnet-4-5-20250514");

        assertNotNull(result);
        assertEquals("claude-sonnet-4-5", result.pricing().getModel());
        assertTrue(result.fuzzyMatched());
    }

    @Test
    void resolve_claudeWithSuffix_matchesLongestPrefix() {
        when(modelPricingRepository.findById("claude-3-5-sonnet-20241022-v2")).thenReturn(Optional.empty());
        stubFindAllWithSeedModels();
        ModelPricing pricing = makeModelPricing("claude-3-5-sonnet-20241022", "anthropic");
        when(modelPricingRepository.findById("claude-3-5-sonnet-20241022")).thenReturn(Optional.of(pricing));

        ModelPricingResolver.ResolvedPricing result = resolver.resolve("claude-3-5-sonnet-20241022-v2");

        assertNotNull(result);
        assertEquals("claude-3-5-sonnet-20241022", result.pricing().getModel());
        assertTrue(result.fuzzyMatched());
    }

    @Test
    void resolve_versionedGemini_matchesBaseModel() {
        when(modelPricingRepository.findById("gemini-2.0-flash-001")).thenReturn(Optional.empty());
        stubFindAllWithSeedModels();
        ModelPricing pricing = makeModelPricing("gemini-2.0-flash", "google");
        when(modelPricingRepository.findById("gemini-2.0-flash")).thenReturn(Optional.of(pricing));

        ModelPricingResolver.ResolvedPricing result = resolver.resolve("gemini-2.0-flash-001");

        assertNotNull(result);
        assertEquals("gemini-2.0-flash", result.pricing().getModel());
        assertTrue(result.fuzzyMatched());
    }

    @Test
    void resolve_unknownModel_returnsNull() {
        when(modelPricingRepository.findById("totally-unknown-model")).thenReturn(Optional.empty());

        ModelPricingResolver.ResolvedPricing result = resolver.resolve("totally-unknown-model");

        assertNull(result);
    }

    @Test
    void resolve_unknownProviderModel_returnsNull() {
        when(modelPricingRepository.findById("somerand-model-v2")).thenReturn(Optional.empty());

        ModelPricingResolver.ResolvedPricing result = resolver.resolve("somerand-model-v2");

        assertNull(result);
    }

    // --- Caching tests ---

    @Test
    void resolve_cacheHit_doesNotQueryDbAgain() {
        ModelPricing pricing = makeModelPricing("gpt-4o", "openai");
        when(modelPricingRepository.findById("gpt-4o")).thenReturn(Optional.of(pricing));

        resolver.resolve("gpt-4o");
        resolver.resolve("gpt-4o");

        verify(modelPricingRepository, times(1)).findById("gpt-4o");
    }

    @Test
    void resolve_negativeCaching_doesNotQueryDbAgain() {
        when(modelPricingRepository.findById("totally-unknown-model")).thenReturn(Optional.empty());

        resolver.resolve("totally-unknown-model");
        resolver.resolve("totally-unknown-model");

        verify(modelPricingRepository, times(1)).findById("totally-unknown-model");
    }

    @Test
    void resolve_afterInvalidateCache_queriesDbAgain() {
        ModelPricing pricing = makeModelPricing("gpt-4o", "openai");
        when(modelPricingRepository.findById("gpt-4o")).thenReturn(Optional.of(pricing));

        resolver.resolve("gpt-4o");
        resolver.invalidateCache();
        resolver.resolve("gpt-4o");

        verify(modelPricingRepository, times(2)).findById("gpt-4o");
    }

    // --- Static findLongestPrefixMatch tests ---

    @Test
    void findLongestPrefixMatch_returnsLongestCandidate() {
        List<String> candidates = List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo");

        assertEquals("gpt-4o-mini", ModelPricingResolver.findLongestPrefixMatch("gpt-4o-mini-2024-07-18", candidates));
    }

    @Test
    void findLongestPrefixMatch_requiresDelimiterBoundary() {
        List<String> candidates = List.of("gpt-4o");

        // "gpt-4oops" does NOT start with "gpt-4o" at a delimiter boundary (next char is 'o', not '-' or '.')
        assertNull(ModelPricingResolver.findLongestPrefixMatch("gpt-4oops", candidates));
    }

    @Test
    void findLongestPrefixMatch_dotDelimiter() {
        List<String> candidates = List.of("gemini-2.0-flash");

        assertEquals("gemini-2.0-flash", ModelPricingResolver.findLongestPrefixMatch("gemini-2.0-flash.001", candidates));
    }

    @Test
    void findLongestPrefixMatch_noMatch_returnsNull() {
        List<String> candidates = List.of("gpt-4o", "gpt-4o-mini");

        assertNull(ModelPricingResolver.findLongestPrefixMatch("claude-3-opus", candidates));
    }

    @Test
    void findLongestPrefixMatch_exactLengthMatch_returnsCandidate() {
        List<String> candidates = List.of("gpt-4o");

        // Input is exactly the candidate — boundary at end-of-string
        assertEquals("gpt-4o", ModelPricingResolver.findLongestPrefixMatch("gpt-4o", candidates));
    }

    // --- Helpers ---

    private void stubFindAllWithSeedModels() {
        when(modelPricingRepository.findAll()).thenReturn(List.of(
                makeModelPricing("gpt-4o", "openai"),
                makeModelPricing("gpt-4o-mini", "openai"),
                makeModelPricing("gpt-4-turbo", "openai"),
                makeModelPricing("gpt-4", "openai"),
                makeModelPricing("o1", "openai"),
                makeModelPricing("o1-mini", "openai"),
                makeModelPricing("o3", "openai"),
                makeModelPricing("o3-mini", "openai"),
                makeModelPricing("claude-sonnet-4-5", "anthropic"),
                makeModelPricing("claude-opus-4", "anthropic"),
                makeModelPricing("claude-sonnet-4", "anthropic"),
                makeModelPricing("claude-3-5-sonnet-20241022", "anthropic"),
                makeModelPricing("claude-3-5-haiku-20241022", "anthropic"),
                makeModelPricing("gemini-2.5-pro", "google"),
                makeModelPricing("gemini-2.5-flash", "google"),
                makeModelPricing("gemini-2.0-flash", "google"),
                makeModelPricing("gemini-1.5-pro", "google"),
                makeModelPricing("gemini-1.5-flash", "google")
        ));
    }

    private static ModelPricing makeModelPricing(String model, String provider) {
        ModelPricing mp = new ModelPricing();
        mp.setModel(model);
        mp.setProvider(provider);
        mp.setInputCostPerMillion(BigDecimal.valueOf(3.0));
        mp.setOutputCostPerMillion(BigDecimal.valueOf(15.0));
        return mp;
    }
}
