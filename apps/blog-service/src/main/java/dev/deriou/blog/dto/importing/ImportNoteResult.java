package dev.deriou.blog.dto.importing;

public record ImportNoteResult(
        String noteId,
        Long postId,
        String action,
        String message
) {
}
