function normalizeBaseUrl(value: string | undefined, fallback: string): string {
  const trimmed = value?.trim();
  return (trimmed && trimmed.length > 0 ? trimmed : fallback).replace(/\/+$/, "");
}

function browserOriginFallback(fallback: string): string {
  if (typeof window === "undefined") {
    return fallback;
  }

  return window.location.origin;
}

export const appConfig = {
  gatewayBaseUrl: normalizeBaseUrl(import.meta.env.VITE_GATEWAY_API_BASE_URL, "http://localhost:8080"),
  blogBaseUrl: normalizeBaseUrl(import.meta.env.VITE_BLOG_API_BASE_URL, browserOriginFallback("http://localhost:8081")),
  opsBaseUrl: normalizeBaseUrl(import.meta.env.VITE_OPS_API_BASE_URL, "http://localhost:8082"),
  useMockOps: import.meta.env.VITE_OPS_USE_MOCK !== "false",
  opsKey: import.meta.env.VITE_OPS_KEY?.trim() ?? ""
};
