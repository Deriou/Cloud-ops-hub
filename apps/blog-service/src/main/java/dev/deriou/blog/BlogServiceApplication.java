package dev.deriou.blog;

import dev.deriou.blog.config.BlogAssetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"dev.deriou.blog", "dev.deriou.common"})
@EnableConfigurationProperties(BlogAssetProperties.class)
public class BlogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogServiceApplication.class, args);
    }
}
