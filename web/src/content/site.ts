export interface SiteNavigationItem {
  label: string;
  to: string;
  match: (path: string) => boolean;
}

export interface FeaturedProject {
  name: string;
  description: string;
  summary: string;
  href: string;
}

export interface CuratedAppDefinition {
  title: string;
  description: string;
  summary: string;
  fallbackRoute: string;
  matchKeys: string[];
}

export interface OpsEntryDefinition {
  title: string;
  description: string;
}

export const siteMeta = {
  title: "Deriou的个人编程笔记",
  githubUrl: "https://github.com/Deriou",
  githubUsername: "Deriou",
  email: "guqiang982@gmail.com",
  icpLabel: "冀ICP备2026010164号"
} as const;

export const siteNavigation: SiteNavigationItem[] = [
  { label: "Home", to: "/", match: (path) => path === "/" },
  { label: "Blog", to: "/blog", match: (path) => path.startsWith("/blog") },
  { label: "Projects", to: "/projects", match: (path) => path.startsWith("/projects") },
  { label: "Apps", to: "/apps", match: (path) => path.startsWith("/apps") },
  { label: "Ops", to: "/ops/cluster", match: (path) => path.startsWith("/ops") }
];

export const profile = {
  name: "Deriou",
  tagline: "写代码、记笔记、在云上折腾",
  intro: "关注云原生、运维自动化与轻量工程实践，持续整理自己的博客、项目与系统入口。",
  avatarUrl: "/photo.jpg",
  techStack: ["Kubernetes", "Go", "Java", "Vue", "DevOps"]
} as const;

export const featuredProjects: FeaturedProject[] = [
  {
    name: "Deriou/Cloud-ops-hub",
    description: "Main distribution hub",
    summary: "集中承载 gateway、blog、ops 与 web 的分发与演进。",
    href: "https://github.com/Deriou/Cloud-ops-hub"
  }
];

export const curatedApps: CuratedAppDefinition[] = [
  {
    title: "Portal",
    description: "有哪些应用、状态如何、从哪进",
    summary: "作为应用目录与聚合入口，后续在这里承接更多业务应用与敏感入口说明。",
    fallbackRoute: "/apps",
    matchKeys: ["portal", "gateway-portal", "portal-ui"]
  }
];

export const opsOverview = {
  statusLabel: "Stable",
  summary: "Online",
  description: "Gateway 与 Blog 管理能力集中在一处，健康摘要优先，后续再逐步接入鉴权。",
  entries: [
    {
      title: "Gateway",
      description: "注册表、健康检查与入口分发的统一门面。"
    },
    {
      title: "Blog Admin",
      description: "博客托管与管理能力保留在运维边界内。"
    }
  ] satisfies OpsEntryDefinition[],
  tags: ["Jenkins", "Grafana", "Auth Later"]
} as const;

export const manifesto = "The coding was tough. Nearly killed me.";
