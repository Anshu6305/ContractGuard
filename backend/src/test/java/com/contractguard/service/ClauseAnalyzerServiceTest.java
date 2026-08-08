package com.contractguard.service;

import com.contractguard.dto.AnalyzedClause;
import com.contractguard.entity.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the analyzer.
 *
 * No Spring context and no network call: LlmClient is a Mockito mock, so these
 * run in milliseconds and are deterministic. That is the reason LlmClient exists
 * as a separate class at all - keeping HTTP out of the analyzer is what makes
 * the analyzer testable.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClauseAnalyzerService")
class ClauseAnalyzerServiceTest {

    @Mock
    private LlmClient llmClient;

    private ClauseAnalyzerService analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ClauseAnalyzerService(llmClient, new ObjectMapper());
    }

    @Test
    @DisplayName("parses a well-formed JSON reply")
    void parsesValidJson() {
        when(llmClient.complete(anyString(), anyString())).thenReturn("""
                {
                  "rationale": "Landlord may evict without notice.",
                  "riskLevel": "RISKY",
                  "plainSummary": "You can be evicted with no warning."
                }
                """);

        AnalyzedClause result = analyzer.analyze("The Landlord may terminate at any time.");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.RISKY);
        assertThat(result.plainSummary()).isEqualTo("You can be evicted with no warning.");
        assertThat(result.rationale()).contains("without notice");
    }

    @Test
    @DisplayName("strips markdown code fences the model sometimes adds")
    void stripsMarkdownFences() {
        when(llmClient.complete(anyString(), anyString())).thenReturn(
                "```json\n{\"riskLevel\":\"SAFE\",\"plainSummary\":\"Standard.\",\"rationale\":\"Balanced.\"}\n```");

        AnalyzedClause result = analyzer.analyze("Either party may terminate with 30 days notice.");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.SAFE);
    }

    @Test
    @DisplayName("falls back to UNKNOWN when the model invents a risk level")
    void handlesUnexpectedEnumValue() {
        when(llmClient.complete(anyString(), anyString())).thenReturn(
                "{\"riskLevel\":\"VERY_DANGEROUS\",\"plainSummary\":\"x\",\"rationale\":\"y\"}");

        AnalyzedClause result = analyzer.analyze("Some clause text that is long enough.");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
    }

    @Test
    @DisplayName("returns UNKNOWN instead of throwing when the LLM call fails")
    void survivesLlmFailure() {
        when(llmClient.complete(anyString(), anyString()))
                .thenThrow(new LlmClient.LlmException("provider returned HTTP 503"));

        AnalyzedClause result = analyzer.analyze("Some clause text that is long enough.");

        // One failed clause must not fail the whole document.
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(result.plainSummary()).isNull();
    }

    @Test
    @DisplayName("records why analysis failed, not just that it did")
    void preservesTheFailureReason() {
        when(llmClient.complete(anyString(), anyString()))
                .thenThrow(new LlmClient.LlmException("provider returned HTTP 429"));

        AnalyzedClause result = analyzer.analyze("Some clause text that is long enough.");

        // The reason is persisted to clauses.rationale so an UNKNOWN clause can
        // be diagnosed from the database rather than from the logs.
        assertThat(result.rationale()).contains("429");
    }

    @Test
    @DisplayName("does not call the model for blank input")
    void skipsBlankClause() {
        AnalyzedClause result = analyzer.analyze("   ");

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        // llmClient was never stubbed and never called - Mockito would fail on
        // an unexpected interaction if it had been.
    }
}
