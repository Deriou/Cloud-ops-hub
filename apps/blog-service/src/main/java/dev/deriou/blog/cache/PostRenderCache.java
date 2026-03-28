package dev.deriou.blog.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PostRenderCache {

    private final Cache<String, String> cache;

    public PostRenderCache(
            @Value("${blog.render.cache.maximum-size:256}") long maximumSize,
            @Value("${blog.render.cache.ttl:PT30M}") Duration ttl
    ) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .build();
    }

    public String get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, String html) {
        cache.put(key, html);
    }

    public void evict(String key) {
        cache.invalidate(key);
    }

    public String buildKey(Long postId, LocalDateTime updateTime) {
        Objects.requireNonNull(postId, "postId must not be null");
        Objects.requireNonNull(updateTime, "updateTime must not be null");
        return postId + ":" + updateTime;
    }
}
