package dev.deriou.blog.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deriou.common.auth.AuthInterceptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ImageAssetControllerTest {

    private static final String HEADER_NAME = AuthInterceptor.HEADER_NAME;
    private static final String MASTER_KEY = "test-master-key";
    private static final Path ASSET_ROOT = Path.of("/tmp/cloud-ops-hub-blog-assets-test");
    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j7i0AAAAASUVORK5CYII=");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanAssetRoot() throws IOException {
        if (Files.notExists(ASSET_ROOT)) {
            return;
        }

        try (var walk = Files.walk(ASSET_ROOT)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }

    @Test
    void image_upload_should_store_file_and_allow_public_get_without_key() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "tiny.png", "image/png", PNG_BYTES);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/blog/assets/images")
                        .file(file)
                        .header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value(org.hamcrest.Matchers.startsWith("sha256-")))
                .andExpect(jsonPath("$.data.path").value(org.hamcrest.Matchers.startsWith("/api/v1/blog/assets/images/")))
                .andReturn();

        String key = objectMapper.readTree(uploadResult.getResponse().getContentAsByteArray())
                .path("data")
                .path("key")
                .asText();

        assertThat(Files.exists(ASSET_ROOT.resolve(key))).isTrue();

        mockMvc.perform(get("/api/v1/blog/assets/images/{key}", key))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG_BYTES));
    }

    @Test
    void identical_uploads_should_return_same_key() throws Exception {
        MockMultipartFile fileA = new MockMultipartFile("file", "same.png", "image/png", PNG_BYTES);
        MockMultipartFile fileB = new MockMultipartFile("file", "same.png", "image/png", PNG_BYTES);

        String keyA = uploadAndGetKey(fileA);
        String keyB = uploadAndGetKey(fileB);

        assertThat(keyA).isEqualTo(keyB);
    }

    private String uploadAndGetKey(MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/blog/assets/images")
                        .file(file)
                        .header(HEADER_NAME, MASTER_KEY))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data")
                .path("key")
                .asText();
    }
}
