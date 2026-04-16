package dev.deriou.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.deriou.blog.converter.TaxonomyConverter;
import dev.deriou.blog.domain.entity.CategoryEntity;
import dev.deriou.blog.dto.taxonomy.TaxonomyCreateRequest;
import dev.deriou.blog.dto.taxonomy.TaxonomyResponse;
import dev.deriou.blog.mapper.CategoryMapper;
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
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    public TaxonomyResponse createCategory(TaxonomyCreateRequest request) {
        String name = SlugResolver.requireText(request.name(), "category name");
        String slug = SlugResolver.resolve(name, request.slug(), "category");
        validateUniqueness(name, slug);

        LocalDateTime now = LocalDateTime.now();
        CategoryEntity entity = new CategoryEntity();
        entity.setName(name);
        entity.setSlug(slug);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        categoryMapper.insert(entity);
        return TaxonomyConverter.toResponse(entity);
    }

    public List<TaxonomyResponse> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                        .orderByAsc(CategoryEntity::getName, CategoryEntity::getId))
                .stream()
                .map(TaxonomyConverter::toResponse)
                .toList();
    }

    public List<CategoryEntity> getExistingCategories(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }

        List<CategoryEntity> categories = categoryMapper.selectBatchIds(categoryIds);
        if (categories.size() != categoryIds.stream().distinct().count()) {
            throw new BizException(ResultCode.BIZ_ERROR, "some categories do not exist");
        }

        return categories.stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
    }

    @Transactional
    public List<CategoryEntity> resolveCategoriesByNames(List<String> categoryNames) {
        List<String> normalizedNames = normalizeNames(categoryNames);
        if (normalizedNames.isEmpty()) {
            return List.of();
        }

        Map<String, CategoryEntity> existingByName = new LinkedHashMap<>();
        Map<String, CategoryEntity> existingBySlug = new LinkedHashMap<>();
        for (CategoryEntity entity : categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>())) {
            existingByName.put(entity.getName().trim().toLowerCase(Locale.ROOT), entity);
            existingBySlug.put(entity.getSlug(), entity);
        }

        LocalDateTime now = LocalDateTime.now();
        for (String name : normalizedNames) {
            String slug = SlugResolver.resolve(name, null, "category");
            if (existingByName.containsKey(name.toLowerCase(Locale.ROOT)) || existingBySlug.containsKey(slug)) {
                continue;
            }

            CategoryEntity entity = new CategoryEntity();
            entity.setName(name);
            entity.setSlug(slug);
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            categoryMapper.insert(entity);
            existingByName.put(name.toLowerCase(Locale.ROOT), entity);
            existingBySlug.put(slug, entity);
        }

        return normalizedNames.stream()
                .map(name -> {
                    CategoryEntity entity = existingByName.get(name.toLowerCase(Locale.ROOT));
                    if (entity == null) {
                        throw new BizException(ResultCode.BIZ_ERROR, "category not found after resolve");
                    }
                    return entity;
                })
                .distinct()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
    }

    private void validateUniqueness(String name, String slug) {
        Long duplicates = categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getName, name)
                .or()
                .eq(CategoryEntity::getSlug, slug));
        if (duplicates != null && duplicates > 0) {
            throw new BizException(ResultCode.BIZ_ERROR, "category already exists");
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
