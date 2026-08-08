import { Component, input, computed } from '@angular/core';

/**
 * The 0-100 safety score as a circular progress ring.
 * Same stroke-dasharray technique as the donut, with a single arc.
 */
@Component({
  selector: 'app-score-ring',
  standalone: true,
  template: `
    <div class="ring-wrap">
      <svg viewBox="0 0 120 120" class="ring">
        <circle cx="60" cy="60" [attr.r]="radius" fill="none"
                stroke="var(--track)" stroke-width="10" />
        <circle cx="60" cy="60" [attr.r]="radius" fill="none"
                [attr.stroke]="colour()" stroke-width="10" stroke-linecap="round"
                [attr.stroke-dasharray]="dash()"
                transform="rotate(-90 60 60)"
                class="ring-arc" />
        <text x="60" y="59" class="ring-value" [attr.fill]="colour()">{{ display() }}</text>
        <text x="60" y="77" class="ring-caption">out of 100</text>
      </svg>
      <span class="ring-verdict" [style.color]="colour()">{{ verdict() }}</span>
    </div>
  `,
})
export class ScoreRingComponent {
  readonly score = input.required<number | null>();

  protected readonly radius = 52;
  private readonly circumference = 2 * Math.PI * 52;

  protected readonly display = computed(() => this.score() ?? '--');

  protected readonly dash = computed(() => {
    const value = this.score() ?? 0;
    const arc = (Math.max(0, Math.min(100, value)) / 100) * this.circumference;
    return `${arc} ${this.circumference - arc}`;
  });

  protected readonly colour = computed(() => {
    const value = this.score();
    if (value === null) return 'var(--muted-2)';
    if (value >= 75) return 'var(--safe)';
    if (value >= 45) return 'var(--moderate)';
    return 'var(--risky)';
  });

  protected readonly verdict = computed(() => {
    const value = this.score();
    if (value === null) return 'Not scored';
    if (value >= 75) return 'Broadly fair';
    if (value >= 45) return 'Read carefully';
    return 'Significant concerns';
  });
}
