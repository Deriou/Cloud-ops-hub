package dev.deriou.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.deriou.blog.domain.entity.CategoryEntity;
import dev.deriou.blog.domain.entity.PostEntity;
import dev.deriou.blog.domain.entity.PostStatus;
import dev.deriou.blog.domain.entity.TagEntity;
import dev.deriou.blog.dto.importing.BatchImportNotesRequest;
import dev.deriou.blog.dto.importing.BatchImportNotesResponse;
import dev.deriou.blog.dto.importing.ImportNoteRequest;
import dev.deriou.blog.dto.importing.ImportNoteResult;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import dev.deriou.blog.mapper.PostMapper;
import dev.deriou.blog.render.PostRenderService;
import dev.deriou.common.api.ResultCode;
import dev.deriou.common.exception.BizException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NoteImportService {

    private final PostMapper postMapper;
    private final PostService postService;
    private final TagService tagService;
    private final CategoryService categoryService;
    private final PostRenderService postRenderService;
    private final TransactionTemplate transactionTemplate;

    public NoteImportService(
            PostMapper postMapper,
            PostService postService,
            TagService tagService,
            CategoryService categoryService,
            PostRenderService postRenderService,
            PlatformTransactionManager transactionManager
    ) {
        this.postMapper = postMapper;
        this.postService = postService;
        this.tagService = tagService;
        this.categoryService = categoryService;
        this.postRenderService = postRenderService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public BatchImportNotesResponse importNotes(BatchImportNotesRequest request) {
        List<ImportNoteRequest> notes = request == null || request.notes() == null ? List.of() : request.notes();
        if (notes.isEmpty()) {
            throw new BizException(ResultCode.BIZ_ERROR, "notes must not be empty");
        }

        long created = 0;
        long updated = 0;
        long skipped = 0;
        long failed = 0;
        List<ImportNoteResult> results = new java.util.ArrayList<>();

        for (ImportNoteRequest note : notes) {
            try {
                ImportNoteResult result = transactionTemplate.execute(status -> importSingle(note));
                if (result == null) {
                    throw new BizException(ResultCode.BIZ_ERROR, "import result must not be null");
                }
                results.add(result);
                switch (result.action()) {
                    case "created" -> created++;
                    case "updated" -> updated++;
                    case "skipped" -> skipped++;
                    default -> {
                    }
                }
            } catch (RuntimeException ex) {
                failed++;
                results.add(new ImportNoteResult(safeNoteId(note), null, "failed", ex.getMessage()));
            }
        }

        return new BatchImportNotesResponse(created, updated, skipped, failed, List.copyOf(results));
    }

    private ImportNoteResult importSingle(ImportNoteRequest request) {
        String noteId = SlugResolver.requireText(request.noteId(), "noteId");
        String title = SlugResolver.requireText(request.title(), "post title");
        String markdownContent = SlugResolver.requireText(request.markdownContent(), "markdown content");
        String summary = postService.normalizeOptionalText(request.summary());
        String sourcePath = postService.normalizeOptionalText(request.sourcePath());
        String contentHash = postService.normalizeOptionalText(request.contentHash());
        String status = request.publish() ? PostStatus.PUBLISHED : PostStatus.DRAFT;
        String internalSlug = buildInternalSlug(noteId);
        LocalDateTime now = LocalDateTime.now();

        PostEntity existing = postMapper.selectOne(new LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getNoteId, noteId)
                .last("limit 1"));

        List<TagEntity> tags = tagService.resolveTagsByNames(request.tags());
        List<CategoryEntity> categories = categoryService.resolveCategoriesByNames(request.categories());

        if (existing == null) {
            PostEntity entity = new PostEntity();
            entity.setNoteId(noteId);
            entity.setStatus(status);
            entity.setSourcePath(sourcePath);
            entity.setContentHash(contentHash);
            entity.setTitle(title);
            entity.setSlug(internalSlug);
            entity.setMarkdownContent(markdownContent);
            entity.setSummary(summary);
            entity.setCreateTime(resolveCreateTime(request.createdAt(), now));
            entity.setUpdateTime(now);
            entity.setLastSyncTime(now);
            postService.validatePostUniqueness(entity.getSlug());
            postMapper.insert(entity);

            postService.saveTagRelations(entity.getId(), tags.stream().map(TagEntity::getId).toList());
            postService.saveCategoryRelations(entity.getId(), categories.stream().map(CategoryEntity::getId).toList());
            return new ImportNoteResult(noteId, entity.getId(), "created", "post imported");
        }

        if (!hasChanges(existing, title, summary, markdownContent, status, sourcePath, contentHash, tags, categories)) {
            existing.setLastSyncTime(now);
            postMapper.updateById(existing);
            return new ImportNoteResult(noteId, existing.getId(), "skipped", "content unchanged");
        }

        postRenderService.evict(existing);
        existing.setStatus(status);
        existing.setSourcePath(sourcePath);
        existing.setContentHash(contentHash);
        existing.setTitle(title);
        existing.setSlug(internalSlug);
        existing.setMarkdownContent(markdownContent);
        existing.setSummary(summary);
        existing.setUpdateTime(now);
        existing.setLastSyncTime(now);
        postMapper.updateById(existing);

        postService.replaceTagRelations(existing.getId(), tags.stream().map(TagEntity::getId).toList());
        postService.replaceCategoryRelations(existing.getId(), categories.stream().map(CategoryEntity::getId).toList());
        return new ImportNoteResult(noteId, existing.getId(), "updated", "post updated");
    }

    private boolean hasChanges(
            PostEntity existing,
            String title,
            String summary,
            String markdownContent,
            String status,
            String sourcePath,
            String contentHash,
            List<TagEntity> tags,
            List<CategoryEntity> categories
    ) {
        if (!Objects.equals(existing.getTitle(), title)
                || !Objects.equals(existing.getSummary(), summary)
                || !Objects.equals(existing.getMarkdownContent(), markdownContent)
                || !Objects.equals(existing.getStatus(), status)
                || !Objects.equals(existing.getSourcePath(), sourcePath)
                || !Objects.equals(existing.getContentHash(), contentHash)) {
            return true;
        }

        Set<String> currentTagSlugs = new LinkedHashSet<>(postService.loadTags(existing.getId()).stream()
                .map(TaxonomyResponse::slug)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList());
        Set<String> targetTagSlugs = new LinkedHashSet<>(tags.stream()
                .map(TagEntity::getSlug)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList());
        if (!currentTagSlugs.equals(targetTagSlugs)) {
            return true;
        }

        Set<String> currentCategorySlugs = new LinkedHashSet<>(postService.loadCategories(existing.getId()).stream()
                .map(TaxonomyResponse::slug)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList());
        Set<String> targetCategorySlugs = new LinkedHashSet<>(categories.stream()
                .map(CategoryEntity::getSlug)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList());
        return !currentCategorySlugs.equals(targetCategorySlugs);
    }

    private LocalDateTime resolveCreateTime(String rawCreatedAt, LocalDateTime fallback) {
        String value = postService.normalizeOptionalText(rawCreatedAt);
        if (value == null) {
            return fallback;
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try local datetime below.
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try local date below.
        }

        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new BizException(ResultCode.BIZ_ERROR, "createdAt must be ISO-8601 datetime or date");
        }
    }

    private String buildInternalSlug(String noteId) {
        return SlugResolver.resolve("obsidian-" + noteId, null, "post");
    }

    private String safeNoteId(ImportNoteRequest request) {
        if (request == null || request.noteId() == null || request.noteId().isBlank()) {
            return "unknown";
        }
        return request.noteId().trim();
    }
}
