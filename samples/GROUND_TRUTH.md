# Ground truth for the sample contracts

Hand-labelled expected classification for the sample contracts. Every prompt
change is re-measured against this rather than eyeballed.

Measure with SQL rather than counting cards in the browser:

```sql
USE contractguard;
SELECT d.original_filename, c.order_index, c.heading, c.risk_level
FROM clauses c JOIN documents d ON d.id = c.document_id
ORDER BY d.id, c.order_index;
```

---

## sample-rental-agreement.pdf — deliberately predatory

Expected: **7 RISKY / 0 MODERATE / 5 SAFE**

| # | Clause | Expected | Why |
|---|---|---|---|
| 1 | Term and Duration | SAFE | Fixed term, mutual renewal |
| 2 | Rent and Payment | RISKY | Landlord may revise at any time, sole discretion |
| 3 | Security Deposit | RISKY | 10 months rent; landlord's determination final |
| 4 | Termination | RISKY | Landlord: no notice, no reason. Tenant: 6 months + forfeiture |
| 5 | Maintenance | RISKY | Tenant pays all repairs including pre-existing defects |
| 6 | Entry and Inspection | RISKY | Any hour, no notice, tenant may not object |
| 7 | Use of Premises | SAFE | Standard |
| 8 | Subletting | SAFE | Consent not unreasonably withheld |
| 9 | Indemnity | RISKY | Unlimited, regardless of fault, survives indefinitely |
| 10 | Alterations | SAFE | Reasonable and bounded |
| 11 | Governing Law | RISKY | Landlord appoints the arbitrator; tenant waives court access |
| 12 | Notices | SAFE | Boilerplate |

**This file contains no MODERATE clauses.** Zero moderate output here is correct,
not a bug. Use the employment contract to test that tier.

---

## sample-employment-contract.pdf — deliberately mixed

Expected: **1 RISKY / 6 MODERATE / 5 SAFE**

| # | Clause | Expected | Why |
|---|---|---|---|
| 1 | Position and Duties | SAFE | Variation must be reasonable and skill-appropriate |
| 2 | Remuneration | SAFE | Clear amount and payment date |
| 3 | Probation Period | MODERATE | 6 months, extendable to 9 — long but bounded |
| 4 | Working Hours | MODERATE | Unpaid extra hours, but qualified by "reasonably required" |
| 5 | Notice Period | MODERATE | 90 days out vs 30 days in — asymmetric, both bounded |
| 6 | Confidentiality | SAFE | 3 years, with a public-domain carve-out |
| 7 | Non-Competition | MODERATE | 12 months, one city — narrow enough to be ordinary in kind |
| 8 | Intellectual Property | SAFE | Limited to work made using company resources |
| 9 | Training Bond | MODERATE | Pro-rata, documented cost, capped — common in India |
| 10 | Leave | MODERATE | Lapsing leave tilts toward the employer but is customary |
| 11 | Termination for Cause | SAFE | Defined causes, enquiry with a right to be heard |
| 12 | Assignment of Agreement | RISKY | Obligations transferred to any entity, no consent, no notice, right to object waived |

The MODERATE clauses are the point of this file: each is unfair in **degree**
but ordinary in **kind**. Marking them RISKY is over-flagging; marking them SAFE
is under-warning. A classifier that only ever reaches for the extremes cannot be
detected without a document like this one.

---

## Results log

| Run | Prompt version | Rental (R/M/S) | Employment (R/M/S) | Correct / 24 |
|---|---|---|---|---|
| 1 | v1 baseline | 7 / 0 / 5 | — | — |
| 2 | v2 tests + calibration | | | |
| 3 | | | | |
