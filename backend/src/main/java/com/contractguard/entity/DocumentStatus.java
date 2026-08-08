package com.contractguard.entity;

/**
 * Lifecycle of an uploaded document.
 *
 * UPLOADED -> PROCESSING -> COMPLETED
 *                        -> FAILED
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    COMPLETED,
    FAILED
}
