import { ApiError } from "@/types/api";

export interface UiErrorState {
  message: string;
  traceId: string;
}

export function toUiError(error: unknown, fallback: string): UiErrorState {
  if (error instanceof ApiError) {
    return {
      message: error.detail.message || fallback,
      traceId: error.detail.traceId ?? ""
    };
  }

  if (error instanceof Error) {
    return {
      message: error.message || fallback,
      traceId: ""
    };
  }

  return {
    message: fallback,
    traceId: ""
  };
}
