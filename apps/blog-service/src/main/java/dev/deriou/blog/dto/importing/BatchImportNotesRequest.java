package dev.deriou.blog.dto.importing;

import java.util.List;

public record BatchImportNotesRequest(
        List<ImportNoteRequest> notes
) {
}
