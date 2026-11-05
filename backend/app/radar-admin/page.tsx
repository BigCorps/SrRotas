import { permanentRedirect } from "next/navigation";

/** Retired browser console: administrative access uses the existing session realm. */
export default function RetiredRadarAdminPage() {
  permanentRedirect("/admin/radar");
}
