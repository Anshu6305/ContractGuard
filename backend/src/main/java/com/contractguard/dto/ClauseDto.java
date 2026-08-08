package com.contractguard.dto;

import com.contractguard.entity.Clause;
import com.contractguard.entity.RiskLevel;

public record ClauseDto(
        Long id,
        int orderIndex,
        String heading,
        String originalText,
        Integer startOffset,
        Integer endOffset,
        RiskLevel riskLevel,
        String plainSummary,
        String rationale
) {
    /**
     * Entities are never returned straight from a controller. Mapping to a DTO
     * keeps the JSON contract stable when the entity changes, and stops lazy
     * associations (like Clause.document) being serialised by accident, which
     * would recurse forever.
     */
    public static ClauseDto from(Clause clause) {
        return new ClauseDto(
                clause.getId(),
                clause.getOrderIndex(),
                clause.getHeading(),
                clause.getOriginalText(),
                clause.getStartOffset(),
                clause.getEndOffset(),
                clause.getRiskLevel(),
                clause.getPlainSummary(),
                clause.getRationale()
        );
    }
}
