package dev.deriou.blog.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.dto.post.UpdatePostRequest;
import dev.deriou.blog.mapper.PostMapper;
import dev.deriou.blog.service.PostService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PostRenderServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRenderService postRenderService;

    @Autowired
    private PostMapper postMapper;

    @SpyBean
    private MarkdownRenderer markdownRenderer;

    @AfterEach
    void tearDown() {
        reset(markdownRenderer);
    }

    @Test
    void first_request_should_render_and_cache_html() {
        long postId = insertPost("Render Once", "render-once", "# Hello");

        String html = postRenderService.getRenderedHtml(postId);

        assertThat(html).isEqualTo("<h1>Hello</h1>\n");
        verify(markdownRenderer, times(1)).render("# Hello");
    }

    @Test
    void second_request_should_hit_cache() {
        long postId = insertPost("Render Twice", "render-twice", "# Cache");

        String first = postRenderService.getRenderedHtml(postId);
        String second = postRenderService.getRenderedHtml(postId);

        assertThat(first).isEqualTo(second);
        verify(markdownRenderer, times(1)).render("# Cache");
    }

    @Test
    void post_updated_should_invalidate_cache() {
        long postId = insertPost("Update Cache", "update-cache", "# Before");

        String firstHtml = postRenderService.getRenderedHtml(postId);
        postService.updatePost(postId, new UpdatePostRequest(
                "Update Cache",
                "update-cache",
                "# After",
                "summary",
                List.of(),
                List.of()
        ));
        String secondHtml = postRenderService.getRenderedHtml(postId);

        assertThat(firstHtml).isEqualTo("<h1>Before</h1>\n");
        assertThat(secondHtml).isEqualTo("<h1>After</h1>\n");
        verify(markdownRenderer, times(1)).render("# Before");
        verify(markdownRenderer, times(1)).render("# After");
    }

    @Test
    void invalid_markdown_should_return_safe_html_or_error() {
        long postId = insertPost("Broken Render", "broken-render", "<script>alert(1)</script>");

        doThrow(new IllegalStateException("boom"))
                .when(markdownRenderer)
                .render("<script>alert(1)</script>");

        String html = postRenderService.getRenderedHtml(postId);

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).startsWith("<pre>");
        assertThat(html).endsWith("</pre>");
    }

    private long insertPost(String title, String slug, String markdownContent) {
        PostEntity entity = new PostEntity();
        entity.setTitle(title);
        entity.setSlug(slug);
        entity.setMarkdownContent(markdownContent);
        entity.setSummary("summary");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        postMapper.insert(entity);
        return entity.getId();
    }
}
