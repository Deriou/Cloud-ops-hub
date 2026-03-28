package dev.deriou.blog.search;

import dev.deriou.blog.dto.common.PagedResponse;
import dev.deriou.blog.dto.post.PostSummaryResponse;
import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "站内搜索", description = "博客文章的关键词搜索接口")
@RestController
@RequestMapping("/api/v1/blog/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "搜索文章", description = "按关键词搜索文章标题和正文，并按相关度与更新时间排序")
    @GetMapping
    public ApiResponse<PagedResponse<PostSummaryResponse>> search(
            @RequestParam("q") String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return ApiResponse.success(searchService.search(new SearchQuery(keyword, pageNo, pageSize)));
    }
}
