import BlogView from "@/views/BlogView.vue";
import DashboardView from "@/views/DashboardView.vue";
import OpsClusterView from "@/views/OpsClusterView.vue";
import OpsDiagnosticsView from "@/views/OpsDiagnosticsView.vue";
import OpsPipelinesView from "@/views/OpsPipelinesView.vue";
import OpsWorkloadsView from "@/views/OpsWorkloadsView.vue";
import PostDetailView from "@/views/PostDetailView.vue";
import { createRouter, createWebHistory } from "vue-router";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", name: "dashboard", component: DashboardView },
    { path: "/blog", name: "blog", component: BlogView },
    { path: "/blog/posts/:postId", name: "post-detail", component: PostDetailView, props: true },
    { path: "/ops/cluster", name: "ops-cluster", component: OpsClusterView },
    { path: "/ops/workloads", name: "ops-workloads", component: OpsWorkloadsView },
    { path: "/ops/pipelines", name: "ops-pipelines", component: OpsPipelinesView },
    { path: "/ops/diagnostics", name: "ops-diagnostics", component: OpsDiagnosticsView },
    { path: "/:pathMatch(.*)*", redirect: "/" }
  ]
});
