package dev.deriou.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.deriou.blog.converter.TaxonomyConverter;
import dev.deriou.blog.domain.entity.TagEntity;
import dev.deriou.blog.dto.taxonomy.TaxonomyCreateRequest;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import dev.deriou.blog.mapper.TagMapper;
import dev.deriou.common.api.ResultCode;
import dev.deriou.common.exception.BizException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    private final TagMapper tagMapper;

    public TagService(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Transactional
    public TaxonomyResponse createTag(TaxonomyCreateRequest request) {
        String name = SlugResolver.requireText(request.name(), "tag name");
        String slug = SlugResolver.resolve(name, request.slug(), "tag");
        validateUniqueness(name, slug);

        LocalDateTime now = LocalDateTime.now();
        TagEntity entity = new TagEntity();
        entity.setName(name);
        entity.setSlug(slug);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        tagMapper.insert(entity);
        return TaxonomyConverter.toResponse(entity);
    }

    public List<TaxonomyResponse> listTags() {
        return tagMapper.selectList(new LambdaQueryWrapper<TagEntity>()
                        .orderByAsc(TagEntity::getName, TagEntity::getId))
                .stream()
                .map(TaxonomyConverter::toResponse)
                .toList();
    }

    public List<TagEntity> getExistingTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }

        List<TagEntity> tags = tagMapper.selectBatchIds(tagIds);
        if (tags.size() != tagIds.stream().distinct().count()) {
            throw new BizException(ResultCode.BIZ_ERROR, "some tags do not exist");
        }

        return tags.stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
    }

    @Transactional
    public List<TagEntity> resolveTagsByNames(List<String> tagNames) {
        List<String> normalizedNames = normalizeNames(tagNames);
        if (normalizedNames.isEmpty()) {
            return List.of();
        }

        Map<String, TagEntity> existingByName = new LinkedHashMap<>();
        Map<String, TagEntity> existingBySlug = new LinkedHashMap<>();
        for (TagEntity entity : tagMapper.selectList(new LambdaQueryWrapper<TagEntity>())) {
            existingByName.put(entity.getName().trim().toLowerCase(Locale.ROOT), entity);
            existingBySlug.put(entity.getSlug(), entity);
        }

        LocalDateTime now = LocalDateTime.now();
        for (String name : normalizedNames) {
            String slug = SlugResolver.resolve(name, null, "tag");
            if (existingByName.containsKey(name.toLowerCase(Locale.ROOT)) || existingBySlug.containsKey(slug)) {
                continue;
            }

            TagEntity entity = new TagEntity();
            entity.setName(name);
            entity.setSlug(slug);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            tagMapper.insert(entity);
            existingByName.put(name.toLowerCase(Locale.ROOT), entity);
            existingBySlug.put(slug, entity);
        }

        return normalizedNames.stream()
                .map(name -> {
                    TagEntity entity = existingByName.get(name.toLowerCase(Locale.ROOT));
                    if (entity == null) {
                        throw new BizException(ResultCode.BIZ_ERROR, "tag not found after resolve");
                    }
                    return entity;
                })
                .distinct()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
    }

    private void validateUniqueness(String name, String slug) {
        Long duplicates = tagMapper.selectCount(new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getName, name)
                .or()
                .eq(TagEntity::getSlug, slug));
        if (duplicates != null && duplicates > 0) {
            throw new BizException(ResultCode.BIZ_ERROR, "tag already exists");
        }
    }

    private List<String> normalizeNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
