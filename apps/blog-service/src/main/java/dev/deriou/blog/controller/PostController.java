package dev.deriou.blog.controller;

import dev.deriou.blog.dto.common.PagedResponse;
import dev.deriou.blog.dto.post.CreatePostRequest;
import dev.deriou.blog.dto.post.PostDetailResponse;
import dev.deriou.blog.dto.post.PostPageQuery;
import dev.deriou.blog.dto.post.PostSummaryResponse;
import dev.deriou.blog.dto.post.UpdatePostRequest;
import dev.deriou.blog.service.PostService;
import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "文章管理", description = "博客文章的查询与写入接口")
@RestController
@RequestMapping("/api/v1/blog/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "创建文章", description = "创建 Markdown 文章并绑定已有标签、分类")
    @PostMapping
    public ApiResponse<PostDetailResponse> createPost(@RequestBody CreatePostRequest request) {
        return ApiResponse.success(postService.createPost(request));
    }

    @Operation(summary = "更新文章", description = "更新文章 Markdown 内容并让渲染缓存按新版本生效")
    @PutMapping("/{postId}")
    public ApiResponse<PostDetailResponse> updatePost(@PathVariable Long postId, @RequestBody UpdatePostRequest request) {
        return ApiResponse.success(postService.updatePost(postId, request));
    }

    @Operation(summary = "分页查询文章", description = "按更新时间倒序返回文章分页列表")
    @GetMapping
    public ApiResponse<PagedResponse<PostSummaryResponse>> listPosts(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.success(postService.listPosts(new PostPageQuery(page, size)));
    }

    @Operation(summary = "查询文章详情", description = "返回文章正文及已绑定的标签、分类")
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getPostDetail(@PathVariable Long postId) {
        return ApiResponse.success(postService.getPostDetail(postId));
    }
}
