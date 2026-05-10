package dev.deriou.gateway.ops;

import dev.deriou.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "运维摘要", description = "Ops 门户只读聚合摘要接口")
@RestController
@RequestMapping("/api/v1/ops/clusters")
public class OpsSummaryController {

    private final OpsSummaryService opsSummaryService;

    public OpsSummaryController(OpsSummaryService opsSummaryService) {
        this.opsSummaryService = opsSummaryService;
    }

    @Operation(summary = "查询集群摘要", description = "返回节点 CPU、节点内存和服务可用性摘要")
    @GetMapping("/summary")
    public ApiResponse<ClusterSummary> getClusterSummary() {
        return ApiResponse.success(opsSummaryService.getClusterSummary());
    }
}
