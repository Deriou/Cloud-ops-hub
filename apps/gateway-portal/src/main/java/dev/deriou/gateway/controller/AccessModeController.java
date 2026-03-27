package dev.deriou.gateway.controller;

import dev.deriou.common.auth.AuthInterceptor;
import dev.deriou.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gateway")
public class AccessModeController {

    @GetMapping("/access-mode")
    public ApiResponse<Map<String, String>> getAccessMode(HttpServletRequest request) {
        Object accessMode = request.getAttribute(AuthInterceptor.ACCESS_MODE_ATTRIBUTE);
        String mode = accessMode instanceof String value ? value : AuthInterceptor.GUEST_MODE;
        return ApiResponse.success(Map.of("mode", mode));
    }
}
