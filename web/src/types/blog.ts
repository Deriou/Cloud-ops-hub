export interface TaxonomyItem {
  id: number;
  name: string;
  slug: string;
}

export interface PostSummary {
  id: number;
  title: string;
  slug: string;
  summary: string;
  updateTime: string;
}

export interface PostDetail {
  id: number;
  title: string;
  slug: string;
  markdownContent: string;
  renderedHtml: string;
  summary: string;
  createTime: string;
  updateTime: string;
  tags: TaxonomyItem[];
  categories: TaxonomyItem[];
}
