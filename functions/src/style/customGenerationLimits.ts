/**
 * Custom-style generation limits + usage accounting (BLO-159 S6).
 *
 * Firestore layout:
 * - Day/month generation counters:
 *   users/{uid}/customGenerationLimits/{yyyy-MM-dd} { count }
 *   users/{uid}/customGenerationLimits/{yyyy-MM}     { count }
 *   (UTC date keys.) Before any model work the handler atomically reserves
 *   one generation against both counters; over either cap the call fails
 *   with resource-exhausted and zero model calls are made.
 * - Normalized usage: users/{uid}/usage/{yyyy-MM} { normalizedTokensUsed },
 *   incremented (merge) after the flow — success or model-failure — by the
 *   summed normalized tokens of every attempt.
 *
 * The store interface is the injected-deps seam: unit tests use a fake,
 * production uses the Admin SDK implementation below.
 */
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

export const MAX_GENERATIONS_PER_DAY = 20;
export const MAX_GENERATIONS_PER_MONTH = 100;

export const GENERATION_LIMIT_REACHED_MESSAGE = "Custom style generation limit reached.";

export function generationLimitCollectionPath(uid: string): string {
  return `users/${uid}/customGenerationLimits`;
}

export function generationUsageDocPath(uid: string, monthKey: string): string {
  return `users/${uid}/usage/${monthKey}`;
}

/** UTC `yyyy-MM-dd` + `yyyy-MM` keys for a now epoch-millis. */
export function generationDateKeys(nowEpochMillis: number): { dayKey: string; monthKey: string } {
  const iso = new Date(nowEpochMillis).toISOString();
  return { dayKey: iso.slice(0, 10), monthKey: iso.slice(0, 7) };
}

export type GenerationLimitStore = {
  /**
   * Atomically reserve one generation against the day + month counters.
   * Throws resource-exhausted when either cap would be exceeded (no
   * reservation recorded). Returns the post-reservation counts.
   */
  reserveGeneration(uid: string, dayKey: string, monthKey: string): Promise<{
    dayUsed: number;
    monthUsed: number;
  }>;
  /** Add normalized tokens to the month usage doc (merge write). */
  addNormalizedTokens(uid: string, monthKey: string, tokens: number): Promise<void>;
};

function countOf(data: Record<string, unknown> | undefined): number {
  return typeof data?.count === "number" && Number.isFinite(data.count) ? data.count : 0;
}

export function adminGenerationLimitStore(): GenerationLimitStore {
  const db = getFirestore();
  return {
    async reserveGeneration(uid, dayKey, monthKey) {
      const collection = generationLimitCollectionPath(uid);
      const dayRef = db.doc(`${collection}/${dayKey}`);
      const monthRef = db.doc(`${collection}/${monthKey}`);
      return db.runTransaction(async (tx) => {
        const [daySnap, monthSnap] = await Promise.all([tx.get(dayRef), tx.get(monthRef)]);
        const day = countOf(daySnap.data() as Record<string, unknown> | undefined);
        const month = countOf(monthSnap.data() as Record<string, unknown> | undefined);
        if (day + 1 > MAX_GENERATIONS_PER_DAY || month + 1 > MAX_GENERATIONS_PER_MONTH) {
          throw new HttpsError("resource-exhausted", GENERATION_LIMIT_REACHED_MESSAGE);
        }
        tx.set(dayRef, { count: day + 1 }, { merge: true });
        tx.set(monthRef, { count: month + 1 }, { merge: true });
        return { dayUsed: day + 1, monthUsed: month + 1 };
      });
    },
    async addNormalizedTokens(uid, monthKey, tokens) {
      await db
        .doc(generationUsageDocPath(uid, monthKey))
        .set({ normalizedTokensUsed: FieldValue.increment(tokens) }, { merge: true });
    },
  };
}
