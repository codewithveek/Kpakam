const MAX_EDGE = 1280;
const QUALITY = 0.72;

/**
 * Compresses a receipt photo on the device before any upload (max 1280 px, WebP,
 * about 150 KB), to save the trader's data.
 */
export async function compressImage(file: Blob): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
  const width = Math.round(bitmap.width * scale);
  const height = Math.round(bitmap.height * scale);
  const canvas = new OffscreenCanvas(width, height);
  const context = canvas.getContext('2d');
  if (context === null) return file;
  context.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();
  return canvas.convertToBlob({ type: 'image/webp', quality: QUALITY });
}

/** Encodes a blob as base64 (no data: prefix) for an inline-data part. */
export async function blobToBase64(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  return btoa(binary);
}
