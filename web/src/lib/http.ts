import { appConfig } from "@/lib/config";
import type { ApiResponse } from "@/types/api";
import { ApiError } from "@/types/api";

interface RequestOptions extends RequestInit {
  query?: Record<string, string | number | undefined>;
}

function buildUrl(baseUrl: string, path: string, query?: RequestOptions["query"]): string {
  const url = new URL(path, `${baseUrl}/`);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value === undefined || value === "") {
      return;
    }

    url.searchParams.set(key, String(value));
  });
  return url.toString();
}

export async function requestApi<T>(baseUrl: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers ?? {});
  headers.set("Accept", "application/json");

  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (appConfig.opsKey) {
    headers.set("X-Ops-Key", appConfig.opsKey);
  }

  const response = await fetch(buildUrl(baseUrl, path, options.query), {
    ...options,
    headers
  });

  let payload: ApiResponse<T> | undefined;
  try {
    payload = (await response.json()) as ApiResponse<T>;
  } catch {
    throw new ApiError({
      status: response.status,
      code: "BAD_PAYLOAD",
      message: "后端未返回可解析的 JSON 响应"
    });
  }

  if (!response.ok || payload.code !== "OK") {
    throw new ApiError({
      status: response.status,
      code: payload.code,
      message: payload.message,
      traceId: payload.traceId
    });
  }

  return payload.data;
}
