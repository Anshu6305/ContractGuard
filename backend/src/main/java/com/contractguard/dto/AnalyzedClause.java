package com.contractguard.dto;

import com.contractguard.entity.RiskLevel;

/**
 * The shape we expect back from the language model for one clause.
 * Kept separate from ClauseDto because this is an inbound contract with an
 * external system, not something we serve to our own frontend.
 */
public record AnalyzedClause(
        RiskLevel riskLevel,
        String plainSummary,
        String rationale
) {
    public static AnalyzedClause unknown(String reason) {
        return new AnalyzedClause(RiskLevel.UNKNOWN, null, reason);
    }
}
