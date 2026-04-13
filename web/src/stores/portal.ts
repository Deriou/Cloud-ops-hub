import { fetchAccessMode, fetchAppRegistry, fetchHealthSummary } from "@/api/gateway";
import { toUiError } from "@/lib/errors";
import type { AppHealth, AppMeta } from "@/types/gateway";
import { defineStore } from "pinia";

let pendingHydration: Promise<void> | null = null;

interface PortalState {
  accessMode: string;
  registry: AppMeta[];
  healthList: AppHealth[];
  isLoading: boolean;
  errorMessage: string;
  errorTraceId: string;
  hydrated: boolean;
}

export const usePortalStore = defineStore("portal", {
  state: (): PortalState => ({
    accessMode: "unknown",
    registry: [],
    healthList: [],
    isLoading: false,
    errorMessage: "",
    errorTraceId: "",
    hydrated: false
  }),
  getters: {
    healthMap(state) {
      return new Map(state.healthList.map((item) => [item.appKey, item]));
    },
    sortedRegistry(state) {
      return [...state.registry].sort((left, right) => left.sortOrder - right.sortOrder || left.title.localeCompare(right.title));
    },
    healthSummary(state) {
      const total = state.healthList.length;
      const healthy = state.healthList.filter((item) => item.status === "UP").length;
      const degraded = state.healthList.filter((item) => item.status === "DEGRADED").length;
      const down = state.healthList.filter((item) => item.status === "DOWN").length;

      return {
        total,
        healthy,
        degraded,
        down,
        label: total > 0 ? `${healthy}/${total} online` : "Health syncing",
        headline: down > 0 ? "Degraded" : "Stable",
        tone: down > 0 ? "danger" : degraded > 0 ? "warning" : "normal"
      } as const;
    }
  },
  actions: {
    async hydrate(force = false) {
      if (pendingHydration && !force) {
        return pendingHydration;
      }

      if (this.hydrated && !force) {
        return;
      }

      pendingHydration = (async () => {
        this.isLoading = true;
        this.errorMessage = "";
        this.errorTraceId = "";

        try {
          const [accessMode, registry, healthList] = await Promise.all([
            fetchAccessMode(),
            fetchAppRegistry(),
            fetchHealthSummary()
          ]);

          this.accessMode = accessMode.mode;
          this.registry = registry;
          this.healthList = healthList;
          this.hydrated = true;
        } catch (error) {
          const uiError = toUiError(error, "读取 Gateway 数据失败");
          this.errorMessage = uiError.message;
          this.errorTraceId = uiError.traceId;
        } finally {
          this.isLoading = false;
        }
      })();

      try {
        await pendingHydration;
      } finally {
        pendingHydration = null;
      }
    },
    async ensureHydrated() {
      if (this.hydrated) {
        return;
      }

      await this.hydrate();
    },
    reset() {
      this.isLoading = true;
      this.errorMessage = "";
      this.errorTraceId = "";
      this.accessMode = "unknown";
      this.registry = [];
      this.healthList = [];
      this.hydrated = false;
      this.isLoading = false;
    }
  }
});
