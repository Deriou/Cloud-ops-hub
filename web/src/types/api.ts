export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  traceId: string;
}

export interface ApiErrorDetail {
  status: number;
  code: string;
  message: string;
  traceId?: string;
}

export class ApiError extends Error {
  readonly detail: ApiErrorDetail;

  constructor(detail: ApiErrorDetail) {
    super(detail.message);
    this.name = "ApiError";
    this.detail = detail;
  }
}

export interface PagedResponse<T> {
  page: number;
  size: number;
  total: number;
  totalPages: number;
  records: T[];
}
