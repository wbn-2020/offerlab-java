package com.offerlab.community.search.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchQuerySpecTest {

    @Test
    void normalizesUnicodeWhitespaceAndPunctuationIntoSharedTerms() {
        SearchQuerySpec query = SearchQuerySpec.from("  OfferLab，Elasticsearch/MySQL  ");

        assertEquals("OfferLab,Elasticsearch/MySQL", query.normalized());
        assertEquals(List.of("OfferLab", "Elasticsearch", "MySQL"), query.terms());
        assertEquals(2, query.minimumTermMatches());
    }

    @Test
    void ignoresWeakTermsWithoutTurningThemIntoMatchAll() {
        SearchQuerySpec query = SearchQuerySpec.from("的 和 a");

        assertFalse(query.emptyInput());
        assertFalse(query.hasMeaningfulTerms());
        assertTrue(query.requiresNoMatches());
    }

    @Test
    void preservesNumericIdentifiersForExactPostLookup() {
        SearchQuerySpec query = SearchQuerySpec.from("909");

        assertTrue(query.numericIdentifier());
        assertFalse(query.requiresNoMatches());
        assertEquals(List.of(), query.terms());
        assertEquals(0, query.minimumTermMatches());
    }

    @Test
    void raisesTheRequiredTermThresholdForLongQueries() {
        SearchQuerySpec query = SearchQuerySpec.from("OfferLab Elasticsearch MySQL fallback consistency");

        assertEquals(5, query.terms().size());
        assertEquals(4, query.minimumTermMatches());
    }
}
