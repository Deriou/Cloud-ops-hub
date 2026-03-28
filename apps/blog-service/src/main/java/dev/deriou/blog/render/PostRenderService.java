package dev.deriou.blog.render;

import dev.deriou.blog.cache.PostRenderCache;
import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.mapper.PostMapper;
import dev.deriou.common.api.ResultCode;
import dev.deriou.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class PostRenderService {

    private final PostMapper postMapper;
    private final MarkdownRenderer markdownRenderer;
    private final PostRenderCache postRenderCache;

    public PostRenderService(
            PostMapper postMapper,
            MarkdownRenderer markdownRenderer,
            PostRenderCache postRenderCache
    ) {
        this.postMapper = postMapper;
        this.markdownRenderer = markdownRenderer;
        this.postRenderCache = postRenderCache;
    }

    public String getRenderedHtml(Long postId) {
        PostEntity post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "post not found");
        }

        return getRenderedHtml(post);
    }

    public String getRenderedHtml(PostEntity post) {
        String cacheKey = postRenderCache.buildKey(post.getId(), post.getUpdateTime());
        String cachedHtml = postRenderCache.get(cacheKey);
        if (cachedHtml != null) {
            return cachedHtml;
        }

        String renderedHtml = renderSafely(post.getMarkdownContent());
        postRenderCache.put(cacheKey, renderedHtml);
        return renderedHtml;
    }

    public void evict(PostEntity post) {
        if (post == null || post.getId() == null || post.getUpdateTime() == null) {
            return;
        }

        postRenderCache.evict(postRenderCache.buildKey(post.getId(), post.getUpdateTime()));
    }

    private String renderSafely(String markdown) {
        try {
            return markdownRenderer.render(markdown);
        } catch (RuntimeException ex) {
            return "<pre>" + HtmlUtils.htmlEscape(markdown) + "</pre>";
        }
    }
}
