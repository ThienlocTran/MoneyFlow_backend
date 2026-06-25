package com.moneyflowbackend.common.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time runner to fix mojibake (double-encoded UTF-8) data in the database.
 * Uses stable columns (code, icon, wallet_type) as keys instead of corrupted name strings.
 * Safe to run multiple times — idempotent.
 */
@Component
@Order(1)
public class FixDBRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public FixDBRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        System.out.println("=========================================");
        System.out.println("[MoneyFlow] Fixing database mojibake...");

        int total = 0;

        // ── Fix Jars (match by code) ──
        total += update("UPDATE jars SET name = 'Thiết yếu' WHERE code = 'NEC' AND name != 'Thiết yếu'");
        total += update("UPDATE jars SET name = 'Tự do tài chính' WHERE code = 'FFA' AND name != 'Tự do tài chính'");
        total += update("UPDATE jars SET name = 'Tiết kiệm dài hạn' WHERE code = 'LTSS' AND name != 'Tiết kiệm dài hạn'");
        total += update("UPDATE jars SET name = 'Giáo dục' WHERE code = 'EDU' AND name != 'Giáo dục'");
        total += update("UPDATE jars SET name = 'Hưởng thụ' WHERE code = 'PLAY' AND name != 'Hưởng thụ'");
        total += update("UPDATE jars SET name = 'Cho đi' WHERE code = 'GIVE' AND name != 'Cho đi'");

        // ── Fix Categories (match by icon) ──
        // Income
        total += update("UPDATE categories SET name = 'Lương' WHERE icon = 'salary' AND name != 'Lương'");
        total += update("UPDATE categories SET name = 'Gia đình chu cấp' WHERE icon = 'family' AND category_type = 'INCOME' AND name != 'Gia đình chu cấp'");
        total += update("UPDATE categories SET name = 'Kinh doanh' WHERE icon = 'business' AND name != 'Kinh doanh'");
        total += update("UPDATE categories SET name = 'Thưởng' WHERE icon = 'bonus' AND name != 'Thưởng'");
        total += update("UPDATE categories SET name = 'Hoàn tiền' WHERE icon = 'refund' AND name != 'Hoàn tiền'");
        total += update("UPDATE categories SET name = 'Được tặng' WHERE icon = 'gift' AND name != 'Được tặng'");
        total += update("UPDATE categories SET name = 'Khác' WHERE icon = 'other' AND name != 'Khác'");

        // Expense - NEC
        total += update("UPDATE categories SET name = 'Ăn uống' WHERE icon = 'food' AND name != 'Ăn uống'");
        total += update("UPDATE categories SET name = 'Xăng xe' WHERE icon = 'car' AND name != 'Xăng xe'");
        total += update("UPDATE categories SET name = 'Gửi xe' WHERE icon = 'parking' AND name != 'Gửi xe'");
        total += update("UPDATE categories SET name = 'Đi chợ' WHERE icon = 'grocery' AND name != 'Đi chợ'");
        total += update("UPDATE categories SET name = 'Tiền trọ' WHERE icon = 'rent' AND name != 'Tiền trọ'");
        total += update("UPDATE categories SET name = 'Tiền điện' WHERE icon = 'electric' AND name != 'Tiền điện'");
        total += update("UPDATE categories SET name = 'Tiền nước' WHERE icon = 'water' AND name != 'Tiền nước'");
        total += update("UPDATE categories SET name = 'Internet/Wifi' WHERE icon = 'wifi' AND name != 'Internet/Wifi'");
        total += update("UPDATE categories SET name = 'Điện thoại/4G' WHERE icon = 'mobile' AND name != 'Điện thoại/4G'");
        total += update("UPDATE categories SET name = 'Y tế' WHERE icon = 'medical' AND name != 'Y tế'");
        total += update("UPDATE categories SET name = 'Đồ dùng trong nhà' WHERE icon = 'household' AND name != 'Đồ dùng trong nhà'");

        // Expense - FFA
        total += update("UPDATE categories SET name = 'Đầu tư' WHERE icon = 'invest' AND name != 'Đầu tư'");
        total += update("UPDATE categories SET name = 'Vốn kinh doanh' WHERE icon = 'capital' AND name != 'Vốn kinh doanh'");
        total += update("UPDATE categories SET name = 'Tài sản' WHERE icon = 'asset' AND name != 'Tài sản'");

        // Expense - LTSS
        total += update("UPDATE categories SET name = 'Quỹ khẩn cấp' WHERE icon = 'emergency' AND name != 'Quỹ khẩn cấp'");
        total += update("UPDATE categories SET name = 'Mục tiêu tiết kiệm' WHERE icon = 'saving-goal' AND name != 'Mục tiêu tiết kiệm'");
        total += update("UPDATE categories SET name = 'Mua sắm lớn' WHERE icon = 'big-buy' AND name != 'Mua sắm lớn'");

        // Expense - EDU
        total += update("UPDATE categories SET name = 'Học phí' WHERE icon = 'tuition' AND name != 'Học phí'");
        total += update("UPDATE categories SET name = 'Sách và tài liệu' WHERE icon = 'books' AND name != 'Sách và tài liệu'");
        total += update("UPDATE categories SET name = 'Khóa học' WHERE icon = 'courses' AND name != 'Khóa học'");
        total += update("UPDATE categories SET name = 'Học tiếng Anh' WHERE icon = 'english' AND name != 'Học tiếng Anh'");
        total += update("UPDATE categories SET name = 'Học lập trình' WHERE icon = 'coding' AND name != 'Học lập trình'");

        // Expense - PLAY
        total += update("UPDATE categories SET name = 'Cafe' WHERE icon = 'coffee' AND name != 'Cafe'");
        total += update("UPDATE categories SET name = 'Ăn ngoài' WHERE icon = 'dining' AND name != 'Ăn ngoài'");
        total += update("UPDATE categories SET name = 'Đi chơi' WHERE icon = 'hangout' AND name != 'Đi chơi'");
        total += update("UPDATE categories SET name = 'Xem phim' WHERE icon = 'movies' AND name != 'Xem phim'");
        total += update("UPDATE categories SET name = 'Game' WHERE icon = 'games' AND name != 'Game'");
        total += update("UPDATE categories SET name = 'Quần áo' WHERE icon = 'clothes' AND name != 'Quần áo'");
        total += update("UPDATE categories SET name = 'Mỹ phẩm' WHERE icon = 'makeup' AND name != 'Mỹ phẩm'");
        total += update("UPDATE categories SET name = 'Du lịch' WHERE icon = 'travel' AND name != 'Du lịch'");

        // Expense - GIVE
        total += update("UPDATE categories SET name = 'Gia đình' WHERE icon = 'family-help' AND name != 'Gia đình'");
        total += update("UPDATE categories SET name = 'Quà tặng' WHERE icon = 'gifts' AND name != 'Quà tặng'");
        total += update("UPDATE categories SET name = 'Từ thiện' WHERE icon = 'charity' AND name != 'Từ thiện'");
        total += update("UPDATE categories SET name = 'Lì xì' WHERE icon = 'lixi' AND name != 'Lì xì'");
        total += update("UPDATE categories SET name = 'Hỗ trợ người khác' WHERE icon = 'support' AND name != 'Hỗ trợ người khác'");

        // ── Fix Wallets (match by wallet_type for default wallets) ──
        total += update("UPDATE wallets SET name = 'Tiền mặt' WHERE wallet_type = 'CASH' AND name != 'Tiền mặt'");

        // ── Fix Workspace names ──
        // Use regex to detect mojibake pattern and replace
        total += update("UPDATE workspaces SET name = CONCAT('Tài chính cá nhân của ', " +
                "(SELECT full_name FROM users WHERE users.id = workspaces.created_by_user_id)) " +
                "WHERE workspace_type = 'PERSONAL' AND name NOT LIKE 'Tài chính cá nhân của%'");

        if (total > 0) {
            System.out.println("[MoneyFlow] Fixed " + total + " rows with mojibake data.");
        } else {
            System.out.println("[MoneyFlow] No mojibake found — database is clean.");
        }
        System.out.println("=========================================");
    }

    private int update(String sql) {
        try {
            return jdbcTemplate.update(sql);
        } catch (Exception e) {
            System.err.println("[MoneyFlow] WARN: " + e.getMessage() + " | SQL: " + sql);
            return 0;
        }
    }
}
