package dev.deriou.blog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.dto.common.PagedResponse;
import dev.deriou.blog.dto.post.PostSummaryResponse;
import dev.deriou.blog.mapper.PostMapper;
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

    private void insertPost(String slug, String title, String markdownContent, LocalDateTime updateTime) {
        PostEntity entity = new PostEntity();
        entity.setSlug(slug);
        entity.setTitle(title);
        entity.setMarkdownContent(markdownContent);
        entity.setSummary(title + " summary");
        entity.setCreateTime(updateTime.minusHours(1));
        entity.setUpdateTime(updateTime);
        postMapper.insert(entity);
    }
}
