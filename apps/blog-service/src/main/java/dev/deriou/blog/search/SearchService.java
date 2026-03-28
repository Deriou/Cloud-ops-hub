package dev.deriou.blog.search;

import dev.deriou.blog.dto.common.PagedResponse;
import dev.deriou.blog.dto.post.PostSummaryResponse;
import dev.deriou.common.api.ResultCode;
import dev.deriou.common.exception.BizException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final long MAX_PAGE_SIZE = 50L;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final SearchRepository searchRepository;

    public SearchService(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public PagedResponse<PostSummaryResponse> search(SearchQuery query) {
        String normalizedKeyword = validateAndNormalizeKeyword(query.keyword());
        validatePageQuery(query.pageNo(), query.pageSize());

        long offset = (query.pageNo() - 1) * query.pageSize();
        SearchResultPage resultPage = searchRepository.search(normalizedKeyword, offset, query.pageSize());
        long totalPages = resultPage.total() == 0
                ? 0
                : (resultPage.total() + query.pageSize() - 1) / query.pageSize();

        List<PostSummaryResponse> records = resultPage.hits().stream()
                .map(hit -> new PostSummaryResponse(
                        hit.id(),
                        hit.title(),
                        hit.slug(),
                        hit.summary(),
                        hit.updateTime()
                ))
                .toList();

        return new PagedResponse<>(query.pageNo(), query.pageSize(), resultPage.total(), totalPages, records);
    }

    private String validateAndNormalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BizException(ResultCode.BIZ_ERROR, "keyword must not be blank");
        }

        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new BizException(ResultCode.BIZ_ERROR, "keyword length must not exceed " + MAX_KEYWORD_LENGTH);
        }

        return normalized;
    }

    private void validatePageQuery(long pageNo, long pageSize) {
        if (pageNo < 1) {
            throw new BizException(ResultCode.BIZ_ERROR, "pageNo must be greater than 0");
        }
        if (pageSize < 1) {
            throw new BizException(ResultCode.BIZ_ERROR, "pageSize must be greater than 0");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.BIZ_ERROR, "pageSize must not exceed " + MAX_PAGE_SIZE);
        }
    }
}
