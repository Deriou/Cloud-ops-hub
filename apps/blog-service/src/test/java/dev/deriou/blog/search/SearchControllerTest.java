package dev.deriou.blog.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.domain.entity.PostStatus;
import dev.deriou.blog.domain.entity.PostTagEntity;
import dev.deriou.blog.domain.entity.TagEntity;
import dev.deriou.blog.mapper.PostMapper;
import dev.deriou.blog.mapper.PostTagMapper;
import dev.deriou.blog.mapper.TagMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private PostTagMapper postTagMapper;

    @Test
    void search_endpoint_should_allow_anonymous_read_and_return_ranked_results() throws Exception {
        insertPost("cloud-java", "Cloud Java", "ops handbook", LocalDateTime.parse("2026-03-28T16:00:00"));
        insertPost("ops-java", "Ops notes", "java platform notes", LocalDateTime.parse("2026-03-28T15:00:00"));

        mockMvc.perform(get("/api/v1/blog/search")
                        .param("q", "java")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].slug").value("cloud-java"))
                .andExpect(jsonPath("$.data.records[1].slug").value("ops-java"));
    }

    @Test
    void empty_keyword_should_return_biz_error() throws Exception {
        mockMvc.perform(get("/api/v1/blog/search")
                        .param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ_ERROR"))
                .andExpect(jsonPath("$.message").value("keyword must not be blank"));
    }

    @Test
    void search_endpoint_should_return_posts_matched_by_taxonomy() throws Exception {
        PostEntity post = insertPost(
                "taxonomy-search",
                "Health dashboard",
                "alert panel notes",
                LocalDateTime.parse("2026-03-28T16:00:00")
        );
        bindTag(post.getId(), "Grafana", "grafana");

        mockMvc.perform(get("/api/v1/blog/search")
                        .param("q", "Grafana")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].slug").value("taxonomy-search"));
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
}
