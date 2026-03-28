package dev.deriou.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.deriou.blog.converter.PostConverter;
import dev.deriou.blog.converter.TaxonomyConverter;
import dev.deriou.blog.domain.entity.CategoryEntity;
import dev.deriou.blog.domain.entity.PostCategoryEntity;
import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.domain.entity.PostTagEntity;
import dev.deriou.blog.domain.entity.TagEntity;
import dev.deriou.blog.dto.common.PagedResponse;
import dev.deriou.blog.dto.post.CreatePostRequest;
import dev.deriou.blog.dto.post.PostDetailResponse;
import dev.deriou.blog.dto.post.PostPageQuery;
import dev.deriou.blog.dto.post.PostSummaryResponse;
import dev.deriou.blog.dto.post.UpdatePostRequest;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import dev.deriou.blog.mapper.PostCategoryMapper;
import dev.deriou.blog.mapper.PostMapper;
import dev.deriou.blog.mapper.PostTagMapper;
import dev.deriou.blog.render.PostRenderService;
import dev.deriou.common.api.ResultCode;
import dev.deriou.common.exception.BizException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    static final long MAX_PAGE_SIZE = 50L;

    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final PostCategoryMapper postCategoryMapper;
    private final TagService tagService;
    private final CategoryService categoryService;
    private final PostRenderService postRenderService;

    public PostService(
            PostMapper postMapper,
            PostTagMapper postTagMapper,
            PostCategoryMapper postCategoryMapper,
            TagService tagService,
            CategoryService categoryService,
            PostRenderService postRenderService
    ) {
        this.postMapper = postMapper;
        this.postTagMapper = postTagMapper;
        this.postCategoryMapper = postCategoryMapper;
        this.tagService = tagService;
        this.categoryService = categoryService;
        this.postRenderService = postRenderService;
    }

    @Transactional
    public PostDetailResponse createPost(CreatePostRequest request) {
        String title = SlugResolver.requireText(request.title(), "post title");
        String slug = SlugResolver.resolve(title, request.slug(), "post");
        String markdownContent = SlugResolver.requireText(request.markdownContent(), "markdown content");
        validatePostUniqueness(slug);

        List<Long> tagIds = distinctIds(request.tagIds());
        List<Long> categoryIds = distinctIds(request.categoryIds());
        tagService.getExistingTags(tagIds);
        categoryService.getExistingCategories(categoryIds);

        LocalDateTime now = LocalDateTime.now();
        PostEntity entity = new PostEntity();
        entity.setTitle(title);
        entity.setSlug(slug);
        entity.setMarkdownContent(markdownContent);
        entity.setSummary(normalizeOptionalText(request.summary()));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        postMapper.insert(entity);

        saveTagRelations(entity.getId(), tagIds);
        saveCategoryRelations(entity.getId(), categoryIds);
        return getPostDetail(entity.getId());
    }

    @Transactional
    public PostDetailResponse updatePost(Long postId, UpdatePostRequest request) {
        PostEntity existing = requirePost(postId);
        String title = SlugResolver.requireText(request.title(), "post title");
        String slug = SlugResolver.resolve(title, request.slug(), "post");
        String markdownContent = SlugResolver.requireText(request.markdownContent(), "markdown content");
        validatePostUniqueness(slug, postId);

        List<Long> tagIds = distinctIds(request.tagIds());
        List<Long> categoryIds = distinctIds(request.categoryIds());
        tagService.getExistingTags(tagIds);
        categoryService.getExistingCategories(categoryIds);

        postRenderService.evict(existing);

        existing.setTitle(title);
        existing.setSlug(slug);
        existing.setMarkdownContent(markdownContent);
        existing.setSummary(normalizeOptionalText(request.summary()));
        existing.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(existing);

        replaceTagRelations(postId, tagIds);
        replaceCategoryRelations(postId, categoryIds);
        return getPostDetail(postId);
    }

    public PagedResponse<PostSummaryResponse> listPosts(PostPageQuery query) {
        validatePageQuery(query);

        Page<PostEntity> pageRequest = Page.of(query.page(), query.size());
        Page<PostEntity> pageResult = postMapper.selectPage(pageRequest, new LambdaQueryWrapper<PostEntity>()
                .orderByDesc(PostEntity::getUpdateTime)
                .orderByDesc(PostEntity::getId));

        long totalPages = pageResult.getTotal() == 0
                ? 0
                : (pageResult.getTotal() + pageResult.getSize() - 1) / pageResult.getSize();

        return new PagedResponse<>(
                pageResult.getCurrent(),
                pageResult.getSize(),
                pageResult.getTotal(),
                totalPages,
                pageResult.getRecords().stream()
                        .map(PostConverter::toSummary)
                        .toList()
        );
    }

    public PostDetailResponse getPostDetail(Long postId) {
        PostEntity entity = requirePost(postId);

        String renderedHtml = postRenderService.getRenderedHtml(entity);
        List<TaxonomyResponse> tags = loadTags(postId);
        List<TaxonomyResponse> categories = loadCategories(postId);
        return PostConverter.toDetail(entity, renderedHtml, tags, categories);
    }

    private void validatePageQuery(PostPageQuery query) {
        if (query.page() < 1) {
            throw new BizException(ResultCode.BIZ_ERROR, "page must be greater than 0");
        }
        if (query.size() < 1) {
            throw new BizException(ResultCode.BIZ_ERROR, "page size must be greater than 0");
        }
        if (query.size() > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.BIZ_ERROR, "page size must not exceed " + MAX_PAGE_SIZE);
        }
    }

    private void validatePostUniqueness(String slug) {
        validatePostUniqueness(slug, null);
    }

    private void validatePostUniqueness(String slug, Long excludedPostId) {
        LambdaQueryWrapper<PostEntity> query = new LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getSlug, slug);
        if (excludedPostId != null) {
            query.ne(PostEntity::getId, excludedPostId);
        }

        Long duplicates = postMapper.selectCount(query);
        if (duplicates != null && duplicates > 0) {
            throw new BizException(ResultCode.BIZ_ERROR, "post slug already exists");
        }
    }

    private void saveTagRelations(Long postId, List<Long> tagIds) {
        for (Long tagId : tagIds) {
            PostTagEntity entity = new PostTagEntity();
            entity.setPostId(postId);
            entity.setTagId(tagId);
            postTagMapper.insert(entity);
        }
    }

    private void replaceTagRelations(Long postId, List<Long> tagIds) {
        postTagMapper.delete(new LambdaQueryWrapper<PostTagEntity>()
                .eq(PostTagEntity::getPostId, postId));
        saveTagRelations(postId, tagIds);
    }

    private void saveCategoryRelations(Long postId, List<Long> categoryIds) {
        for (Long categoryId : categoryIds) {
            PostCategoryEntity entity = new PostCategoryEntity();
            entity.setPostId(postId);
            entity.setCategoryId(categoryId);
            postCategoryMapper.insert(entity);
        }
    }

    private void replaceCategoryRelations(Long postId, List<Long> categoryIds) {
        postCategoryMapper.delete(new LambdaQueryWrapper<PostCategoryEntity>()
                .eq(PostCategoryEntity::getPostId, postId));
        saveCategoryRelations(postId, categoryIds);
    }

    private List<TaxonomyResponse> loadTags(Long postId) {
        List<Long> tagIds = postTagMapper.selectList(new LambdaQueryWrapper<PostTagEntity>()
                        .eq(PostTagEntity::getPostId, postId))
                .stream()
                .map(PostTagEntity::getTagId)
                .toList();

        List<TagEntity> tags = tagService.getExistingTags(tagIds);
        return tags.stream()
                .map(TaxonomyConverter::toResponse)
                .toList();
    }

    private List<TaxonomyResponse> loadCategories(Long postId) {
        List<Long> categoryIds = postCategoryMapper.selectList(new LambdaQueryWrapper<PostCategoryEntity>()
                        .eq(PostCategoryEntity::getPostId, postId))
                .stream()
                .map(PostCategoryEntity::getCategoryId)
                .toList();

        List<CategoryEntity> categories = categoryService.getExistingCategories(categoryIds);
        return categories.stream()
                .map(TaxonomyConverter::toResponse)
                .toList();
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PostEntity requirePost(Long postId) {
        if (postId == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "post id must not be null");
        }

        PostEntity entity = postMapper.selectById(postId);
        if (entity == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "post not found");
        }

        return entity;
    }
}
