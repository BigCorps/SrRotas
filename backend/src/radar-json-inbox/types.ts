/** Metadata only; original bytes never enter receipt or list responses. */
export type UploadReceipt = {
  upload_id: string;
  original_name: string;
  size_bytes: number;
  sha256: string;
  received_at: string;
  retention_review_at: string;
  status: "quarantined" | "purged";
};
