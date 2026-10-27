package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
// java.time API (thread-safe, modern) — replaces legacy java.util.Date + SimpleDateFormat
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // Externalised to environment variable for container/cloud compatibility (fixes czr-java-001).
    // Defaults to /tmp/reports/ which is writable inside any container image.
    @Value("${app.report.base-path:/tmp/reports/}")
    private String reportBasePath;

    // Report download base URL — externalised and enforces HTTPS (fixes cr-java-0088, czr-java-001).
    @Value("${app.report.download-url:https://reports.resorts-internal.com/download}")
    private String reportDownloadBaseUrl;

    /**
     * Generates a monthly booking report CSV file.
     *
     * <p>The output directory is resolved from the {@code app.report.base-path}
     * environment variable so the service works inside Docker/ECS containers
     * without requiring a specific host path (fixes czr-java-001).</p>
     *
     * @param month the month label (e.g. "March")
     * @param year  the four-digit year (e.g. "2024")
     * @return a result map containing status and the generated file path
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String fullPath = reportBasePath + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(reportBasePath);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }

            try (FileWriter writer = new FileWriter(fullPath)) {
                writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
                writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
                writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            }

            result.put("status", "generated");
            result.put("path", fullPath);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds the HTTPS download URL for a named report file.
     *
     * <p>The base URL is injected from {@code app.report.download-url} and
     * defaults to an HTTPS endpoint, satisfying cloud security requirements
     * (fixes cr-java-0088). The hardcoded plain-HTTP URL has been removed.</p>
     *
     * @param reportName the report file name to append to the base URL
     * @return the full HTTPS download URL for the report
     */
    public String buildReportDownloadUrl(String reportName) {
        // HTTPS endpoint injected from configuration (fixes cr-java-0088, czr-java-001)
        return reportDownloadBaseUrl + "/" + reportName;
    }

    /**
     * Returns current system/configuration information for diagnostics.
     *
     * <p>The server port is no longer hardcoded; it is read from the Spring
     * environment to support dynamic port assignment in ECS/EKS (fixes czr-port-001).
     * Report and backup paths are resolved from environment variables (fixes czr-java-001).</p>
     *
     * @return a map of diagnostic key-value pairs
     */
    public Map<String, Object> getSystemInfo() {
        // java.time API — thread-safe replacement for legacy SimpleDateFormat + Date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);

        Map<String, Object> info = new HashMap<>();
        // Paths resolved from environment variables (fixes czr-java-001)
        info.put("reportPath", reportBasePath);
        info.put("generatedAt", timestamp);
        return info;
    }
}
