package com.contractguard.service;

import com.contractguard.dto.AnalyzedClause;
import com.contractguard.entity.RiskLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Classifies one clause and rewrites it in plain English.
 *
 * On failure this returns AnalyzedClause.unknown(...) rather than throwing, so a
 * single unclassifiable clause cannot fail the whole document. That is
 * deliberate: partial results are far more useful here than an all-or-nothing
 * error, and the failure reason is recorded per clause so it stays diagnosable.
 *
 * Prompt design notes are on systemPrompt() below.
 */
@Service
public class ClauseAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ClauseAnalyzerService.class);

    /** Clauses longer than this are truncated before being sent. */
    private static final int MAX_CLAUSE_CHARS = 6000;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ClauseAnalyzerService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public AnalyzedClause analyze(String clauseText) {
        if (clauseText == null || clauseText.isBlank()) {
            return AnalyzedClause.unknown("Empty clause");
        }

        String trimmed = clauseText.length() > MAX_CLAUSE_CHARS
                ? clauseText.substring(0, MAX_CLAUSE_CHARS)
                : clauseText;

        try {
            String reply = llmClient.complete(systemPrompt(), trimmed);
            return parse(reply);
        } catch (Exception ex) {
            // Record WHY, not just THAT. The reason is persisted to
            // clauses.rationale, so a failed clause can be diagnosed later with
            // a single query instead of by hunting through logs:
            //
            //   SELECT heading, rationale FROM clauses WHERE risk_level='UNKNOWN';
            //
            // "Analysis unavailable" told us nothing. This tells us whether we
            // were rate limited, timed out, or sent a bad key.
            log.warn("Analysis failed for clause: {}", ex.getMessage());
            return AnalyzedClause.unknown("Analysis failed: " + ex.getMessage());
        }
    }

    /**
     * Prompt v2.
     *
     * v1 defined the tiers with adjectives ("slightly favours the drafting
     * party") and scored the sample rental agreement 7 RISKY / 0 MODERATE /
     * 5 SAFE. The middle tier collapsed entirely and classification became
     * effectively binary. Three changes address that:
     *
     *   1. A decision test per tier rather than adjectives. The operative
     *      distinction is whether a clause is unfair in KIND or merely in
     *      DEGREE; that is something a model can actually apply.
     *   2. An explicit calibration statement. Models over-flag when asked to
     *      spot risk, because flagging feels like the helpful answer. Stating
     *      the base rate - most clauses in a normal contract are fine -
     *      counteracts it.
     *   3. One worked example per tier, MODERATE included. A tier with no
     *      example is a tier the model will not reach for. The examples are in
     *      the exact JSON shape the response must take.
     *
     * Measured against samples/GROUND_TRUTH.md after each change.
     *
     * TODO: pass the contract type (rental / employment / NDA) as context. A
     *       90-day notice period is ordinary in employment and aggressive in a
     *       lease, and the model currently has no way to know which it is
     *       reading.
     * TODO: ablate the examples - remove them, re-measure, and confirm they are
     *       earning their token cost.
     */
    private String systemPrompt() {
        return """
                You are helping an ordinary person understand a contract before they
                sign it. You are not giving legal advice.

                Assign exactly one risk level, applying these tests in order.

                RISKY - the clause does at least one of:
                  - lets one party act unilaterally with no notice, cap or recourse
                  - waives a legal right, or removes access to courts
                  - creates an unlimited or uncapped obligation
                  - makes one party's own judgement final and unchallengeable

                MODERATE - ordinary in kind but tilted in degree. Typical signs: a
                notice period, deposit or penalty larger than customary but not
                absurd; an obligation that is one-sided but bounded; a right that
                exists but is inconvenient to exercise.

                SAFE - balanced, or standard boilerplate a reasonable person expects.

                CALIBRATION: in a normal contract most clauses are SAFE. Do not mark a
                clause RISKY merely because it imposes an obligation on someone --
                obligations are what contracts are made of. Reserve RISKY for the tests
                above. If a clause is unfair in DEGREE but not in KIND, it is MODERATE.

                Examples. Note these are in exactly the output format you must use.

                INPUT: "The Tenant shall pay a security deposit of three months rent,
                refundable within 30 days of vacating, less documented damage."
                OUTPUT: {"rationale":"Deposit is customary and the refund terms are
                defined and bounded.","riskLevel":"SAFE","plainSummary":"You pay three
                months rent up front and get it back within a month of moving out,
                minus any damage they can document."}

                INPUT: "The Tenant shall give three months written notice to terminate.
                The Landlord shall give one month."
                OUTPUT: {"rationale":"Notice periods are asymmetric and favour the
                landlord, but both are bounded and ordinary in
                kind.","riskLevel":"MODERATE","plainSummary":"You must give three
                months notice to leave, but the landlord only owes you one."}

                INPUT: "The Landlord may terminate at any time without notice and the
                Tenant shall forfeit the entire deposit."
                OUTPUT: {"rationale":"Unilateral termination with no notice plus total
                forfeiture leaves the tenant no
                recourse.","riskLevel":"RISKY","plainSummary":"The landlord can evict
                you with no warning and keep your whole deposit."}

                Respond with JSON only, in this exact shape:
                {
                  "rationale": "<one sentence: why this level>",
                  "riskLevel": "SAFE" | "MODERATE" | "RISKY",
                  "plainSummary": "<the clause in plain English, max 2 sentences>"
                }
                """;
    }

    /**
     * Parses the model's JSON reply defensively. Models occasionally wrap JSON
     * in markdown fences or return an unexpected enum value, and neither should
     * crash the pipeline.
     */
    private AnalyzedClause parse(String reply) {
        try {
            String cleaned = reply.trim()
                    .replaceAll("^```(?:json)?\\s*", "")
                    .replaceAll("\\s*```$", "");

            JsonNode node = objectMapper.readTree(cleaned);

            RiskLevel level;
            try {
                level = RiskLevel.valueOf(node.path("riskLevel").asText().toUpperCase());
            } catch (IllegalArgumentException ex) {
                level = RiskLevel.UNKNOWN;
            }

            return new AnalyzedClause(
                    level,
                    node.path("plainSummary").asText(null),
                    node.path("rationale").asText(null)
            );
        } catch (Exception ex) {
            log.warn("Could not parse model reply: {}", ex.getMessage());
            return AnalyzedClause.unknown("Could not parse model response");
        }
    }
}
