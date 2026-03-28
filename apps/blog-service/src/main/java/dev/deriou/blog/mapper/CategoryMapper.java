package dev.deriou.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.deriou.blog.domain.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
