export type GoldenField = 'kind' | 'totalNaira' | 'paidNaira' | 'customerName' | 'quantity' | 'item' | 'unit';
export interface GoldenScore {
  readonly valid: boolean;
  readonly correct: boolean;
  readonly fields: Readonly<Record<GoldenField, boolean | null>>;
}
export declare const HARD_FIELDS: readonly GoldenField[];
export declare const SOFT_FIELDS: readonly GoldenField[];
export declare function scoreGolden(expected: unknown, actual: unknown, validate: (data: unknown) => boolean): GoldenScore;
