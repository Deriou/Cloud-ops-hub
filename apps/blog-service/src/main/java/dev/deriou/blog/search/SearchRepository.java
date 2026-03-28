package dev.deriou.blog.search;

public interface SearchRepository {

    SearchResultPage search(String normalizedKeyword, long offset, long limit);
}
