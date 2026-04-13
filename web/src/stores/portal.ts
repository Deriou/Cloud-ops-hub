import { fetchAccessMode, fetchAppRegistry, fetchHealthSummary } from "@/api/gateway";
import { toUiError } from "@/lib/errors";
import type { AppHealth, AppMeta } from "@/types/gateway";
import { defineStore } from "pinia";

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
    }
  },
  actions: {
    async hydrate() {
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
    }
  }
});
