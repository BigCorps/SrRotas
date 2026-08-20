import { authenticateDevice } from "@/src/device-auth";
import {
  getCostProfile,
  saveCostProfile,
} from "@/src/costs";

export const runtime = "nodejs";

export async function GET(
  request: Request,
) {
  const auth =
    await authenticateDevice(
      request,
    );

  if (!auth) {
    return Response.json(
      {
        error:
          "unauthorized",
      },
      {
        status: 401,
      },
    );
  }

  try {
    return Response.json(
      await getCostProfile(
        auth.driverId,
      ),
    );
  } catch (error) {
    return Response.json(
      {
        error:
          error instanceof Error
            ? error.message
            : "cost_profile_failed",
      },
      {
        status: 500,
      },
    );
  }
}

export async function PUT(
  request: Request,
) {
  const auth =
    await authenticateDevice(
      request,
    );

  if (!auth) {
    return Response.json(
      {
        error:
          "unauthorized",
      },
      {
        status: 401,
      },
    );
  }

  const body =
    await request
      .json()
      .catch(
        () => null,
      );

  if (
    !body ||
    typeof body !==
      "object"
  ) {
    return Response.json(
      {
        error:
          "invalid_json",
      },
      {
        status: 400,
      },
    );
  }

  try {
    return Response.json(
      await saveCostProfile(
        auth.driverId,
        body as
          Record<
            string,
            unknown
          >,
      ),
    );
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : "cost_profile_failed";

    return Response.json(
      {
        error: message,
      },
      {
        status:
          message ===
          "cost_profile_without_usable_cost"
            ? 400
            : 500,
      },
    );
  }
}
