export interface AppMeta {
  appKey: string;
  title: string;
  route: string;
  status: string;
  description: string;
  sortOrder: number;
}

export interface AppHealth {
  appKey: string;
  status: string;
  rawStatus: string;
  message: string;
  checkedAt: string;
}

export interface AccessModeResponse {
  mode: string;
}
