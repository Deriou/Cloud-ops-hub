package dev.deriou.blog.controller;

import dev.deriou.blog.dto.taxonomy.TaxonomyCreateRequest;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import dev.deriou.blog.service.TagService;
import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "标签管理", description = "博客标签的查询与写入接口")
@RestController
@RequestMapping("/api/v1/blog/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "创建标签", description = "创建新的文章标签")
    @PostMapping
    public ApiResponse<TaxonomyResponse> createTag(@RequestBody TaxonomyCreateRequest request) {
        return ApiResponse.success(tagService.createTag(request));
    }

    @Operation(summary = "查询标签列表", description = "按名称升序返回标签列表")
    @GetMapping
    public ApiResponse<List<TaxonomyResponse>> listTags() {
        return ApiResponse.success(tagService.listTags());
    }
}
