package com.contractguard.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One uploaded contract PDF and the result of analysing it.
 */
@Entity
@Table(
        name = "documents",
        indexes = {
                // Every "list my documents, newest first" query filters on user_id
                // and sorts on uploaded_at. A composite index on both means the
                // database can satisfy it without a filesort.
                @Index(name = "idx_documents_user_uploaded", columnList = "user_id, uploaded_at")
        }
)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY so that loading a Document does not automatically drag the User row
     * along with it. EAGER on @ManyToOne is the JPA default and is a classic
     * source of N+1 query problems.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_path", nullable = false)
    private String storedPath;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    /**
     * Raw text extracted from the PDF. Kept so re-analysis needs no re-parse.
     *
     * NOT @Lob. In Hibernate 6, @Lob on a String does not render LONGTEXT on
     * MySQL -- it falls back to varchar(255), and any longer value fails the
     * insert with "Data too long for column" (SQL error 1406). An explicit
     * columnDefinition is unambiguous. H2 accepts LONGTEXT because the test and
     * dev URLs both set MODE=MySQL.
     */
    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    /**
     * 0-100. Higher means safer. Derived from the clause mix -- see
     * DocumentService.calculateOverallScore.
     */
    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    /**
     * orphanRemoval = true means deleting a clause from this list deletes the row.
     * Combined with CascadeType.ALL, deleting a Document cleans up its clauses.
     */
    @OneToMany(
            mappedBy = "document",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("orderIndex ASC")
    private List<Clause> clauses = new ArrayList<>();

    protected Document() {
    }

    public Document(User user, String originalFilename, String storedPath, Long sizeBytes) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.storedPath = storedPath;
        this.sizeBytes = sizeBytes;
    }

    /**
     * Keeps both sides of the bidirectional relationship in sync. Forgetting to
     * do this is the single most common JPA bug in student projects: the child
     * is added to the list but its foreign key is never set.
     */
    public void addClause(Clause clause) {
        clauses.add(clause);
        clause.setDocument(this);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(Instant analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public List<Clause> getClauses() {
        return clauses;
    }
}
