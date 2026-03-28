package dev.deriou.gateway.registry;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "应用注册表元数据")
public record AppMeta(
        @Schema(description = "应用唯一标识", example = "blog-service")
        String appKey,
        @Schema(description = "应用标题", example = "博客服务")
        String title,
        @Schema(description = "前端路由", example = "/blog")
        String route,
        @Schema(description = "应用状态", example = "UP")
        String status,
        @Schema(description = "应用描述", example = "内容管理与 SEO 服务")
        String description,
        @Schema(description = "排序权重，值越小越靠前", example = "10")
        Integer sortOrder
) {
}
