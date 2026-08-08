package com.contractguard.service;

import com.contractguard.entity.Clause;
import com.contractguard.entity.RiskLevel;
import com.contractguard.repository.DocumentRepository;
import com.contractguard.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification for DocumentService.calculateOverallScore, written before the
 * implementation.
 *
 * The last test is the important one: it encodes a product decision rather than
 * arithmetic. A plain average lets a single catastrophic clause disappear into a
 * reassuring score, so the scoring rule has to deliberately prevent that.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService.calculateOverallScore")
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PdfExtractionService pdfExtractionService;
    @Mock private ClauseSegmentationService segmentationService;
    @Mock private ClauseAnalyzerService analyzerService;

    @InjectMocks private DocumentService documentService;

    private static Clause clauseWith(RiskLevel level) {
        Clause clause = new Clause(0, "Heading", "Some clause text here.", 0, 20);
        clause.setRiskLevel(level);
        return clause;
    }

    private static List<Clause> clauses(RiskLevel... levels) {
        List<Clause> list = new ArrayList<>();
        for (RiskLevel level : levels) {
            list.add(clauseWith(level));
        }
        return list;
    }

    @Test
    @DisplayName("all-safe contract scores 100")
    void allSafeScoresFull() {
        int score = documentService.calculateOverallScore(
                clauses(RiskLevel.SAFE, RiskLevel.SAFE, RiskLevel.SAFE));

        assertThat(score).isEqualTo(100);
    }

    @Test
    @DisplayName("all-risky contract scores 0")
    void allRiskyScoresZero() {
        int score = documentService.calculateOverallScore(
                clauses(RiskLevel.RISKY, RiskLevel.RISKY));

        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("empty document scores 0 rather than dividing by zero")
    void emptyDocumentDoesNotDivideByZero() {
        assertThat(documentService.calculateOverallScore(List.of())).isEqualTo(0);
    }

    @Test
    @DisplayName("UNKNOWN clauses are excluded from the calculation")
    void unknownClausesAreIgnored() {
        // Two SAFE and one UNASSESSED should score the same as two SAFE.
        int withUnknown = documentService.calculateOverallScore(
                clauses(RiskLevel.SAFE, RiskLevel.SAFE, RiskLevel.UNKNOWN));
        int withoutUnknown = documentService.calculateOverallScore(
                clauses(RiskLevel.SAFE, RiskLevel.SAFE));

        assertThat(withUnknown).isEqualTo(withoutUnknown);
    }

    @Test
    @DisplayName("one risky clause among many safe ones still produces a clear warning")
    void singleRiskyClauseIsNotDilutedAway() {
        // 39 safe clauses and 1 risky one. A plain weighted average gives 97.5,
        // which would show the user a reassuring green score on a contract that
        // contains a clause capable of ruining them.
        //
        // Decide what the right behaviour is and encode it here. A cap of 60
        // whenever any RISKY clause exists is one reasonable answer; there are
        // others. What matters is that you chose deliberately and can defend it.
        List<Clause> mixed = new ArrayList<>(clauses(RiskLevel.RISKY));
        for (int i = 0; i < 39; i++) {
            mixed.add(clauseWith(RiskLevel.SAFE));
        }

        int score = documentService.calculateOverallScore(mixed);

        assertThat(score).isLessThanOrEqualTo(60);
    }
}
