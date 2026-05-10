package dev.deriou.gateway.ops;

import dev.deriou.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/services")
public class OpsServiceHealthController {

    private final OpsServiceHealthService opsServiceHealthService;

    public OpsServiceHealthController(OpsServiceHealthService opsServiceHealthService) {
        this.opsServiceHealthService = opsServiceHealthService;
    }

    @GetMapping("/health")
    public ApiResponse<List<ServiceHealth>> listServiceHealth() {
        return ApiResponse.success(opsServiceHealthService.listServiceHealth());
    }
}
