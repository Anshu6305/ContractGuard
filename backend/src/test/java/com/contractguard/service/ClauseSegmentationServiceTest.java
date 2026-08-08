package com.contractguard.service;

import com.contractguard.entity.Clause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Segmentation behaviour, including the three cases that broke the original
 * heading-regex approach: statutory citations read as headings, sub-clauses
 * split away from their parent, and preamble text dropped before the first
 * numbered clause.
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
    @DisplayName("splits numbered paragraphs that have no headings at all")
    void splitsHeadinglessNumberedParagraphs() {
        // Real deeds frequently have no titles at all - every clause is a
        // numbered paragraph whose first line runs past any sensible heading
        // length. The original regex capped the text after the number at 80
        // characters and silently skipped every clause of this shape.
        //
        // The line lengths below matter: each numbered line is deliberately
        // over 100 characters, matching the source document. Wrapping them
        // shorter would put them back under MAX_HEADING_LENGTH and stop the
        // test exercising the case it exists for.
        String text = """
                1. That, the lessor hereby leases to the lessee the following described premises at Plot No 10-2CM-819/A,
                Cuttack, together with all fixtures and fittings presently installed therein and listed in the schedule.
                2. That the premises shall be used for the purpose of a hostel of the lessee for a period of 24 (twenty four)
                months commencing from the date first written above and for no other purpose whatsoever.
                3. That, the lessee shall hold the said premises for a period of 24 (twenty four) months beginning from
                the first day of the month next following the execution of this deed of agreement.
                """;

        List<Clause> clauses = service.segment(text);

        assertThat(clauses).hasSize(3);
        // No headings recorded - these opening lines are paragraphs, not titles.
        assertThat(clauses).allSatisfy(c -> assertThat(c.getHeading()).isNull());
        assertThat(clauses.get(1).getOriginalText()).contains("hostel");
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
