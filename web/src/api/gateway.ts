import { appConfig } from "@/lib/config";
import { requestApi } from "@/lib/http";
import type { AccessModeResponse, AppHealth, AppMeta } from "@/types/gateway";

export function fetchAppRegistry(): Promise<AppMeta[]> {
  return requestApi<AppMeta[]>(appConfig.gatewayBaseUrl, "/api/v1/gateway/apps");
}

export function fetchHealthSummary(): Promise<AppHealth[]> {
  return requestApi<AppHealth[]>(appConfig.gatewayBaseUrl, "/api/v1/gateway/apps/health");
}

export function fetchAccessMode(): Promise<AccessModeResponse> {
  return requestApi<AccessModeResponse>(appConfig.gatewayBaseUrl, "/api/v1/gateway/access-mode");
}
