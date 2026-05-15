package dev.deriou.blog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deriou.blog.domain.entity.CategoryEntity;
import dev.deriou.blog.domain.entity.PostCategoryEntity;
import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.domain.entity.PostStatus;
import dev.deriou.blog.domain.entity.PostTagEntity;
import dev.deriou.blog.domain.entity.TagEntity;
import dev.deriou.blog.dto.common.PagedResponse;
import dev.deriou.blog.dto.post.PostSummaryResponse;
import dev.deriou.blog.mapper.CategoryMapper;
import dev.deriou.blog.mapper.PostMapper;
import dev.deriou.blog.mapper.PostCategoryMapper;
import dev.deriou.blog.mapper.PostTagMapper;
import dev.deriou.blog.mapper.TagMapper;
import dev.deriou.common.exception.BizException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SearchServiceTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private PostTagMapper postTagMapper;

    @Autowired
    private PostCategoryMapper postCategoryMapper;

    @Test
    void search_should_return_ranked_results_by_keyword() {
        insertPost("java-title-win", "Java tuning", "ops handbook", LocalDateTime.parse("2026-03-28T14:00:00"));
        insertPost("java-body-match", "Ops notes", "java memory tuning guide", LocalDateTime.parse("2026-03-28T15:00:00"));
        insertPost("java-later-body", "Platform notes", "java cache details", LocalDateTime.parse("2026-03-28T16:00:00"));

        PagedResponse<PostSummaryResponse> result = searchService.search(new SearchQuery("java", 1, 10));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.records()).extracting(PostSummaryResponse::slug)
                .containsExactly("java-title-win", "java-later-body", "java-body-match");
    }

    @Test
    void empty_keyword_should_return_validation_error() {
        assertThatThrownBy(() -> searchService.search(new SearchQuery("   ", 1, 10)))
                .isInstanceOf(BizException.class)
                .hasMessage("keyword must not be blank");
    }

    @Test
    void search_should_support_pagination() {
        insertPost("cloud-1", "Cloud alpha", "cloud install guide", LocalDateTime.parse("2026-03-28T14:00:00"));
        insertPost("cloud-2", "Cloud beta", "cloud release notes", LocalDateTime.parse("2026-03-28T15:00:00"));
        insertPost("cloud-3", "Cloud gamma", "cloud rollback plan", LocalDateTime.parse("2026-03-28T16:00:00"));

        PagedResponse<PostSummaryResponse> result = searchService.search(new SearchQuery("cloud", 2, 1));

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.records()).extracting(PostSummaryResponse::slug).containsExactly("cloud-2");
    }

    @Test
    void keyword_not_found_should_return_empty_list() {
        insertPost("existing-post", "Ops intro", "release guide", LocalDateTime.parse("2026-03-28T14:00:00"));

        PagedResponse<PostSummaryResponse> result = searchService.search(new SearchQuery("missing", 1, 10));

        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    void search_should_match_posts_by_tag_name() {
        PostEntity taggedPost = insertPost(
                "tagged-observability",
                "Metrics pipeline",
                "prometheus setup notes",
                LocalDateTime.parse("2026-03-28T14:00:00")
        );
        insertPost(
                "unrelated-post",
                "Release guide",
                "deploy checklist",
                LocalDateTime.parse("2026-03-28T15:00:00")
        );
        bindTag(taggedPost.getId(), "Grafana", "grafana");

        PagedResponse<PostSummaryResponse> result = searchService.search(new SearchQuery("Grafana", 1, 10));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).extracting(PostSummaryResponse::slug).containsExactly("tagged-observability");
    }

    @Test
    void search_should_match_posts_by_category_name() {
        PostEntity categorizedPost = insertPost(
                "categorized-k8s",
                "Node health",
                "cluster inspection notes",
                LocalDateTime.parse("2026-03-28T14:00:00")
        );
        insertPost(
                "unrelated-runbook",
                "Backup plan",
                "database snapshots",
                LocalDateTime.parse("2026-03-28T15:00:00")
        );
        bindCategory(categorizedPost.getId(), "Kubernetes", "kubernetes");

        PagedResponse<PostSummaryResponse> result = searchService.search(new SearchQuery("Kubernetes", 1, 10));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).extracting(PostSummaryResponse::slug).containsExactly("categorized-k8s");
    }

    private PostEntity insertPost(String slug, String title, String markdownContent, LocalDateTime updateTime) {
        PostEntity entity = new PostEntity();
        entity.setSlug(slug);
        entity.setStatus(PostStatus.PUBLISHED);
        entity.setTitle(title);
        entity.setMarkdownContent(markdownContent);
        entity.setSummary(title + " summary");
        entity.setCreateTime(updateTime.minusHours(1));
        entity.setUpdateTime(updateTime);
        postMapper.insert(entity);
        return entity;
    }

    private void bindTag(Long postId, String name, String slug) {
        LocalDateTime now = LocalDateTime.parse("2026-03-28T12:00:00");
        TagEntity tag = new TagEntity();
        tag.setName(name);
        tag.setSlug(slug);
        tag.setCreateTime(now);
        tag.setUpdateTime(now);
        tagMapper.insert(tag);

        PostTagEntity relation = new PostTagEntity();
        relation.setPostId(postId);
        relation.setTagId(tag.getId());
        postTagMapper.insert(relation);
    }

    private void bindCategory(Long postId, String name, String slug) {
        LocalDateTime now = LocalDateTime.parse("2026-03-28T12:00:00");
        CategoryEntity category = new CategoryEntity();
        category.setName(name);
        category.setSlug(slug);
        category.setCreateTime(now);
        category.setUpdateTime(now);
        categoryMapper.insert(category);

        PostCategoryEntity relation = new PostCategoryEntity();
        relation.setPostId(postId);
        relation.setCategoryId(category.getId());
        postCategoryMapper.insert(relation);
    }
}
