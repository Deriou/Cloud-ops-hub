package dev.deriou.blog.dto.importing;

import java.util.List;

public record BatchImportNotesResponse(
        long created,
        long updated,
        long skipped,
        long failed,
        List<ImportNoteResult> results
) {
}
