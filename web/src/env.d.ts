/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_GATEWAY_API_BASE_URL?: string;
  readonly VITE_BLOG_API_BASE_URL?: string;
  readonly VITE_OPS_API_BASE_URL?: string;
  readonly VITE_GRAFANA_DASHBOARD_URL?: string;
  readonly VITE_OPS_USE_MOCK?: string;
  readonly VITE_OPS_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
