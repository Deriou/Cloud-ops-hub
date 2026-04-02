export interface OpsStat {
  label: string;
  value: string;
  trend: number[];
  tone: "normal" | "warning" | "danger";
}

export interface ClusterSummary {
  clusterName: string;
  region: string;
  checkedAt: string;
  stats: OpsStat[];
}

export interface WorkloadItem {
  namespace: string;
  name: string;
  kind: string;
  status: "HEALTHY" | "DEGRADED" | "UNHEALTHY";
  pods: string;
  owner: string;
}

export interface PipelineRun {
  runId: string;
  jobName: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | "TIMEOUT";
  triggerBy: string;
  startedAt: string;
  duration: string;
}

export interface DiagnosisReport {
  reportId: string;
  severity: "LOW" | "MEDIUM" | "HIGH";
  summary: string;
  rootCause: string;
  suggestions: string[];
  metrics: Array<{ label: string; value: string }>;
  logs: string[];
}
