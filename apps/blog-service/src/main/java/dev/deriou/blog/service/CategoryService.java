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
import java.util.List;
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

    private void validateUniqueness(String name, String slug) {
        Long duplicates = categoryMapper.selectCount(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getName, name)
                .or()
                .eq(CategoryEntity::getSlug, slug));
        if (duplicates != null && duplicates > 0) {
            throw new BizException(ResultCode.BIZ_ERROR, "category already exists");
        }
    }
}
