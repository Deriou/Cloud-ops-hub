package dev.deriou.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.deriou.blog.domain.entity.TagEntity;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper extends BaseMapper<TagEntity> {

    @Select("""
            SELECT DISTINCT t.id, t.name, t.slug, t.create_time, t.update_time
            FROM tag t
            INNER JOIN post_tag pt ON pt.tag_id = t.id
            INNER JOIN post p ON p.id = pt.post_id
            WHERE p.status = 'published'
            ORDER BY t.name ASC, t.id ASC
            """)
    List<TagEntity> selectPublishedTagsInUse();
}
