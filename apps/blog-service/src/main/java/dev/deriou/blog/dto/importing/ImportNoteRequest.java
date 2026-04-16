package dev.deriou.blog.dto.importing;

import java.util.List;

public record ImportNoteRequest(
        String noteId,
        String sourcePath,
        String title,
        String summary,
        String markdownContent,
        List<String> tags,
        List<String> categories,
        String createdAt,
        String contentHash,
        boolean publish
) {
}
