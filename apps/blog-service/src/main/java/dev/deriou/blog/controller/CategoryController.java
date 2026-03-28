package dev.deriou.blog.controller;

import dev.deriou.blog.dto.taxonomy.TaxonomyCreateRequest;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import dev.deriou.blog.service.CategoryService;
import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "分类管理", description = "博客分类的查询与写入接口")
@RestController
@RequestMapping("/api/v1/blog/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "创建分类", description = "创建新的文章分类")
    @PostMapping
    public ApiResponse<TaxonomyResponse> createCategory(@RequestBody TaxonomyCreateRequest request) {
        return ApiResponse.success(categoryService.createCategory(request));
    }

    @Operation(summary = "查询分类列表", description = "按名称升序返回分类列表")
    @GetMapping
    public ApiResponse<List<TaxonomyResponse>> listCategories() {
        return ApiResponse.success(categoryService.listCategories());
    }
}
