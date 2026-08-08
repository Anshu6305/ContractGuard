package com.contractguard.repository;

import com.contractguard.entity.Clause;
import com.contractguard.entity.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClauseRepository extends JpaRepository<Clause, Long> {

    List<Clause> findByDocumentIdOrderByOrderIndexAsc(Long documentId);

    long countByDocumentIdAndRiskLevel(Long documentId, RiskLevel riskLevel);
}
