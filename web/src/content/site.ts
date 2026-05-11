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
  }
];

export const curatedApps: CuratedAppDefinition[] = [
  {
    title: "Portal",
    description: "Application directory",
    summary: "Aggregated sub-app entry.",
    fallbackRoute: "/apps",
    matchKeys: ["portal", "gateway-portal", "portal-ui"]
  }
];

export const opsOverview = {
  statusLabel: "Stable",
  summary: "Online"
} as const;

export const manifesto = "I am Deriou. Blade of Mikaela";
