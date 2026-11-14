/** File transport checks only. Deliberately independent of the unavailable Radar v1.3 schema. */
export const RADAR_LOCAL_FILE_LIMIT = 2_000_000;
export type JsonInspection = { status: "syntax_valid" | "invalid_json" | "invalid_encoding" | "too_large"; message: string };
export function inspectRadarJsonFile(bytes: Uint8Array): JsonInspection {
  if (bytes.byteLength > RADAR_LOCAL_FILE_LIMIT)
    return { status: "too_large", message: "O arquivo ultrapassa o limite local de 2 MB." };
  let text: string;
  try { text = new TextDecoder("utf-8", { fatal: true }).decode(bytes); }
  catch { return { status: "invalid_encoding", message: "O arquivo precisa estar codificado em UTF-8." }; }
  try { JSON.parse(text); }
  catch { return { status: "invalid_json", message: "Sintaxe JSON inválida. Confira aspas, vírgulas e chaves no arquivo." }; }
  return { status: "syntax_valid", message: "Sintaxe JSON válida. O formato e os registros ainda não foram validados." };
}
