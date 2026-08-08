package com.contractguard.repository;

import com.contractguard.dto.RiskCount;
import com.contractguard.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * Ownership-scoped lookup. Always fetching by (id, userId) rather than id
     * alone means one user can never read another user's document by guessing
     * an id -- an IDOR vulnerability. Worth pointing out in a demo.
     */
    Optional<Document> findByIdAndUserId(Long id, Long userId);

    /**
     * Explicit JOIN FETCH to load a document together with its clauses in a
     * single query. Without this, iterating document.getClauses() triggers a
     * second query -- the N+1 problem.
     */
    @Query("select d from Document d left join fetch d.clauses where d.id = :id and d.user.id = :userId")
    Optional<Document> findWithClausesByIdAndUserId(Long id, Long userId);

    /**
     * Risk-level counts for every document belonging to one user, in ONE query.
     *
     * The naive way to show "3 risky clauses" on a list of 20 documents is to
     * loop and call count() per document -- 20 extra queries, the N+1 problem
     * again. This groups in the database instead and returns a few dozen rows,
     * which the service folds into the DTOs.
     *
     * "select new com.contractguard.dto.RiskCount(...)" is a JPQL constructor
     * expression: Hibernate instantiates the record directly from the result
     * set, so nothing untyped ever reaches your code.
     */
    @Query("""
           select new com.contractguard.dto.RiskCount(c.document.id, c.riskLevel, count(c))
           from Clause c
           where c.document.user.id = :userId
           group by c.document.id, c.riskLevel
           """)
    List<RiskCount> countClausesByRiskForUser(@Param("userId") Long userId);
}
