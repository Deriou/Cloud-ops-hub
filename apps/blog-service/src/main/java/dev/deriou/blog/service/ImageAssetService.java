package dev.deriou.blog.service;

import dev.deriou.blog.config.BlogAssetProperties;
import dev.deriou.blog.dto.asset.ImageAssetResponse;
import dev.deriou.common.api.ResultCode;
import dev.deriou.common.exception.BizException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageAssetService {

    private final BlogAssetProperties assetProperties;

    public ImageAssetService(BlogAssetProperties assetProperties) {
        this.assetProperties = assetProperties;
    }

    public ImageAssetResponse storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BIZ_ERROR, "image file must not be empty");
        }
        if (!isLocalStorageMode()) {
            throw new BizException(ResultCode.BIZ_ERROR, "unsupported asset storage mode");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BizException(ResultCode.BIZ_ERROR, "only image uploads are supported");
        }

        try {
            byte[] bytes = file.getBytes();
            String key = buildKey(bytes, file.getOriginalFilename(), contentType);
            Path root = resolveRoot();
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) {
                throw new BizException(ResultCode.BIZ_ERROR, "invalid image key");
            }

            Files.createDirectories(root);
            if (!Files.exists(target)) {
                Files.write(target, bytes);
            }
            return new ImageAssetResponse(key, buildPublicPath(key));
        } catch (IOException ex) {
            throw new BizException(ResultCode.BIZ_ERROR, "failed to store image");
        }
    }

    public Resource loadImage(String key) {
        Path path = resolveAssetPath(key);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            return null;
        }
        return new FileSystemResource(path);
    }

    public MediaType resolveMediaType(String key) {
        return MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    public String buildPublicPath(String key) {
        return "/api/v1/blog/assets/images/" + key;
    }

    private Path resolveAssetPath(String key) {
        if (key == null || key.isBlank() || key.contains("/") || key.contains("\\") || key.contains("..")) {
            throw new BizException(ResultCode.BIZ_ERROR, "invalid image key");
        }

        Path root = resolveRoot();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.BIZ_ERROR, "invalid image key");
        }
        return target;
    }

    private Path resolveRoot() {
        String localRoot = assetProperties.getLocalRoot();
        if (localRoot == null || localRoot.isBlank()) {
            throw new BizException(ResultCode.BIZ_ERROR, "asset local root must not be blank");
        }
        return Path.of(localRoot).toAbsolutePath().normalize();
    }

    private boolean isLocalStorageMode() {
        return assetProperties.getStorageMode() == null
                || assetProperties.getStorageMode().isBlank()
                || "local".equalsIgnoreCase(assetProperties.getStorageMode());
    }

    private String buildKey(byte[] bytes, String originalFilename, String contentType) {
        String extension = resolveExtension(originalFilename, contentType);
        return "sha256-" + sha256(bytes) + "." + extension;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int lastDot = originalFilename.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < originalFilename.length() - 1) {
                return originalFilename.substring(lastDot + 1).trim().toLowerCase();
            }
        }

        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> "bin";
        };
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
