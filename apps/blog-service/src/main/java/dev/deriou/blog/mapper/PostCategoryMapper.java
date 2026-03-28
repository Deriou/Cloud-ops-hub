package dev.deriou.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.deriou.blog.domain.entity.PostCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostCategoryMapper extends BaseMapper<PostCategoryEntity> {
}
