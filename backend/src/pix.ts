export function qrCodeSource(value: unknown): string | null {
  const candidate=String(value??"").trim();if(!candidate)return null;
  if(candidate.startsWith("data:image/")||candidate.startsWith("https://")||candidate.startsWith("http://"))return candidate;
  return `data:image/png;base64,${candidate}`;
}
