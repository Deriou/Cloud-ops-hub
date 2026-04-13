import type { ClusterSummary, DiagnosisReport, PipelineRun, WorkloadItem, WorkloadPage } from "@/types/ops";

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

export async function getClusterSummary(): Promise<ClusterSummary> {
  await wait(150);
  return {
    clusterName: "k3s-single-node",
    region: "cn-hangzhou",
    checkedAt: new Date().toISOString(),
    stats: [
      { label: "CPU 利用率", value: "42%", trend: [21, 28, 33, 29, 38, 42], tone: "normal" },
      { label: "内存占用", value: "5.2GB / 8GB", trend: [48, 51, 58, 57, 61, 65], tone: "warning" },
      { label: "错误率", value: "0.7%", trend: [0.2, 0.3, 0.4, 0.6, 0.5, 0.7], tone: "normal" }
    ]
  };
}

const workloadItems: WorkloadItem[] = [
  { namespace: "apps", name: "gateway-portal", kind: "Deployment", status: "HEALTHY", pods: "1/1", owner: "platform" },
  { namespace: "apps", name: "blog-service", kind: "Deployment", status: "HEALTHY", pods: "1/1", owner: "content" },
  { namespace: "ops", name: "jenkins", kind: "StatefulSet", status: "DEGRADED", pods: "1/1", owner: "delivery" },
  { namespace: "obs", name: "loki", kind: "StatefulSet", status: "UNHEALTHY", pods: "0/1", owner: "observability" },
  { namespace: "obs", name: "prometheus", kind: "StatefulSet", status: "HEALTHY", pods: "1/1", owner: "observability" },
  { namespace: "apps", name: "portal-ui", kind: "Deployment", status: "HEALTHY", pods: "1/1", owner: "frontend" }
];

export async function getWorkloads(pageNo = 1, pageSize = 8): Promise<WorkloadPage> {
  await wait(150);
  const start = (pageNo - 1) * pageSize;
  const records = workloadItems.slice(start, start + pageSize);

  return {
    page: pageNo,
    size: pageSize,
    total: workloadItems.length,
    totalPages: Math.max(1, Math.ceil(workloadItems.length / pageSize)),
    records
  };
}

export async function getPipelineRuns(): Promise<PipelineRun[]> {
  await wait(150);
  return [
    {
      runId: "run-1042",
      jobName: "gateway-release",
      status: "SUCCESS",
      triggerBy: "jenkins-bot",
      startedAt: "2026-03-28T07:00:00Z",
      duration: "1m 54s",
      stages: [
        { name: "checkout", status: "SUCCESS", duration: "18s" },
        { name: "build", status: "SUCCESS", duration: "47s" },
        { name: "deploy", status: "SUCCESS", duration: "49s" }
      ]
    },
    {
      runId: "run-1043",
      jobName: "blog-release",
      status: "RUNNING",
      triggerBy: "deriou",
      startedAt: "2026-03-28T07:12:00Z",
      duration: "36s",
      stages: [
        { name: "checkout", status: "SUCCESS", duration: "11s" },
        { name: "build", status: "RUNNING", duration: "25s" },
        { name: "deploy", status: "PENDING" }
      ]
    },
    {
      runId: "run-1044",
      jobName: "plg-sync",
      status: "TIMEOUT",
      triggerBy: "jenkins-bot",
      startedAt: "2026-03-28T06:40:00Z",
      duration: "5m 00s",
      stages: [
        { name: "checkout", status: "SUCCESS", duration: "16s" },
        { name: "build", status: "SUCCESS", duration: "1m 22s" },
        { name: "deploy", status: "TIMEOUT", duration: "3m 22s" }
      ]
    }
  ];
}

export async function getDiagnosisReports(): Promise<DiagnosisReport[]> {
  await wait(150);
  return [
    {
      reportId: "diag-7751",
      severity: "HIGH",
      summary: "发布后 Loki 实例持续重启，日志采集链路中断。",
      rootCause: "Loki 存储卷未成功挂载，导致进程启动后写 WAL 失败。",
      suggestions: ["检查 PVC 绑定状态", "确认宿主机磁盘权限", "回滚到上一个稳定镜像"],
      metrics: [
        { label: "内存峰值", value: "82%" },
        { label: "容器重启次数", value: "7" }
      ],
      logs: ["level=error msg=\"mkdir /var/loki/wal: permission denied\"", "level=warn msg=\"shipper not ready\""]
    },
    {
      reportId: "diag-7752",
      severity: "MEDIUM",
      summary: "Jenkins 任务触发耗时升高，但集群整体仍可用。",
      rootCause: "Agent Pod 拉取基础镜像耗时偏高。",
      suggestions: ["为常用镜像开启预热", "缩短镜像层体积", "检查 Harbor 或 Docker Hub 拉取速率"],
      metrics: [
        { label: "触发耗时 P95", value: "18s" },
        { label: "重试次数", value: "1" }
      ],
      logs: ["Waiting for agent pod", "Image pull completed after 12s"]
    }
  ];
}
