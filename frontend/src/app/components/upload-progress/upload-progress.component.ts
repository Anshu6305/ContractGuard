import { Component, OnDestroy, OnInit, signal, computed } from '@angular/core';

/**
 * Feedback while a document is analysed.
 *
 * Deliberately NOT a percentage bar. The backend runs synchronously and reports
 * nothing until it finishes, so any percentage would be invented. Showing a fake
 * 73% is worse than showing none -- it teaches the user not to trust the UI.
 *
 * Instead: the real elapsed time, plus the stage the pipeline is most likely in.
 * The stage timings are estimates and the copy says so.
 *
 * TODO: once DocumentService.analyzeAsync is wired up and this polls
 *       GET /api/documents/{id}, replace the estimates with real status and
 *       clause counts from the server. A percentage becomes honest at that
 *       point, and not before.
 */
@Component({
  selector: 'app-upload-progress',
  standalone: true,
  template: `
    <div class="progress-card">
      <div class="spinner" aria-hidden="true"></div>

      <div class="progress-body">
        <strong>{{ stage() }}</strong>
        <p class="muted">
          Each clause is analysed separately, so a full contract takes about a minute.
        </p>

        <ol class="stages">
          @for (s of stages; track s.label; let i = $index) {
            <li [class.done]="i < stageIndex()" [class.active]="i === stageIndex()">
              {{ s.label }}
            </li>
          }
        </ol>
      </div>

      <span class="elapsed">{{ elapsed() }}s</span>
    </div>
  `,
})
export class UploadProgressComponent implements OnInit, OnDestroy {
  protected readonly stages = [
    { label: 'Uploading file', until: 3 },
    { label: 'Extracting text', until: 7 },
    { label: 'Splitting into clauses', until: 11 },
    { label: 'Analysing each clause', until: Number.MAX_SAFE_INTEGER },
  ];

  protected readonly elapsed = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  protected readonly stageIndex = computed(() => {
    const seconds = this.elapsed();
    const found = this.stages.findIndex((s) => seconds < s.until);
    return found === -1 ? this.stages.length - 1 : found;
  });

  protected readonly stage = computed(() => this.stages[this.stageIndex()].label);

  ngOnInit(): void {
    this.timer = setInterval(() => this.elapsed.update((n) => n + 1), 1000);
  }

  ngOnDestroy(): void {
    // A timer that outlives its component keeps firing against a destroyed
    // view - a memory leak. Clearing it here is what ngOnDestroy is for.
    clearInterval(this.timer);
  }
}
