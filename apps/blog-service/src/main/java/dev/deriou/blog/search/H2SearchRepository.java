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
@ConditionalOnProperty(name = "blog.search.mode", havingValue = "h2")
public class H2SearchRepository implements SearchRepository {

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

    public H2SearchRepository(JdbcTemplate jdbcTemplate) {
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
                      lower(p.title) like ?
                      or lower(cast(p.markdown_content as varchar)) like ?
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
                       (
                           case
                               when lower(p.title) = ? then 300
                               when locate(?, lower(p.title)) > 0 then 200
                               else 0
                           end
                           +
                           case
                               when locate(?, lower(cast(p.markdown_content as varchar))) > 0 then 100
                               else 0
                           end
                           +
                           case
                               when exists (
                                   select 1
                                   from post_tag pt
                                   inner join tag t on t.id = pt.tag_id
                                   where pt.post_id = p.id
                                     and lower(t.name) = ?
                               ) then 180
                               when exists (
                                   select 1
                                   from post_tag pt
                                   inner join tag t on t.id = pt.tag_id
                                   where pt.post_id = p.id
                                     and (
                                         lower(t.name) like ?
                                         or lower(t.slug) like ?
                                     )
                               ) then 140
                               else 0
                           end
                           +
                           case
                               when exists (
                                   select 1
                                   from post_category pc
                                   inner join category c on c.id = pc.category_id
                                   where pc.post_id = p.id
                                     and lower(c.name) = ?
                               ) then 180
                               when exists (
                                   select 1
                                   from post_category pc
                                   inner join category c on c.id = pc.category_id
                                   where pc.post_id = p.id
                                     and (
                                         lower(c.name) like ?
                                         or lower(c.slug) like ?
                                     )
                               ) then 140
                               else 0
                           end
                       ) as score
                from post p
                where p.status = 'published'
                  and (
                      lower(p.title) like ?
                      or lower(cast(p.markdown_content as varchar)) like ?
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
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword
        );
        List<SearchHit> hits = jdbcTemplate.query(
                searchSql,
                SEARCH_HIT_ROW_MAPPER,
                normalizedKeyword.toLowerCase(),
                normalizedKeyword.toLowerCase(),
                normalizedKeyword.toLowerCase(),
                normalizedKeyword.toLowerCase(),
                likeKeyword,
                likeKeyword,
                normalizedKeyword.toLowerCase(),
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
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
