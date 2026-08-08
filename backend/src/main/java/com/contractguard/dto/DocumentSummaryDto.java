package com.contractguard.dto;

import com.contractguard.entity.Document;
import com.contractguard.entity.DocumentStatus;
import com.contractguard.entity.RiskLevel;

import java.time.Instant;
import java.util.List;

public record DocumentSummaryDto(
        Long id,
        String filename,
        DocumentStatus status,
        Integer overallScore,
        Instant uploadedAt,
        int clauseCount,
        int riskyCount,
        int moderateCount,
        int safeCount
) {
    /** Used by the list endpoint, where counts come from an aggregate query. */
    public static DocumentSummaryDto of(Document document,
                                        int risky, int moderate, int safe, int unknown) {
        return new DocumentSummaryDto(
                document.getId(),
                document.getOriginalFilename(),
                document.getStatus(),
                document.getOverallScore(),
                document.getUploadedAt(),
                risky + moderate + safe + unknown,
                risky, moderate, safe
        );
    }

    /**
     * Used right after upload, when the clauses are already loaded in memory --
     * counting them here costs nothing and avoids another round trip.
     */
    public static DocumentSummaryDto from(Document document) {
        List<RiskLevel> levels = document.getClauses().stream()
                .map(clause -> clause.getRiskLevel())
                .toList();

        return of(
                document,
                (int) levels.stream().filter(l -> l == RiskLevel.RISKY).count(),
                (int) levels.stream().filter(l -> l == RiskLevel.MODERATE).count(),
                (int) levels.stream().filter(l -> l == RiskLevel.SAFE).count(),
                (int) levels.stream().filter(l -> l == RiskLevel.UNKNOWN).count()
        );
    }
}
