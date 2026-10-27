package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

// jakarta.servlet replaces javax.servlet (Spring Boot 3.x / Jakarta EE 10)
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // Inventory service URL — externalised to environment variable and enforces HTTPS
    // (fixes cr-java-0088, cr-java-0021). Hardcoded plain-HTTP URL has been removed.
    @Value("${app.inventory.endpoint:https://inventory-svc.internal/rooms}")
    private String inventoryEndpoint;

    // Report base path — externalised to environment variable for container compatibility
    // (fixes czr-java-001). Hardcoded /var/legacy/reports path has been removed.
    @Value("${app.report.base-path:/tmp/reports/}")
    private String reportBasePath;

    // NOTE (cr-java-0067): The static in-memory bookingCache has been removed.
    // Instance-local caches break horizontal scaling (AWS ALB / ECS / EKS).
    // Replace with a distributed cache (e.g. Amazon ElastiCache / Redis) if caching
    // is required. Booking lookups now go directly to the database via BookingService.

    /**
     * Creates a new booking and returns the confirmed booking details.
     *
     * <p>Session state is no longer used to store booking data (fixes cr-java-0065).
     * HTTP sessions are instance-local and invisible to other nodes behind an AWS ALB.
     * Booking state is persisted to the database and retrieved by ID on subsequent requests.</p>
     *
     * @param guestName guest's full name
     * @param roomType  room category (STANDARD, DELUXE, SUITE, VILLA)
     * @param checkIn   check-in date string
     * @param checkOut  check-out date string
     * @return confirmed booking details
     */
    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Session state removed (fixes cr-java-0065): booking is persisted to the database.
        // Clients should use the returned bookingId for subsequent status lookups.

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * Returns the current status and details of a booking by its ID.
     *
     * <p>Booking details are retrieved from the database rather than from HTTP session
     * state (fixes cr-java-0065), ensuring consistent results across all cluster nodes.</p>
     *
     * @param bookingId the unique booking identifier
     * @return booking status and details
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(@PathVariable String bookingId) {
        // Session attribute removed (fixes cr-java-0065): state is read from the database.
        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * Checks room availability for the requested room type.
     *
     * <p>The inventory service URL is injected from configuration and uses HTTPS
     * (fixes cr-java-0088). The hardcoded plain-HTTP URL has been removed.</p>
     *
     * @param roomType the room category to check
     * @return availability status and inventory endpoint reference
     */
    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // HTTPS endpoint injected from configuration (fixes cr-java-0088, cr-java-0021)
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryEndpoint);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    /**
     * Returns the download path for a monthly booking report.
     *
     * <p>The report base path is resolved from the {@code app.report.base-path}
     * environment variable (fixes czr-java-001). The hardcoded /var/legacy/reports
     * path has been removed.</p>
     *
     * @param month the month identifier for the report
     * @return report path and generation status message
     */
    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Path resolved from environment variable (fixes czr-java-001)
        String reportPath = reportBasePath + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
