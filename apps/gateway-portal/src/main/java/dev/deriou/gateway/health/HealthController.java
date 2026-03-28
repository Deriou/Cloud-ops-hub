package dev.deriou.gateway.health;

import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "应用健康", description = "Gateway 子应用健康聚合查询接口")
@RestController
@RequestMapping("/api/v1/gateway/apps")
public class HealthController {

    private final HealthAggregationService healthAggregationService;

    public HealthController(HealthAggregationService healthAggregationService) {
        this.healthAggregationService = healthAggregationService;
    }

    @Operation(summary = "查询全部应用健康状态", description = "返回注册表内所有应用的聚合健康状态")
    @GetMapping("/health")
    public ApiResponse<List<AppHealth>> listAppHealths() {
        return ApiResponse.success(healthAggregationService.listAppHealths());
    }

    @Operation(summary = "查询单个应用健康状态", description = "根据 appKey 返回指定应用的聚合健康状态")
    @GetMapping("/{appKey}/health")
    public ApiResponse<AppHealth> getAppHealth(@PathVariable String appKey) {
        return ApiResponse.success(healthAggregationService.getAppHealth(appKey));
    }
}
