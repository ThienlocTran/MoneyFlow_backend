package com.moneyflowbackend.dashboard.controller;

import com.moneyflowbackend.dashboard.dto.DashboardCategoryResponse;
import com.moneyflowbackend.dashboard.dto.DashboardComparisonResponse;
import com.moneyflowbackend.dashboard.dto.DashboardJarResponse;
import com.moneyflowbackend.dashboard.dto.DashboardResponse;
import com.moneyflowbackend.dashboard.service.DashboardService;
import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "SAME_PERIOD") String comparisonMode) {
        DashboardResponse res = dashboardService.getDashboard(workspaceId, month, comparisonMode, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Dashboard loaded", res));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<DashboardResponse>> getMonthlySummary(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "SAME_PERIOD") String comparisonMode) {
        DashboardResponse res = dashboardService.getDashboard(workspaceId, month, comparisonMode, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Dashboard monthly loaded", res));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<DashboardCategoryResponse>>> getCategoryBreakdown(
            @PathVariable UUID workspaceId,
            @RequestParam int year,
            @RequestParam int month) {
        List<DashboardCategoryResponse> res = dashboardService.getCategoryBreakdown(workspaceId, year, month, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Lấy phân tích danh mục chi tiêu thành công", res));
    }

    @GetMapping("/jars")
    public ResponseEntity<ApiResponse<List<DashboardJarResponse>>> getJarBreakdown(
            @PathVariable UUID workspaceId,
            @RequestParam int year,
            @RequestParam int month) {
        List<DashboardJarResponse> res = dashboardService.getJarBreakdown(workspaceId, year, month, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Lấy phân tích 6 hũ thành công", res));
    }

    @GetMapping("/comparison")
    public ResponseEntity<ApiResponse<DashboardComparisonResponse>> getExpenseComparison(
            @PathVariable UUID workspaceId,
            @RequestParam int year,
            @RequestParam int month) {
        DashboardComparisonResponse res = dashboardService.getExpenseComparison(workspaceId, year, month, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Lấy so sánh chi tiêu với tháng trước thành công", res));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
