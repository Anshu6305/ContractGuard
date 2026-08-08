package com.contractguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single segmented clause plus its analysis.
 *
 * startOffset/endOffset are character positions into Document.extractedText.
 * They exist so the frontend can highlight the exact span in the original
 * document. Storing offsets rather than a copy of the text is what makes
 * click-to-scroll possible later without re-running the analysis.
 */
@Entity
@Table(
        name = "clauses",
        indexes = {
                @Index(name = "idx_clauses_document", columnList = "document_id"),
                @Index(name = "idx_clauses_risk", columnList = "risk_level")
        }
)
public class Clause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Position of this clause within the document, starting at 0. */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    /** e.g. "7. Termination". Null when the segmenter found no heading. */
    @Column(length = 300)
    private String heading;

    // See the note in Document.extractedText: @Lob on a String does not give
    // you a text column on MySQL under Hibernate 6. Be explicit.
    @Column(name = "original_text", nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "start_offset")
    private Integer startOffset;

    @Column(name = "end_offset")
    private Integer endOffset;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel = RiskLevel.UNKNOWN;

    /** Plain-English rewrite of the clause. */
    @Column(name = "plain_summary", columnDefinition = "TEXT")
    private String plainSummary;

    /** Why the model assigned that risk level. Shown to the user on demand. */
    @Column(columnDefinition = "TEXT")
    private String rationale;

    protected Clause() {
    }

    public Clause(int orderIndex, String heading, String originalText,
                  Integer startOffset, Integer endOffset) {
        this.orderIndex = orderIndex;
        this.heading = heading;
        this.originalText = originalText;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public Long getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    void setDocument(Document document) {
        // Package-private on purpose: callers should use Document.addClause(...)
        // so both sides of the relationship stay consistent.
        this.document = document;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public String getOriginalText() {
        return originalText;
    }

    public Integer getStartOffset() {
        return startOffset;
    }

    public Integer getEndOffset() {
        return endOffset;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getPlainSummary() {
        return plainSummary;
    }

    public void setPlainSummary(String plainSummary) {
        this.plainSummary = plainSummary;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }
}
