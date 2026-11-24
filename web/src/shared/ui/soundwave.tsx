import { useEffect, useRef } from 'react';

const BARS = 44;
const MIN_SCALE = 0.08;
const IDLE_SHAPE = Array.from({ length: BARS }, (_, i) => 0.18 + 0.5 * Math.abs(Math.sin(i * 0.55)) * Math.abs(Math.cos(i * 0.21)));

export interface SoundwaveProps {
  /** The live microphone stream. Without one, the wave shows a still, quiet shape. */
  readonly stream: MediaStream | null;
  /** Freeze the wave (paused or finished). */
  readonly active: boolean;
  readonly className?: string;
}

function reducedMotion(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** Root-mean-square loudness of one frame of time-domain samples, scaled to 0..1. */
export function loudness(samples: Uint8Array): number {
  let sum = 0;
  for (const value of samples) {
    const centred = (value - 128) / 128;
    sum += centred * centred;
  }
  return Math.min(1, Math.sqrt(sum / Math.max(1, samples.length)) * 4);
}

function paint(bars: readonly HTMLSpanElement[], history: readonly number[]): void {
  bars.forEach((bar, i) => {
    const value = history[i] ?? MIN_SCALE;
    bar.style.transform = `scaleY(${Math.max(MIN_SCALE, value).toFixed(3)})`;
  });
}

/** Reads the microphone level each frame and scrolls it through the bars (transform only, no React renders). */
function useLiveBars(input: { stream: MediaStream | null; active: boolean; bars: { current: HTMLSpanElement[] } }) {
  const { stream, active, bars } = input;
  useEffect(() => {
    if (stream === null || !active || reducedMotion() || typeof AudioContext === 'undefined') return undefined;
    const context = new AudioContext();
    const analyser = context.createAnalyser();
    analyser.fftSize = 512;
    context.createMediaStreamSource(stream).connect(analyser);
    const samples = new Uint8Array(analyser.fftSize);
    const history = Array.from({ length: BARS }, () => MIN_SCALE);
    let frame = 0;
    let tick = 0;
    const loop = () => {
      tick += 1;
      if (tick % 3 === 0) {
        analyser.getByteTimeDomainData(samples);
        history.shift();
        history.push(loudness(samples));
        paint(bars.current, history);
      }
      frame = requestAnimationFrame(loop);
    };
    frame = requestAnimationFrame(loop);
    return () => {
      cancelAnimationFrame(frame);
      void context.close();
    };
  }, [stream, active, bars]);
}

/**
 * A recording sound wave: thin rounded bars that rise with the speaker's voice and scroll from right
 * to left. Decorative (aria-hidden); the timer and status text carry the meaning.
 */
export function Soundwave({ stream, active, className = '' }: SoundwaveProps) {
  const bars = useRef<HTMLSpanElement[]>([]);
  useLiveBars({ stream, active, bars });
  return (
    <div aria-hidden="true" className={`flex h-24 items-center justify-center gap-[3px] ${className}`}>
      {IDLE_SHAPE.map((height, i) => (
        <span
          key={i}
          ref={(el) => {
            if (el !== null) bars.current[i] = el;
          }}
          className={`h-full w-[3px] origin-center rounded-full transition-transform duration-100 ease-out ${i >= BARS - 6 ? 'bg-text' : 'bg-text/80'}`}
          style={{ transform: `scaleY(${height.toFixed(3)})` }}
        />
      ))}
    </div>
  );
}
