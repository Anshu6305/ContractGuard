package com.contractguard.service;

import com.contractguard.entity.Clause;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first three tests cover the current behaviour.
 *
 * The @Disabled ones are known segmentation bugs, written as failing tests so
 * the backlog lives in code rather than in a comment. Each one enabled is a bug
 * actually fixed.
 */
@DisplayName("ClauseSegmentationService")
class ClauseSegmentationServiceTest {

    private final ClauseSegmentationService service = new ClauseSegmentationService();

    @Test
    @DisplayName("splits a contract at numbered headings")
    void splitsOnNumberedHeadings() {
        String text = """
                1. Term and Duration
                This agreement shall remain in force for eleven months from the date of signing.

                2. Termination
                The Landlord may terminate this agreement at any time without prior notice to the Tenant.

                3. Security Deposit
                The Tenant shall deposit a sum equal to three months rent, refundable on vacating.
                """;

        List<Clause> clauses = service.segment(text);

        assertThat(clauses).hasSize(3);
        assertThat(clauses.get(0).getHeading()).startsWith("1. Term");
        assertThat(clauses.get(1).getHeading()).startsWith("2. Termination");
    }

    @Test
    @DisplayName("records character offsets that map back into the source text")
    void offsetsPointAtOriginalText() {
        String text = """
                1. Payment Terms
                Rent is payable in advance on the first day of each calendar month without demand.

                2. Maintenance
                The Tenant shall keep the premises in good and tenantable repair at all times.
                """;

        List<Clause> clauses = service.segment(text);

        // The offsets are what make click-to-highlight possible in the frontend,
        // so this property matters more than it looks.
        for (Clause clause : clauses) {
            String slice = text.substring(clause.getStartOffset(), clause.getEndOffset());
            assertThat(slice).contains(clause.getHeading());
        }
    }

    @Test
    @DisplayName("falls back to paragraph splitting when there are no headings")
    void fallsBackToParagraphs() {
        String text = """
                The parties agree that this document constitutes the entire understanding
                between them and supersedes all prior discussions and correspondence.

                Neither party shall assign its rights hereunder without the prior written
                consent of the other party, such consent not to be unreasonably withheld.
                """;

        List<Clause> clauses = service.segment(text);

        assertThat(clauses).hasSize(2);
        assertThat(clauses.get(0).getHeading()).isNull();
    }

    @Test
    @Disabled("Known bug: statutory citations are mistaken for headings")
    @DisplayName("does not split on a citation like 'Section 8 of the Companies Act'")
    void ignoresCitations() {
        String text = """
                1. Compliance
                The Company shall comply with all applicable law. In particular the parties
                note the following obligation.
                8. of the Companies Act, 2013 shall apply to any transfer of shares made
                under this agreement by either party at any time.
                """;

        List<Clause> clauses = service.segment(text);

        assertThat(clauses).hasSize(1);
    }

    @Test
    @Disabled("Known bug: sub-clauses are split out instead of merged into the parent")
    @DisplayName("treats 7.1 and 7.2 as part of clause 7, not as separate clauses")
    void mergesSubClauses() {
        String text = """
                7. Indemnity
                The Tenant shall indemnify the Landlord against all claims arising from use.
                7.1 This indemnity survives termination of the agreement for a period of one year.
                7.2 The indemnity is capped at the total rent paid under this agreement.

                8. Governing Law
                This agreement is governed by the laws of India and subject to Bhubaneswar courts.
                """;

        List<Clause> clauses = service.segment(text);

        assertThat(clauses).hasSize(2);
        assertThat(clauses.get(0).getOriginalText()).contains("7.1").contains("7.2");
    }

    @Test
    @Disabled("Known bug: preamble before the first heading is dropped")
    @DisplayName("keeps text that appears before the first numbered heading")
    void capturesPreamble() {
        String text = """
                This Rental Agreement is made on the first day of August between the Landlord
                and the Tenant, both of whom are of sound mind and legal age to contract.

                1. Term
                The tenancy shall run for a period of eleven months from the date of signing.
                """;

        List<Clause> clauses = service.segment(text);

        assertThat(clauses).hasSize(2);
        assertThat(clauses.get(0).getOriginalText()).contains("This Rental Agreement is made");
    }
}
