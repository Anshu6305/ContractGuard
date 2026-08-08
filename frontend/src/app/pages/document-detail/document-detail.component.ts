import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DocumentDetail, Clause, RiskLevel } from '../../models/models';
import { DocumentService } from '../../services/document.service';
import { RiskDonutComponent } from '../../components/risk-donut/risk-donut.component';
import { ScoreRingComponent } from '../../components/score-ring/score-ring.component';

@Component({
  selector: 'app-document-detail',
  standalone: true,
  imports: [RouterLink, RiskDonutComponent, ScoreRingComponent],
  templateUrl: './document-detail.component.html',
})
export class DocumentDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly documentService = inject(DocumentService);

  readonly document = signal<DocumentDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly expandedId = signal<number | null>(null);
  readonly filter = signal<RiskLevel | 'ALL'>('ALL');

  readonly counts = computed(() => {
    const clauses = this.document()?.clauses ?? [];
    return {
      risky: clauses.filter((c) => c.riskLevel === 'RISKY').length,
      moderate: clauses.filter((c) => c.riskLevel === 'MODERATE').length,
      safe: clauses.filter((c) => c.riskLevel === 'SAFE').length,
      unknown: clauses.filter((c) => c.riskLevel === 'UNKNOWN').length,
    };
  });

  readonly visibleClauses = computed(() => {
    const doc = this.document();
    if (!doc) return [];
    const active = this.filter();
    return active === 'ALL' ? doc.clauses : doc.clauses.filter((c) => c.riskLevel === active);
  });

  /** One-line verdict shown beside the charts. */
  readonly verdict = computed(() => {
    const { risky, moderate } = this.counts();
    if (risky > 0) {
      return `${risky} clause${risky === 1 ? '' : 's'} could work against you. Read those before signing.`;
    }
    if (moderate > 0) {
      return `Nothing alarming, but ${moderate} clause${moderate === 1 ? '' : 's'} tilt toward the other party.`;
    }
    return 'No one-sided clauses were found in this contract.';
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.documentService.get(id).subscribe({
      next: (doc) => this.document.set(doc),
      error: () => this.error.set('Could not load that document'),
    });
  }

  toggle(clause: Clause): void {
    this.expandedId.update((current) => (current === clause.id ? null : clause.id));
  }

  setFilter(level: RiskLevel | 'ALL'): void {
    this.filter.set(level);
  }

  riskClass(level: RiskLevel): string {
    return 'risk-' + level.toLowerCase();
  }

  // TODO: render the original PDF beside this list with ng2-pdf-viewer, using
  //       clause.startOffset / clause.endOffset to scroll to and highlight the
  //       matching span when a card is clicked. The offsets are persisted for
  //       exactly this purpose.
}
