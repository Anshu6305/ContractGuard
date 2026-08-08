package com.contractguard.service;

import com.contractguard.entity.Clause;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits raw contract text into individual clauses.
 *
 * The current strategy is a numbered-heading regex: find lines matching
 * "7. Termination" or "12) Indemnity" and cut the document at each one, falling
 * back to blank-line paragraph splitting when a document has no numbering at
 * all. Accuracy on real contracts is roughly 60%.
 *
 * Known limitations, tracked as failing tests in ClauseSegmentationServiceTest:
 *
 *   1. Sub-clauses. "7.1" and "7.2" are treated as top-level clauses rather
 *      than being merged into clause 7.
 *   2. ALL-CAPS headings. Contracts that write "TERMINATION" with no number
 *      are missed entirely.
 *   3. Citation false positives. "Section 8 of the Companies Act, 2013" looks
 *      like a heading to the regex and wrongly splits the document.
 *   4. Tiny fragments below MIN_CLAUSE_LENGTH are dropped instead of being
 *      merged into the preceding clause.
 *   5. Preamble text before the first heading is discarded.
 */
@Service
public class ClauseSegmentationService {

    /** Minimum characters for a segment to count as a real clause. */
    private static final int MIN_CLAUSE_LENGTH = 40;

    /**
     * Matches a line starting with a number followed by '.' or ')', then a
     * capitalised word. e.g. "7. Termination", "12) Indemnity".
     */
    private static final Pattern HEADING = Pattern.compile(
            "(?m)^\\s*(\\d{1,2})[.)]\\s+([A-Z][^\\n]{2,80})$");

    public List<Clause> segment(String text) {
        List<Clause> clauses = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return clauses;
        }

        Matcher matcher = HEADING.matcher(text);
        List<int[]> boundaries = new ArrayList<>();
        List<String> headings = new ArrayList<>();

        while (matcher.find()) {
            boundaries.add(new int[]{matcher.start(), matcher.end()});
            headings.add(matcher.group().trim());
        }

        // No headings found at all: fall back to splitting on blank lines so the
        // pipeline still produces something rather than failing.
        if (boundaries.isEmpty()) {
            return segmentByParagraph(text);
        }

        int order = 0;
        for (int i = 0; i < boundaries.size(); i++) {
            int start = boundaries.get(i)[0];
            int end = (i + 1 < boundaries.size()) ? boundaries.get(i + 1)[0] : text.length();

            String body = text.substring(start, end).trim();
            if (body.length() < MIN_CLAUSE_LENGTH) {
                // TODO: merge into the previous clause instead of dropping it.
                continue;
            }

            clauses.add(new Clause(order++, headings.get(i), body, start, end));
        }

        // TODO: capture the preamble (text before boundaries.get(0)[0]) as
        //       clause 0 when it exceeds MIN_CLAUSE_LENGTH.

        return clauses;
    }

    /**
     * Fallback for documents with no recognisable headings: treat each
     * blank-line-separated block as a clause.
     */
    private List<Clause> segmentByParagraph(String text) {
        List<Clause> clauses = new ArrayList<>();
        int order = 0;
        int cursor = 0;

        for (String block : text.split("\\n{2,}")) {
            int start = text.indexOf(block, cursor);
            if (start < 0) {
                start = cursor;
            }
            int end = start + block.length();
            cursor = end;

            String trimmed = block.trim();
            if (trimmed.length() >= MIN_CLAUSE_LENGTH) {
                clauses.add(new Clause(order++, null, trimmed, start, end));
            }
        }
        return clauses;
    }
}
