package dev.deriou.blog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deriou.blog.dto.common.PagedResponse;
import dev.deriou.blog.dto.post.CreatePostRequest;
import dev.deriou.blog.dto.post.PostDetailResponse;
import dev.deriou.blog.dto.post.PostPageQuery;
import dev.deriou.blog.dto.post.PostSummaryResponse;
import dev.deriou.blog.dto.post.UpdatePostRequest;
import dev.deriou.blog.dto.taxonomy.TaxonomyCreateRequest;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import dev.deriou.blog.service.CategoryService;
import dev.deriou.blog.service.PostService;
import dev.deriou.blog.service.TagService;
import dev.deriou.common.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PostServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private TagService tagService;

    @Autowired
    private CategoryService categoryService;

    @Test
    void create_post_with_tags_and_categories_should_persist_relations() {
        TaxonomyResponse tag = tagService.createTag(new TaxonomyCreateRequest("Java", "java"));
        TaxonomyResponse category = categoryService.createCategory(new TaxonomyCreateRequest("Backend", "backend"));

        PostDetailResponse created = postService.createPost(new CreatePostRequest(
                "My First Post",
                "my-first-post",
                "# Hello Blog",
                "hello summary",
                List.of(tag.id()),
                List.of(category.id())
        ));

        assertThat(created.id()).isNotNull();
        assertThat(created.slug()).isEqualTo("my-first-post");
        assertThat(created.renderedHtml()).contains("<h1>Hello Blog</h1>");
        assertThat(created.tags()).extracting(TaxonomyResponse::name).containsExactly("Java");
        assertThat(created.categories()).extracting(TaxonomyResponse::name).containsExactly("Backend");
    }

    @Test
    void list_posts_should_return_paged_result() {
        postService.createPost(new CreatePostRequest("Post 1", "post-1", "# 1", "summary 1", List.of(), List.of()));
        postService.createPost(new CreatePostRequest("Post 2", "post-2", "# 2", "summary 2", List.of(), List.of()));
        postService.createPost(new CreatePostRequest("Post 3", "post-3", "# 3", "summary 3", List.of(), List.of()));

        PagedResponse<PostSummaryResponse> page = postService.listPosts(new PostPageQuery(1, 2));

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.records()).hasSize(2);
        assertThat(page.records()).extracting(PostSummaryResponse::title).containsExactly("Post 3", "Post 2");
    }

    @Test
    void get_post_detail_should_include_tags_and_categories() {
        TaxonomyResponse tagA = tagService.createTag(new TaxonomyCreateRequest("Java", "java"));
        TaxonomyResponse tagB = tagService.createTag(new TaxonomyCreateRequest("Spring", "spring"));
        TaxonomyResponse category = categoryService.createCategory(new TaxonomyCreateRequest("Backend", "backend"));

        PostDetailResponse created = postService.createPost(new CreatePostRequest(
                "Service Detail",
                "service-detail",
                "content",
                "detail summary",
                List.of(tagA.id(), tagB.id()),
                List.of(category.id())
        ));

        PostDetailResponse detail = postService.getPostDetail(created.id());

        assertThat(detail.title()).isEqualTo("Service Detail");
        assertThat(detail.markdownContent()).isEqualTo("content");
        assertThat(detail.renderedHtml()).contains("<p>content</p>");
        assertThat(detail.tags()).extracting(TaxonomyResponse::slug).containsExactly("java", "spring");
        assertThat(detail.categories()).extracting(TaxonomyResponse::slug).containsExactly("backend");
    }

    @Test
    void update_post_should_refresh_content_and_relations() {
        TaxonomyResponse oldTag = tagService.createTag(new TaxonomyCreateRequest("Java", "java"));
        TaxonomyResponse newTag = tagService.createTag(new TaxonomyCreateRequest("Spring", "spring"));
        TaxonomyResponse oldCategory = categoryService.createCategory(new TaxonomyCreateRequest("Backend", "backend"));
        TaxonomyResponse newCategory = categoryService.createCategory(new TaxonomyCreateRequest("Platform", "platform"));

        PostDetailResponse created = postService.createPost(new CreatePostRequest(
                "Update Me",
                "update-me",
                "# v1",
                "summary 1",
                List.of(oldTag.id()),
                List.of(oldCategory.id())
        ));

        PostDetailResponse updated = postService.updatePost(created.id(), new UpdatePostRequest(
                "Updated Post",
                "updated-post",
                "# v2",
                "summary 2",
                List.of(newTag.id()),
                List.of(newCategory.id())
        ));

        assertThat(updated.title()).isEqualTo("Updated Post");
        assertThat(updated.slug()).isEqualTo("updated-post");
        assertThat(updated.markdownContent()).isEqualTo("# v2");
        assertThat(updated.renderedHtml()).contains("<h1>v2</h1>");
        assertThat(updated.tags()).extracting(TaxonomyResponse::slug).containsExactly("spring");
        assertThat(updated.categories()).extracting(TaxonomyResponse::slug).containsExactly("platform");
    }

    @Test
    void page_size_over_limit_should_be_rejected() {
        assertThatThrownBy(() -> postService.listPosts(new PostPageQuery(1, 51)))
                .isInstanceOf(BizException.class)
                .hasMessage("page size must not exceed 50");
    }
}
