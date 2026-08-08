package com.contractguard.dto;

import com.contractguard.entity.RiskLevel;

/**
 * One row of "how many clauses of this risk level does this document have".
 *
 * Populated directly by a JPQL constructor expression, so the database does the
 * counting and returns a handful of rows rather than us loading every clause
 * into memory just to count them.
 */
public record RiskCount(Long documentId, RiskLevel riskLevel, Long total) {
}
