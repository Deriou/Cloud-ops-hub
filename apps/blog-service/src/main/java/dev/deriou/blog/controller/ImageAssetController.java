package dev.deriou.blog.controller;

import dev.deriou.blog.dto.asset.ImageAssetResponse;
import dev.deriou.blog.service.ImageAssetService;
import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "图片资源", description = "博客图片资源上传与读取接口")
@RestController
@RequestMapping("/api/v1/blog/assets/images")
public class ImageAssetController {

    private final ImageAssetService imageAssetService;

    public ImageAssetController(ImageAssetService imageAssetService) {
        this.imageAssetService = imageAssetService;
    }

    @Operation(summary = "上传图片资源", description = "上传图片到本地持久卷并返回公开访问路径")
    @PostMapping
    public ApiResponse<ImageAssetResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(imageAssetService.storeImage(file));
    }

    @Operation(summary = "读取图片资源", description = "按 key 返回图片资源内容")
    @GetMapping("/{key}")
    public ResponseEntity<Resource> getImage(@PathVariable String key) {
        Resource resource = imageAssetService.loadImage(key);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(imageAssetService.resolveMediaType(key))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}
