import { appConfig } from "@/lib/config";
import { requestApi } from "@/lib/http";
import { getClusterSummary, getDiagnosisReports, getPipelineRuns, getWorkloads } from "@/mock/ops";
import type { ClusterSummary, DiagnosisReport, PipelineRun, WorkloadPage } from "@/types/ops";

function shouldUseMockOps(): boolean {
  return appConfig.useMockOps;
}

export function fetchClusterSummary(): Promise<ClusterSummary> {
  if (shouldUseMockOps()) {
    return getClusterSummary();
  }

  return requestApi<ClusterSummary>(appConfig.opsBaseUrl, "/api/v1/ops/clusters/summary");
}

export function fetchWorkloads(pageNo = 1, pageSize = 8): Promise<WorkloadPage> {
  if (shouldUseMockOps()) {
    return getWorkloads(pageNo, pageSize);
  }

  return requestApi<WorkloadPage>(appConfig.opsBaseUrl, "/api/v1/ops/workloads", {
    query: { pageNo, pageSize }
  });
}

export function fetchPipelineRuns(): Promise<PipelineRun[]> {
  if (shouldUseMockOps()) {
    return getPipelineRuns();
  }

  return requestApi<PipelineRun[]>(appConfig.opsBaseUrl, "/api/v1/ops/pipelines/runs");
}

export function fetchDiagnosisReports(): Promise<DiagnosisReport[]> {
  if (shouldUseMockOps()) {
    return getDiagnosisReports();
  }

  return requestApi<DiagnosisReport[]>(appConfig.opsBaseUrl, "/api/v1/ops/diagnostics/reports");
}
