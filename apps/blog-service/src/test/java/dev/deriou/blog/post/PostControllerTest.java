package dev.deriou.blog.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deriou.common.auth.AuthInterceptor;
import dev.deriou.common.auth.GuestTokenStore;
import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.domain.entity.PostStatus;
import dev.deriou.blog.domain.entity.PostTagEntity;
import dev.deriou.blog.mapper.PostMapper;
import dev.deriou.blog.mapper.PostTagMapper;
import java.time.LocalDateTime;
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
class PostControllerTest {

    private static final String HEADER_NAME = AuthInterceptor.HEADER_NAME;
    private static final String MASTER_KEY = "test-master-key";
    private static final String GUEST_TOKEN_ID = "blog-guest-token";
    private static final String GUEST_TOKEN_VALUE = "blog-guest-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GuestTokenStore guestTokenStore;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostTagMapper postTagMapper;

    @BeforeEach
    void setUp() {
        guestTokenStore.put(GUEST_TOKEN_ID, GUEST_TOKEN_VALUE);
    }

    @Test
    void post_endpoints_should_return_api_response_with_taxonomies() throws Exception {
        long tagId = createTaxonomy("/api/v1/blog/tags", "Java", "java");
        long categoryId = createTaxonomy("/api/v1/blog/categories", "Backend", "backend");

        MvcResult createResult = mockMvc.perform(post("/api/v1/blog/posts")
                        .header(HEADER_NAME, MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "title", "Controller Post",
                                "slug", "controller-post",
                                "markdownContent", "# Controller",
                                "summary", "controller summary",
                                "tagIds", new long[] {tagId},
                                "categoryIds", new long[] {categoryId}
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.slug").value("controller-post"))
                .andExpect(jsonPath("$.data.renderedHtml").value("<h1>Controller</h1>\n"))
                .andExpect(jsonPath("$.data.tags[0].slug").value("java"))
                .andExpect(jsonPath("$.data.categories[0].slug").value("backend"))
                .andReturn();

        long postId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/v1/blog/posts")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].slug").value("controller-post"));

        mockMvc.perform(get("/api/v1/blog/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Controller Post"))
                .andExpect(jsonPath("$.data.renderedHtml").value("<h1>Controller</h1>\n"))
                .andExpect(jsonPath("$.data.tags[0].name").value("Java"))
                .andExpect(jsonPath("$.data.categories[0].name").value("Backend"));

        mockMvc.perform(get("/api/v1/blog/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("java"));

        mockMvc.perform(get("/api/v1/blog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("backend"));
    }

    @Test
    void update_endpoint_should_refresh_html_and_guest_write_should_be_rejected() throws Exception {
        long postId = createPost("Cache Post", "cache-post", "# Before");

        mockMvc.perform(put("/api/v1/blog/posts/{postId}", postId)
                        .header(HEADER_NAME, GUEST_TOKEN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "title", "Blocked Update",
                                "slug", "blocked-update",
                                "markdownContent", "# blocked",
                                "summary", "blocked summary",
                                "tagIds", new long[] {},
                                "categoryIds", new long[] {}
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/blog/posts/{postId}", postId)
                        .header(HEADER_NAME, MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "title", "Updated Cache Post",
                                "slug", "updated-cache-post",
                                "markdownContent", "# After",
                                "summary", "after summary",
                                "tagIds", new long[] {},
                                "categoryIds", new long[] {}
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("updated-cache-post"))
                .andExpect(jsonPath("$.data.renderedHtml").value("<h1>After</h1>\n"));

        mockMvc.perform(get("/api/v1/blog/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markdownContent").value("# After"))
                .andExpect(jsonPath("$.data.renderedHtml").value("<h1>After</h1>\n"));
    }

    @Test
    void page_size_over_limit_should_be_rejected_by_api() throws Exception {
        mockMvc.perform(get("/api/v1/blog/posts")
                        .param("page", "1")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ_ERROR"))
                .andExpect(jsonPath("$.message").value("page size must not exceed 50"));
    }

    @Test
    void anonymous_write_requests_should_still_be_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/blog/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "title", "Anonymous Post",
                                "slug", "anonymous-post",
                                "markdownContent", "# Anonymous",
                                "summary", "anonymous summary",
                                "tagIds", new long[] {},
                                "categoryIds", new long[] {}
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tags_endpoint_should_only_return_tags_used_by_published_posts() throws Exception {
        long publishedTagId = createTaxonomy("/api/v1/blog/tags", "Java", "java");
        long draftOnlyTagId = createTaxonomy("/api/v1/blog/tags", "Draft Only", "draft-only");
        createTaxonomy("/api/v1/blog/tags", "Unused", "unused");

        createPost("Published Post", "published-post", "# Published", new long[] {publishedTagId});
        createDraftPost("draft-note", "Draft Post", "draft-post", draftOnlyTagId);

        mockMvc.perform(get("/api/v1/blog/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].slug").value("java"));
    }

    private long createTaxonomy(String path, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header(HEADER_NAME, MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("name", name, "slug", slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        return data.path("id").asLong();
    }

    private long createPost(String title, String slug, String markdownContent) throws Exception {
        return createPost(title, slug, markdownContent, new long[] {});
    }

    private long createPost(String title, String slug, String markdownContent, long[] tagIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/blog/posts")
                        .header(HEADER_NAME, MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "title", title,
                                "slug", slug,
                                "markdownContent", markdownContent,
                                "summary", "summary",
                                "tagIds", tagIds,
                                "categoryIds", new long[] {}
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        return data.path("id").asLong();
    }

    private void createDraftPost(String noteId, String title, String slug, long tagId) {
        LocalDateTime now = LocalDateTime.now();

        PostEntity post = new PostEntity();
        post.setNoteId(noteId);
        post.setStatus(PostStatus.DRAFT);
        post.setTitle(title);
        post.setSlug(slug);
        post.setMarkdownContent("# Draft");
        post.setSummary("draft summary");
        post.setCreateTime(now);
        post.setUpdateTime(now);
        post.setLastSyncTime(now);
        postMapper.insert(post);

        PostTagEntity relation = new PostTagEntity();
        relation.setPostId(post.getId());
        relation.setTagId(tagId);
        postTagMapper.insert(relation);
    }
}
