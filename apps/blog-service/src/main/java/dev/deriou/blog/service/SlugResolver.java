package dev.deriou.blog.service;

import dev.deriou.common.api.ResultCode;
import dev.deriou.common.exception.BizException;
import java.util.Locale;
import java.util.Objects;

final class SlugResolver {

    private SlugResolver() {
    }

    static String resolve(String rawName, String rawSlug, String fieldName) {
        String candidate = normalize(rawSlug);
        if (!candidate.isBlank()) {
            return candidate;
        }

        String generated = normalize(rawName);
        if (!generated.isBlank()) {
            return generated;
        }

        throw new BizException(ResultCode.BIZ_ERROR, fieldName + " slug must not be blank");
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BizException(ResultCode.BIZ_ERROR, fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }
}
