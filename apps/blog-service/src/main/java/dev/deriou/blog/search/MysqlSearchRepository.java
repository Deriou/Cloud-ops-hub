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
        String countSql = """
                select count(*)
                from post
                where match(title, markdown_content) against (? in natural language mode)
                """;

        String searchSql = """
                select id,
                       title,
                       slug,
                       summary,
                       update_time,
                       match(title, markdown_content) against (? in natural language mode)
                           + case
                                 when lower(title) = lower(?) then 100
                                 when lower(title) like concat('%', lower(?), '%') then 50
                                 else 0
                             end as score
                from post
                where match(title, markdown_content) against (? in natural language mode)
                order by score desc, update_time desc, id desc
                limit ? offset ?
                """;

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, normalizedKeyword);
        List<SearchHit> hits = jdbcTemplate.query(
                searchSql,
                SEARCH_HIT_ROW_MAPPER,
                normalizedKeyword,
                normalizedKeyword,
                normalizedKeyword,
                normalizedKeyword,
                limit,
                offset
        );
        return new SearchResultPage(total != null ? total : 0L, hits);
    }
}
