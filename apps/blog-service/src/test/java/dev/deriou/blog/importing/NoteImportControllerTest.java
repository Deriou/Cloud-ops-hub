package dev.deriou.blog.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.mapper.PostMapper;
import dev.deriou.common.auth.AuthInterceptor;
import dev.deriou.common.auth.GuestTokenStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class NoteImportControllerTest {

    private static final String HEADER_NAME = AuthInterceptor.HEADER_NAME;
    private static final String MASTER_KEY = "test-master-key";
    private static final String GUEST_TOKEN_ID = "import-guest-token";
    private static final String GUEST_TOKEN_VALUE = "import-guest-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GuestTokenStore guestTokenStore;

    @Autowired
    private PostMapper postMapper;

    @BeforeEach
    void setUp() {
        guestTokenStore.put(GUEST_TOKEN_ID, GUEST_TOKEN_VALUE);
    }

    @Test
    void batch_import_should_create_published_post_with_created_time_and_taxonomies() throws Exception {
        String noteId = "8d8e48af-5ea6-4d81-8f36-8a50d0a83624";

        MvcResult result = mockMvc.perform(post("/api/v1/blog/import/notes:batch")
                        .header(HEADER_NAME, MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "notes", List.of(Map.of(
                                        "noteId", noteId,
                                        "sourcePath", "Ops/docker/basics.md",
                                        "title", "Docker Basics",
                                        "summary", "docker summary",
                                        "markdownContent", "# Docker",
                                        "tags", List.of("docker", "devops"),
                                        "categories", List.of("cloud-ops"),
                                        "createdAt", "2024-01-10T21:00:00+08:00",
                                        "contentHash", "sha256:abc",
                                        "publish", true
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.updated").value(0))
                .andExpect(jsonPath("$.data.results[0].action").value("created"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        long postId = data.path("results").get(0).path("postId").asLong();
        PostEntity entity = postMapper.selectById(postId);

        assertThat(entity.getNoteId()).isEqualTo(noteId);
        assertThat(entity.getStatus()).isEqualTo("published");
        assertThat(entity.getSlug()).isEqualTo("obsidian-8d8e48af-5ea6-4d81-8f36-8a50d0a83624");
        assertThat(entity.getCreateTime()).isEqualTo(LocalDateTime.parse("2024-01-10T21:00:00"));

        mockMvc.perform(get("/api/v1/blog/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Docker Basics"))
                .andExpect(jsonPath("$.data.tags[0].slug").value("devops"))
                .andExpect(jsonPath("$.data.tags[1].slug").value("docker"))
                .andExpect(jsonPath("$.data.categories[0].slug").value("cloud-ops"));
    }

    @Test
    void repeated_import_without_changes_should_be_skipped_and_keep_update_time() throws Exception {
        String noteId = "note-skip-1";
        importNote(noteId, "# Same", "sha256:same", true);

        PostEntity before = findByNoteId(noteId);
        MvcResult result = importNote(noteId, "# Same", "sha256:same", true);
        PostEntity after = findByNoteId(noteId);

        assertThat(after.getUpdateTime()).isEqualTo(before.getUpdateTime());
        assertThat(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data")
                .path("results")
                .get(0)
                .path("action")
                .asText()).isEqualTo("skipped");
    }

    @Test
    void changed_import_should_refresh_update_time_and_keep_create_time() throws Exception {
        String noteId = "note-update-1";
        importNote(noteId, "# Before", "sha256:before", true);

        PostEntity before = findByNoteId(noteId);
        LocalDateTime originalCreateTime = before.getCreateTime();
        Thread.sleep(25L);

        MvcResult result = importNote(noteId, "# After", "sha256:after", true, "2025-03-20T09:00:00+08:00");
        PostEntity after = findByNoteId(noteId);

        assertThat(after.getCreateTime()).isEqualTo(originalCreateTime);
        assertThat(after.getUpdateTime()).isAfter(before.getUpdateTime());
        assertThat(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data")
                .path("results")
                .get(0)
                .path("action")
                .asText()).isEqualTo("updated");
    }

    @Test
    void publish_false_should_hide_post_from_public_queries() throws Exception {
        String noteId = "note-draft-1";
        importNote(noteId, "# Hidden", "sha256:hidden", false);

        PostEntity entity = findByNoteId(noteId);
        assertThat(entity.getStatus()).isEqualTo("draft");

        mockMvc.perform(get("/api/v1/blog/posts")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/blog/posts/{postId}", entity.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("post not found"));
    }

    @Test
    void anonymous_import_write_should_be_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/blog/import/notes:batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("notes", List.of()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private MvcResult importNote(String noteId, String markdownContent, String contentHash, boolean publish) throws Exception {
        return importNote(noteId, markdownContent, contentHash, publish, "2024-02-01T10:15:00+08:00");
    }

    private MvcResult importNote(
            String noteId,
            String markdownContent,
            String contentHash,
            boolean publish,
            String createdAt
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/blog/import/notes:batch")
                        .header(HEADER_NAME, MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "notes", List.of(Map.of(
                                        "noteId", noteId,
                                        "sourcePath", "Ops/" + noteId + ".md",
                                        "title", "Imported " + noteId,
                                        "summary", "summary " + noteId,
                                        "markdownContent", markdownContent,
                                        "tags", List.of("ops"),
                                        "categories", List.of("cloud-ops"),
                                        "createdAt", createdAt,
                                        "contentHash", contentHash,
                                        "publish", publish
                                ))
                        ))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private PostEntity findByNoteId(String noteId) {
        return postMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getNoteId, noteId)
                .last("limit 1"));
    }
}
