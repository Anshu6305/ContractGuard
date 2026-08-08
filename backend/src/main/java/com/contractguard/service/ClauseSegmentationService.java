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
 * Boundaries are numbered lines — "7. Termination", "12) Indemnity", or a bare
 * numbered paragraph like "1. That, the lessor hereby leases...". A heading is
 * only recorded when the numbered line is short enough to actually be a title;
 * many real contracts have none, and Clause.heading is nullable for that reason.
 *
 * Two rules do most of the work:
 *
 *   SEQUENCE. Candidate boundaries are kept only if their numbers ascend. A
 *   citation such as "8. of the Companies Act, 2013" appearing inside clause 1
 *   is discarded because 8 does not follow 1. Implemented by taking the longest
 *   ascending run rather than trusting every match.
 *
 *   WHITESPACE AFTER THE SEPARATOR. Requiring "7." to be followed by a space
 *   means "7.1" and "7.2" are not boundaries, so sub-clauses stay attached to
 *   their parent instead of being split out as clauses of their own.
 *
 * Text before the first boundary is kept as a preamble clause. Fragments below
 * MIN_CLAUSE_LENGTH are merged into the preceding clause rather than dropped.
 */
@Service
public class ClauseSegmentationService {

    /** Minimum characters for a segment to stand as a clause of its own. */
    private static final int MIN_CLAUSE_LENGTH = 40;

    /**
     * Above this length the numbered line is a paragraph, not a title, so no
     * heading is recorded and the UI falls back to "Clause N".
     */
    private static final int MAX_HEADING_LENGTH = 90;

    /** A line opening with a number, then '.' or ')', then whitespace, then text. */
    private static final Pattern NUMBERED_LINE =
            Pattern.compile("(?m)^[ \\t]*(\\d{1,2})[.)][ \\t]+(\\S[^\\n]*)$");

    public List<Clause> segment(String text) {
        List<Clause> clauses = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return clauses;
        }

        List<Boundary> boundaries = findBoundaries(text);
        if (boundaries.isEmpty()) {
            return segmentByParagraph(text);
        }

        int order = 0;

        // Preamble: recitals and party details before the first numbered clause.
        int firstStart = boundaries.get(0).start();
        String preamble = text.substring(0, firstStart).trim();
        if (preamble.length() >= MIN_CLAUSE_LENGTH) {
            clauses.add(new Clause(order++, null, preamble, 0, firstStart));
        }

        for (int i = 0; i < boundaries.size(); i++) {
            Boundary boundary = boundaries.get(i);
            int start = boundary.start();
            int end = (i + 1 < boundaries.size()) ? boundaries.get(i + 1).start() : text.length();

            String body = text.substring(start, end).trim();
            if (body.isEmpty()) {
                continue;
            }

            // Too short to stand alone: fold it into the previous clause so no
            // text is silently lost.
            if (body.length() < MIN_CLAUSE_LENGTH && !clauses.isEmpty()) {
                Clause previous = clauses.remove(clauses.size() - 1);
                clauses.add(new Clause(
                        previous.getOrderIndex(),
                        previous.getHeading(),
                        previous.getOriginalText() + "\n" + body,
                        previous.getStartOffset(),
                        end));
                continue;
            }

            String heading = boundary.line().length() <= MAX_HEADING_LENGTH ? boundary.line() : null;
            clauses.add(new Clause(order++, heading, body, start, end));
        }

        return clauses;
    }

    /**
     * Every numbered line, reduced to the longest run whose numbers ascend.
     *
     * Runs are grown from each candidate and the longest wins, so a stray
     * number early in the document cannot derail the real sequence. A gap of one
     * is tolerated, which covers a clause whose number failed to extract cleanly.
     */
    private List<Boundary> findBoundaries(String text) {
        List<Boundary> candidates = new ArrayList<>();
        Matcher matcher = NUMBERED_LINE.matcher(text);
        while (matcher.find()) {
            candidates.add(new Boundary(
                    matcher.start(),
                    Integer.parseInt(matcher.group(1)),
                    matcher.group().trim()));
        }

        List<Boundary> best = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            List<Boundary> run = new ArrayList<>();
            run.add(candidates.get(i));
            int last = candidates.get(i).number();

            for (int j = i + 1; j < candidates.size(); j++) {
                int number = candidates.get(j).number();
                if (number > last && number <= last + 2) {
                    run.add(candidates.get(j));
                    last = number;
                }
            }

            if (run.size() > best.size()) {
                best = run;
            }
        }
        return best;
    }

    /** Fallback for documents with no numbering: one clause per paragraph. */
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

    /** A candidate clause start: where it begins, its number, and its opening line. */
    private record Boundary(int start, int number, String line) {
    }
}
