package dev.deriou.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.deriou.blog.domain.entity.TagEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper extends BaseMapper<TagEntity> {
}
