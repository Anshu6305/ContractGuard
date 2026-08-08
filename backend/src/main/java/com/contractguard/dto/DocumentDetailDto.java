package com.contractguard.dto;

import com.contractguard.entity.DocumentStatus;

import java.util.List;

public record DocumentDetailDto(
        Long id,
        String filename,
        DocumentStatus status,
        Integer overallScore,
        String extractedText,
        List<ClauseDto> clauses,
        String failureReason
) {
}
