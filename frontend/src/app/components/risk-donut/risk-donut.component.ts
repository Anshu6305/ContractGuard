import { Component, input, computed } from '@angular/core';

/**
 * Risk split as an SVG donut. No charting library: a donut is four circles and
 * some arithmetic, which is cheaper than a 200KB dependency.
 *
 * The trick: draw each segment as a full circle, then use stroke-dasharray to
 * show only part of its outline and stroke-dashoffset to rotate it into place.
 * dasharray "80 300" means "draw 80 units, skip 300" -- so an 80-unit arc.
 */
@Component({
  selector: 'app-risk-donut',
  standalone: true,
  template: `
    <div class="donut-wrap">
      <svg viewBox="0 0 140 140" class="donut" role="img"
           [attr.aria-label]="ariaLabel()">
        <!-- track -->
        <circle cx="70" cy="70" [attr.r]="radius" fill="none"
                stroke="var(--track)" [attr.stroke-width]="thickness" />

        @for (seg of segments(); track seg.label) {
          <circle
            cx="70" cy="70" [attr.r]="radius" fill="none"
            [attr.stroke]="seg.colour"
            [attr.stroke-width]="thickness"
            [attr.stroke-dasharray]="seg.dash"
            [attr.stroke-dashoffset]="seg.offset"
            stroke-linecap="butt"
            transform="rotate(-90 70 70)"
            class="donut-seg" />
        }

        <text x="70" y="66" class="donut-total">{{ total() }}</text>
        <text x="70" y="84" class="donut-label">clauses</text>
      </svg>

      <ul class="legend">
        <li><span class="dot dot-risky"></span>Risky<b>{{ risky() }}</b></li>
        <li><span class="dot dot-moderate"></span>Moderate<b>{{ moderate() }}</b></li>
        <li><span class="dot dot-safe"></span>Safe<b>{{ safe() }}</b></li>
        @if (unknown() > 0) {
          <li><span class="dot dot-unknown"></span>Unassessed<b>{{ unknown() }}</b></li>
        }
      </ul>
    </div>
  `,
})
export class RiskDonutComponent {
  // Signal inputs (Angular 17.1+). Same idea as @Input(), but the value is a
  // signal, so computed() below recalculates automatically when it changes.
  readonly safe = input.required<number>();
  readonly moderate = input.required<number>();
  readonly risky = input.required<number>();
  readonly unknown = input<number>(0);

  protected readonly radius = 58;
  protected readonly thickness = 16;
  private readonly circumference = 2 * Math.PI * 58;

  protected readonly total = computed(
    () => this.safe() + this.moderate() + this.risky() + this.unknown()
  );

  protected readonly ariaLabel = computed(
    () => `${this.risky()} risky, ${this.moderate()} moderate, ${this.safe()} safe clauses`
  );

  protected readonly segments = computed(() => {
    const total = this.total();
    if (total === 0) {
      return [];
    }

    const parts = [
      { label: 'risky', value: this.risky(), colour: 'var(--risky)' },
      { label: 'moderate', value: this.moderate(), colour: 'var(--moderate)' },
      { label: 'safe', value: this.safe(), colour: 'var(--safe)' },
      { label: 'unknown', value: this.unknown(), colour: 'var(--muted-2)' },
    ].filter((p) => p.value > 0);

    let cumulative = 0;
    return parts.map((p) => {
      const fraction = p.value / total;
      const arc = fraction * this.circumference;
      const segment = {
        label: p.label,
        colour: p.colour,
        dash: `${arc} ${this.circumference - arc}`,
        // Negative offset advances the start point around the circle.
        offset: -cumulative * this.circumference,
      };
      cumulative += fraction;
      return segment;
    });
  });
}
