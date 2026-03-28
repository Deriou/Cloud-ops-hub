package dev.deriou.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.deriou.blog", "dev.deriou.common"})
public class BlogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogServiceApplication.class, args);
    }
}
