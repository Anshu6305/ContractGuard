// Mirrors the backend DTOs. Keeping these in sync by hand is fine at this size;
// larger projects generate them from an OpenAPI spec.

export type RiskLevel = 'SAFE' | 'MODERATE' | 'RISKY' | 'UNKNOWN';
export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface AuthResponse {
  token: string;
  email: string;
  fullName: string;
}

export interface Clause {
  id: number;
  orderIndex: number;
  heading: string | null;
  originalText: string;
  startOffset: number | null;
  endOffset: number | null;
  riskLevel: RiskLevel;
  plainSummary: string | null;
  rationale: string | null;
}

export interface DocumentSummary {
  id: number;
  filename: string;
  status: DocumentStatus;
  overallScore: number | null;
  uploadedAt: string;
  clauseCount: number;
  riskyCount: number;
  moderateCount: number;
  safeCount: number;
}

export interface DocumentDetail {
  id: number;
  filename: string;
  status: DocumentStatus;
  overallScore: number | null;
  extractedText: string | null;
  clauses: Clause[];
  failureReason: string | null;
}
