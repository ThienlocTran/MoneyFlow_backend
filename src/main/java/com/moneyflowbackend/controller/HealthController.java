package com.moneyflowbackend.controller;

import com.moneyflowbackend.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Liveness probe — confirms the process is running.
     * Does NOT access the database.
     */
    @GetMapping("/live")
    public ResponseEntity<ApiResponse<Map<String, String>>> live() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("application", "UP");
        return ResponseEntity.ok(ApiResponse.ok("MoneyFlow backend is alive", data));
    }

    /**
     * Readiness probe — confirms the application can serve traffic.
     * Checks database connectivity with a lightweight query.
     * Never exposes connection details, credentials, or error internals.
     */
    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, String>>> ready() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("application", "UP");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            if (rs.next()) {
                data.put("database", "UP");
            }
        } catch (Exception e) {
            data.put("database", "DOWN");
            return ResponseEntity
                .status(503)
                .body(ApiResponse.error("MoneyFlow backend is not ready"));
        }

        return ResponseEntity.ok(ApiResponse.ok("MoneyFlow backend is ready", data));
    }
}
