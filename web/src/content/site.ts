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
  stack?: string[];
}

export interface CuratedAppDefinition {
  title: string;
  description: string;
  summary: string;
  fallbackRoute: string;
  matchKeys: string[];
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
  tagline: "A CS student building, breaking, and learning in the cloud",
  intro: "A personal hub for notes, cloud-native experiments, DevOps projects, and tools I build along the way.",
  avatarUrl: "/photo.jpg",
  techStack: [
    "DevOps",
    "Kubernetes",
    "Linux",
    "Docker",
    "Shell",
    "Java",
    "Python",
    "JavaWeb",
    "MySQL",
    "Spring Boot",
    "Jenkins",
    "Grafana",
    "Prometheus",
    "Vue"
  ]
} as const;

export const featuredProjects: FeaturedProject[] = [
  {
    name: "Deriou/Cloud-ops-hub",
    description: "Single-node cloud-native platform",
    summary: "K3s + Jenkins + PLG on one 8GB node.",
    href: "https://github.com/Deriou/Cloud-ops-hub",
    stack: ["K3s", "Jenkins", "PLG", "Spring Boot", "Vue"]
  },
  {
    name: "Deriou/ai-resume",
    description: "LLM-powered resume optimization platform",
    summary: "Vue 3 + Spring Boot system for resume scoring, job matching, RBAC, Redis, observability, and Jenkins/K3s delivery.",
    href: "https://github.com/Deriou/ai-resume",
    stack: ["Vue 3", "Spring Boot", "Redis", "DeepSeek", "K3s"]
  },
  {
    name: "Deriou/yolodrive",
    description: "Autonomous driving vision perception practice",
    summary: "YOLO + DeepSORT vehicle tracking, traffic sign recognition, and lane segmentation experiments.",
    href: "https://github.com/Deriou/yolodrive",
    stack: ["Python", "YOLO", "DeepSORT", "YOLOv5", "Mask R-CNN"]
  }
];

export const curatedApps: CuratedAppDefinition[] = [
  {
    title: "应用实验室",
    description: "前端小工具与交互实验的公开入口。",
    summary: "Planning lightweight tools for release checks, trace demos, and resource planning.",
    fallbackRoute: "/apps",
    matchKeys: ["app-lab", "apps", "portal-ui"]
  }
];

export const opsOverview = {
  statusLabel: "Stable",
  summary: "Online"
} as const;

export const manifesto = "let the sapphire star light your way";
