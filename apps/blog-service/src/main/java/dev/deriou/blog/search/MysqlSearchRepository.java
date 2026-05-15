package dev.deriou.blog.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "blog.search.mode", havingValue = "mysql", matchIfMissing = true)
public class MysqlSearchRepository implements SearchRepository {

    private static final RowMapper<SearchHit> SEARCH_HIT_ROW_MAPPER = new RowMapper<>() {
        @Override
        public SearchHit mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp updateTime = rs.getTimestamp("update_time");
            return new SearchHit(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("slug"),
                    rs.getString("summary"),
                    updateTime != null ? updateTime.toLocalDateTime() : null,
                    rs.getDouble("score")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public MysqlSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SearchResultPage search(String normalizedKeyword, long offset, long limit) {
        String likeKeyword = "%" + normalizedKeyword.toLowerCase() + "%";

        String countSql = """
                select count(*)
                from post p
                where p.status = 'published'
                  and (
                      match(p.title, p.markdown_content) against (? in natural language mode)
                      or exists (
                          select 1
                          from post_tag pt
                          inner join tag t on t.id = pt.tag_id
                          where pt.post_id = p.id
                            and (
                                lower(t.name) like ?
                                or lower(t.slug) like ?
                            )
                      )
                      or exists (
                          select 1
                          from post_category pc
                          inner join category c on c.id = pc.category_id
                          where pc.post_id = p.id
                            and (
                                lower(c.name) like ?
                                or lower(c.slug) like ?
                            )
                      )
                  )
                """;

        String searchSql = """
                select p.id,
                       p.title,
                       p.slug,
                       p.summary,
                       p.update_time,
                       match(p.title, p.markdown_content) against (? in natural language mode)
                           + case
                                 when lower(p.title) = lower(?) then 100
                                 when lower(p.title) like concat('%', lower(?), '%') then 50
                                 else 0
                             end
                           + case
                                 when exists (
                                     select 1
                                     from post_tag pt
                                     inner join tag t on t.id = pt.tag_id
                                     where pt.post_id = p.id
                                       and lower(t.name) = lower(?)
                                 ) then 80
                                 when exists (
                                     select 1
                                     from post_tag pt
                                     inner join tag t on t.id = pt.tag_id
                                     where pt.post_id = p.id
                                       and (
                                           lower(t.name) like ?
                                           or lower(t.slug) like ?
                                       )
                                 ) then 60
                                 else 0
                             end
                           + case
                                 when exists (
                                     select 1
                                     from post_category pc
                                     inner join category c on c.id = pc.category_id
                                     where pc.post_id = p.id
                                       and lower(c.name) = lower(?)
                                 ) then 80
                                 when exists (
                                     select 1
                                     from post_category pc
                                     inner join category c on c.id = pc.category_id
                                     where pc.post_id = p.id
                                       and (
                                           lower(c.name) like ?
                                           or lower(c.slug) like ?
                                       )
                                 ) then 60
                                 else 0
                             end as score
                from post p
                where p.status = 'published'
                  and (
                      match(p.title, p.markdown_content) against (? in natural language mode)
                      or exists (
                          select 1
                          from post_tag pt
                          inner join tag t on t.id = pt.tag_id
                          where pt.post_id = p.id
                            and (
                                lower(t.name) like ?
                                or lower(t.slug) like ?
                            )
                      )
                      or exists (
                          select 1
                          from post_category pc
                          inner join category c on c.id = pc.category_id
                          where pc.post_id = p.id
                            and (
                                lower(c.name) like ?
                                or lower(c.slug) like ?
                            )
                      )
                  )
                order by score desc, p.update_time desc, p.id desc
                limit ? offset ?
                """;

        Long total = jdbcTemplate.queryForObject(
                countSql,
                Long.class,
                normalizedKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword
        );
        List<SearchHit> hits = jdbcTemplate.query(
                searchSql,
                SEARCH_HIT_ROW_MAPPER,
                normalizedKeyword,
                normalizedKeyword,
                normalizedKeyword,
                normalizedKeyword,
                likeKeyword,
                likeKeyword,
                normalizedKeyword,
                likeKeyword,
                likeKeyword,
                normalizedKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                limit,
                offset
        );
        return new SearchResultPage(total != null ? total : 0L, hits);
    }
}
