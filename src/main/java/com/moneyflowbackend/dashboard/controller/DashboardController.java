package com.moneyflowbackend.dashboard.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.dashboard.dto.*;
import com.moneyflowbackend.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        DashboardResponse res = dashboardService.getDashboard(workspaceId, month, comparisonMode, userId);
        return ResponseEntity.ok(ApiResponse.ok("Dashboard loaded", res));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<DashboardMonthlyResponse>> getMonthlySummary(
            @PathVariable UUID workspaceId,
            @RequestParam int year,
            @RequestParam int month) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        DashboardMonthlyResponse res = dashboardService.getMonthlySummary(workspaceId, year, month, userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy tóm tắt tháng thành công", res));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<DashboardCategoryResponse>>> getCategoryBreakdown(
            @PathVariable UUID workspaceId,
            @RequestParam int year,
            @RequestParam int month) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        List<DashboardCategoryResponse> res = dashboardService.getCategoryBreakdown(workspaceId, year, month, userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy phân tích danh mục chi tiêu thành công", res));
    }

    @GetMapping("/jars")
    public ResponseEntity<ApiResponse<List<DashboardJarResponse>>> getJarBreakdown(
            @PathVariable UUID workspaceId,
            @RequestParam int year,
            @RequestParam int month) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        List<DashboardJarResponse> res = dashboardService.getJarBreakdown(workspaceId, year, month, userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy phân tích 6 hũ thành công", res));
    }

    @GetMapping("/comparison")
    public ResponseEntity<ApiResponse<DashboardComparisonResponse>> getExpenseComparison(
            @PathVariable UUID workspaceId,
            @RequestParam int year,
            @RequestParam int month) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        DashboardComparisonResponse res = dashboardService.getExpenseComparison(workspaceId, year, month, userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy so sánh chi tiêu với tháng trước thành công", res));
    }
}
