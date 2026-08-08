package com.contractguard.entity;

/**
 * Risk tier assigned to a single clause.
 *
 * Stored with @Enumerated(EnumType.STRING) rather than ORDINAL so that adding
 * or reordering a tier later cannot silently reinterpret existing rows. ORDINAL
 * persists the position in this list, which makes the enum order part of the
 * database contract.
 */
public enum RiskLevel {
    SAFE,
    MODERATE,
    RISKY,
    /** Analysis could not classify this clause (LLM failure, unparseable output). */
    UNKNOWN
}
