package dev.deriou.blog.domain.entity;

public final class PostStatus {

    public static final String DRAFT = "draft";
    public static final String PUBLISHED = "published";
    public static final String ARCHIVED = "archived";

    private PostStatus() {
    }

    public static boolean isPublished(String status) {
        return PUBLISHED.equalsIgnoreCase(status);
    }
}
