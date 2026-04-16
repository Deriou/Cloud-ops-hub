package dev.deriou.blog.controller;

import dev.deriou.blog.dto.importing.BatchImportNotesRequest;
import dev.deriou.blog.dto.importing.BatchImportNotesResponse;
import dev.deriou.blog.service.NoteImportService;
import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "笔记导入", description = "Obsidian 笔记批量导入接口")
@RestController
@RequestMapping("/api/v1/blog/import")
public class NoteImportController {

    private final NoteImportService noteImportService;

    public NoteImportController(NoteImportService noteImportService) {
        this.noteImportService = noteImportService;
    }

    @Operation(summary = "批量导入笔记", description = "按 noteId 批量导入或更新 Obsidian 笔记")
    @PostMapping("/notes:batch")
    public ApiResponse<BatchImportNotesResponse> importNotes(@RequestBody BatchImportNotesRequest request) {
        return ApiResponse.success(noteImportService.importNotes(request));
    }
}
