package com.contractguard.service;

import com.contractguard.dto.AnalyzedClause;
import com.contractguard.dto.ClauseDto;
import com.contractguard.dto.DocumentDetailDto;
import com.contractguard.dto.DocumentSummaryDto;
import com.contractguard.dto.RiskCount;
import com.contractguard.entity.Clause;
import com.contractguard.entity.Document;
import com.contractguard.entity.DocumentStatus;
import com.contractguard.entity.RiskLevel;
import com.contractguard.entity.User;
import com.contractguard.exception.PdfProcessingException;
import com.contractguard.exception.ResourceNotFoundException;
import com.contractguard.repository.DocumentRepository;
import com.contractguard.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the upload -> extract -> segment -> analyse -> persist pipeline.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Path STORAGE_DIR = Paths.get("uploads");

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PdfExtractionService pdfExtractionService;
    private final ClauseSegmentationService segmentationService;
    private final ClauseAnalyzerService analyzerService;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           PdfExtractionService pdfExtractionService,
                           ClauseSegmentationService segmentationService,
                           ClauseAnalyzerService analyzerService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.pdfExtractionService = pdfExtractionService;
        this.segmentationService = segmentationService;
        this.analyzerService = analyzerService;
    }

    @Transactional
    public DocumentSummaryDto upload(MultipartFile file, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validate(file);
        Path stored = store(file);

        Document document = new Document(
                user,
                file.getOriginalFilename(),
                stored.toString(),
                file.getSize()
        );
        documentRepository.save(document);

        // Analyse inline for now. Once this works, move it to @Async so the HTTP
        // response returns immediately and the frontend polls for status --
        // see the TODO on analyzeAsync below.
        //
        // Worth knowing: this is a self-invocation, so the @Transactional on
        // analyze() does NOT start a new transaction -- Spring's proxy is
        // bypassed when a bean calls its own method. Here that is harmless
        // because upload() is already transactional and analyze() simply joins
        // it. But self-invocation silently defeating @Transactional (and
        // @Async, and @Cacheable) is a common source of subtle bugs.
        analyze(document.getId());

        return DocumentSummaryDto.from(
                documentRepository.findById(document.getId()).orElseThrow());
    }

    @Transactional
    public void analyze(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        try {
            byte[] bytes = Files.readAllBytes(Paths.get(document.getStoredPath()));
            String text = pdfExtractionService.extractText(bytes);
            document.setExtractedText(text);

            List<Clause> clauses = segmentationService.segment(text);
            log.info("Segmented document {} into {} clauses", documentId, clauses.size());

            for (Clause clause : clauses) {
                AnalyzedClause result = analyzerService.analyze(clause.getOriginalText());
                clause.setRiskLevel(result.riskLevel());
                clause.setPlainSummary(result.plainSummary());
                clause.setRationale(result.rationale());
                document.addClause(clause);
            }

            document.setOverallScore(calculateOverallScore(clauses));
            document.setAnalyzedAt(Instant.now());
            document.setStatus(DocumentStatus.COMPLETED);

        } catch (PdfProcessingException ex) {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason(ex.getMessage());
        } catch (IOException ex) {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason("Stored file could not be read");
            log.error("Read failure for document {}", documentId, ex);
        }

        documentRepository.save(document);
    }

    /**
     * Calculates a 0-100 safety score for the assessed clauses in a contract.
     *
     * SAFE clauses contribute 100 points, MODERATE clauses contribute 50 points,
     * and RISKY clauses contribute 0 points. UNKNOWN clauses are excluded because
     * they were not assessed and should not affect the final score.
     *
     * The score is capped at 60 when any RISKY clause is present so that a large
     * number of safe clauses cannot hide a potentially serious contractual risk.
     * This deliberately favors avoiding false reassurance over giving a high score
     * to a contract containing a known risky clause.
     */
    int calculateOverallScore(List<Clause> clauses) {
        int totalScore = 0;
        int assessedClauses = 0;
        boolean hasRiskyClause = false;

        for (Clause clause : clauses) {
            RiskLevel riskLevel = clause.getRiskLevel();

            if (riskLevel == RiskLevel.UNKNOWN) {
                continue;
            }

            switch (riskLevel) {
                case SAFE -> totalScore += 100;
                case MODERATE -> totalScore += 50;
                case RISKY -> {
                    totalScore += 0;
                    hasRiskyClause = true;
                }
                case UNKNOWN -> {
                    // Already handled above.
                }
            }

            assessedClauses++;
        }

        if (assessedClauses == 0) {
            return 0;
        }

        int score = (int) Math.round((double) totalScore / assessedClauses);

        if (hasRiskyClause) {
            score = Math.min(score, 60);
        }

        return score;
    }

    /**
     * TODO: switch upload() to call this, add @EnableAsync to the application
     *       class, and have the frontend poll GET /api/documents/{id} until
     *       status != PROCESSING.
     *
     * Analysing a 40-clause contract means 40 sequential LLM calls. At roughly
     * a second each the HTTP request hangs for 40s, and most proxies time out at
     * 30. Moving the work off the request thread is the fix; a full task queue
     * (Redis/Celery) would be overkill at this scale.
     */
    @Async
    public void analyzeAsync(Long documentId) {
        analyze(documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryDto> listForUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Document> documents =
                documentRepository.findByUserIdOrderByUploadedAtDesc(user.getId());

        // TWO queries total, regardless of how many documents the user has.
        // Looping and counting per document would be one query per document --
        // the N+1 problem. Grouping in the database avoids it.
        Map<Long, Map<RiskLevel, Integer>> counts = documentRepository
                .countClausesByRiskForUser(user.getId())
                .stream()
                .collect(Collectors.groupingBy(
                        RiskCount::documentId,
                        Collectors.toMap(
                                RiskCount::riskLevel,
                                row -> row.total().intValue())));

        return documents.stream()
                .map(document -> {
                    Map<RiskLevel, Integer> byLevel =
                            counts.getOrDefault(document.getId(), Map.of());
                    return DocumentSummaryDto.of(
                            document,
                            byLevel.getOrDefault(RiskLevel.RISKY, 0),
                            byLevel.getOrDefault(RiskLevel.MODERATE, 0),
                            byLevel.getOrDefault(RiskLevel.SAFE, 0),
                            byLevel.getOrDefault(RiskLevel.UNKNOWN, 0));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDetailDto getDetail(Long documentId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = documentRepository
                .findWithClausesByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        return new DocumentDetailDto(
                document.getId(),
                document.getOriginalFilename(),
                document.getStatus(),
                document.getOverallScore(),
                document.getExtractedText(),
                document.getClauses().stream().map(ClauseDto::from).toList(),
                document.getFailureReason()
        );
    }

    @Transactional
    public void delete(Long documentId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document document = documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        documentRepository.delete(document);
    }

    // ---------------------------------------------------------------- helpers

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PdfProcessingException("No file was uploaded");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new PdfProcessingException("Only PDF files are accepted");
        }
    }

    /**
     * Stores under a random UUID rather than the user's filename. Using the
     * uploaded name directly would let someone upload "../../etc/passwd" and
     * escape the directory -- a path traversal vulnerability.
     */
    private Path store(MultipartFile file) {
        try {
            Files.createDirectories(STORAGE_DIR);
            Path target = STORAGE_DIR.resolve(UUID.randomUUID() + ".pdf");
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException ex) {
            throw new PdfProcessingException("Could not store the uploaded file", ex);
        }
    }

}
