package dev.deriou.blog.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.domain.entity.PostStatus;
import dev.deriou.blog.mapper.PostMapper;
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

    private void insertPost(String slug, String title, String markdownContent, LocalDateTime updateTime) {
        PostEntity entity = new PostEntity();
        entity.setSlug(slug);
        entity.setStatus(PostStatus.PUBLISHED);
        entity.setTitle(title);
        entity.setMarkdownContent(markdownContent);
        entity.setSummary(title + " summary");
        entity.setCreateTime(updateTime.minusHours(1));
        entity.setUpdateTime(updateTime);
        postMapper.insert(entity);
    }
}
