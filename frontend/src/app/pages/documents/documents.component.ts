import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DocumentService } from '../../services/document.service';
import { DocumentSummary } from '../../models/models';
import { UploadProgressComponent } from '../../components/upload-progress/upload-progress.component';
import { RiskBarComponent } from '../../components/risk-bar/risk-bar.component';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [RouterLink, DatePipe, UploadProgressComponent, RiskBarComponent],
  templateUrl: './documents.component.html',
})
export class DocumentsComponent implements OnInit {
  private readonly documentService = inject(DocumentService);

  readonly documents = signal<DocumentSummary[]>([]);
  readonly uploading = signal(false);
  readonly error = signal<string | null>(null);
  readonly dragging = signal(false);

  /** Portfolio-level numbers for the stats strip. */
  readonly stats = computed(() => {
    const docs = this.documents().filter((d) => d.status === 'COMPLETED');
    const risky = docs.reduce((sum, d) => sum + d.riskyCount, 0);
    const clauses = docs.reduce((sum, d) => sum + d.clauseCount, 0);
    const scored = docs.filter((d) => d.overallScore !== null);
    const avg = scored.length
      ? Math.round(scored.reduce((s, d) => s + (d.overallScore ?? 0), 0) / scored.length)
      : null;
    return { contracts: this.documents().length, clauses, risky, avg };
  });

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.documentService.list().subscribe({
      next: (docs) => this.documents.set(docs),
      error: () => this.error.set('Could not load your documents'),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.upload(file, () => (input.value = ''));
    }
  }

  // --- drag and drop ---------------------------------------------------------
  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);

    const file = event.dataTransfer?.files?.[0];
    if (!file) {
      return;
    }
    if (file.type !== 'application/pdf') {
      this.error.set('Only PDF files are accepted');
      return;
    }
    this.upload(file);
  }

  private upload(file: File, cleanup?: () => void): void {
    this.uploading.set(true);
    this.error.set(null);

    this.documentService.upload(file).subscribe({
      next: () => {
        this.uploading.set(false);
        cleanup?.();
        this.refresh();
      },
      error: (err) => {
        this.error.set(err.error?.message ?? 'Upload failed');
        this.uploading.set(false);
        cleanup?.();
      },
    });
  }

  remove(id: number, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.documentService.delete(id).subscribe({
      next: () => this.documents.update((docs) => docs.filter((d) => d.id !== id)),
      error: () => this.error.set('Could not delete that document'),
    });
  }

  scoreClass(score: number | null): string {
    if (score === null) return 'score-unknown';
    if (score >= 75) return 'score-safe';
    if (score >= 45) return 'score-moderate';
    return 'score-risky';
  }
}
