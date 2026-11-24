/** Reads text aloud with the browser's speech synthesis (F8 read-back). Returns false when unsupported. */
export function speak(text: string): boolean {
  if (typeof speechSynthesis === 'undefined') return false;
  speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = 'en-NG';
  utterance.rate = 0.95;
  speechSynthesis.speak(utterance);
  return true;
}
