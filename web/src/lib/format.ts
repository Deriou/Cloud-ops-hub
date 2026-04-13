export function formatDateTime(value: string): string {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

export function formatFullDateTime(value: string): string {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

export function toneForStatus(status: string): "normal" | "warning" | "danger" {
  switch (status.toUpperCase()) {
    case "UP":
    case "HEALTHY":
    case "SUCCESS":
    case "LOW":
      return "normal";
    case "DEGRADED":
    case "PENDING":
    case "RUNNING":
    case "MEDIUM":
      return "warning";
    default:
      return "danger";
  }
}

export function initials(value: string): string {
  return value
    .split(/[\s-]+/)
    .filter(Boolean)
    .map((item) => item[0]?.toUpperCase())
    .join("")
    .slice(0, 2);
}

export function clampTrend(values: number[]): number[] {
  return values.length > 0 ? values : [0, 0, 0, 0, 0, 0];
}
