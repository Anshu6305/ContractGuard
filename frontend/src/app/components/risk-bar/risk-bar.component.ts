import { Component, input, computed } from '@angular/core';

/**
 * Compact stacked bar showing a document's risk mix. Pure CSS flex -- each
 * segment's width is its share of the total, so the bar is a distribution at a
 * glance without needing a chart library.
 */
@Component({
  selector: 'app-risk-bar',
  standalone: true,
  template: `
    <div class="risk-bar" [attr.aria-label]="label()" role="img">
      @for (seg of segments(); track seg.cls) {
        <span [class]="seg.cls" [style.flex]="seg.share"></span>
      }
    </div>
  `,
})
export class RiskBarComponent {
  readonly risky = input.required<number>();
  readonly moderate = input.required<number>();
  readonly safe = input.required<number>();
  readonly unknown = input<number>(0);

  private readonly total = computed(
    () => this.risky() + this.moderate() + this.safe() + this.unknown()
  );

  protected readonly label = computed(
    () => `${this.risky()} risky, ${this.moderate()} moderate, ${this.safe()} safe`
  );

  protected readonly segments = computed(() => {
    if (this.total() === 0) {
      return [{ cls: 'seg seg-empty', share: 1 }];
    }
    return [
      { cls: 'seg seg-risky', share: this.risky() },
      { cls: 'seg seg-moderate', share: this.moderate() },
      { cls: 'seg seg-safe', share: this.safe() },
      { cls: 'seg seg-unknown', share: this.unknown() },
    ].filter((s) => s.share > 0);
  });
}
