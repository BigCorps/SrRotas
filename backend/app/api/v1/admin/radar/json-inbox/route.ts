import { upload, list } from "@/src/radar-json-inbox/service";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const POST = upload;
export const GET = list;
