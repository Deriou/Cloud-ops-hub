package dev.deriou.gateway.registry;

import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "应用注册表", description = "Gateway 子应用元数据查询接口")
@RestController
@RequestMapping("/api/v1/gateway/apps")
public class AppRegistryController {

    private final AppRegistryService appRegistryService;

    public AppRegistryController(AppRegistryService appRegistryService) {
        this.appRegistryService = appRegistryService;
    }

    @Operation(summary = "查询应用注册表", description = "返回前端导航可直接消费的子应用元数据列表")
    @GetMapping
    public ApiResponse<List<AppMeta>> listApps() {
        return ApiResponse.success(appRegistryService.listApps());
    }
}
