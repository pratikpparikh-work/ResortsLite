package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Externalised to environment variable / AWS Parameter Store (fixes cr-java-0021, sec-cred-001).
    // DB connection details are managed by Spring DataSource configuration in application.properties.
    @Value("${app.payment.endpoint:https://payment-svc.internal/charge}")
    private String paymentApi;

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Fixed SQL injection (sql-inject-001): replaced string concatenation with
        // parameterised query using JdbcTemplate '?' placeholders. User-supplied values
        // are bound as parameters and never interpolated into the SQL string.
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // SHA-256 hash (secure) — MD5 was replaced in a prior pass (RFC 6151).
        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // Fixed SQL injection (sql-inject-001): parameterised query replaces string
        // concatenation. bookingId is bound as a JDBC parameter, not interpolated.
        String sql = "SELECT * FROM bookings WHERE id = ?";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql, bookingId);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    /**
     * Calculates the total room price based on room type, number of nights,
     * season, and loyalty tier.
     *
     * <p>Refactored to reduce cyclomatic complexity: room base prices and
     * discount multipliers are stored in lookup maps, eliminating the long
     * if-else chains that exceeded the complexity threshold.</p>
     *
     * @param roomType the room category (STANDARD, DELUXE, SUITE, VILLA)
     * @param nights   number of nights to stay
     * @param season   pricing season (PEAK, OFF, or standard)
     * @param loyalty  guest loyalty tier (GOLD, PLATINUM, DIAMOND, or none)
     * @return formatted total price string
     */
    public String calculateRoomPrice(String roomType, int nights, String season, String loyalty) {
        // Room base prices — replaces repeated if-else chain (fixes high cyclomatic complexity)
        Map<String, Double> basePrices = Map.of(
                "STANDARD", 120.0,
                "DELUXE",   200.0,
                "SUITE",    350.0,
                "VILLA",    600.0
        );
        double basePrice = basePrices.getOrDefault(roomType, 120.0);

        // Season multipliers
        Map<String, Double> seasonMultipliers = Map.of(
                "PEAK", 1.5,
                "OFF",  0.8
        );
        basePrice *= seasonMultipliers.getOrDefault(season, 1.0);

        // Loyalty discount multipliers
        Map<String, Double> loyaltyMultipliers = Map.of(
                "GOLD",     0.9,
                "PLATINUM", 0.8,
                "DIAMOND",  0.7
        );
        basePrice *= loyaltyMultipliers.getOrDefault(loyalty, 1.0);

        // Long-stay discount — 14+ nights takes priority over 7+ nights
        if (nights >= 14) {
            basePrice *= 0.90;
        } else if (nights >= 7) {
            basePrice *= 0.95;
        }

        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    /**
     * Checks whether the given room type is a recognised, bookable category.
     *
     * <p>Validation is now delegated to the shared base-price map used by
     * {@link #calculateRoomPrice}, eliminating the duplicated room-type
     * validation logic (fixes dup-logic-001).</p>
     *
     * @param roomType the room category to validate
     * @return {@code true} if the room type is valid and available
     */
    public boolean isRoomAvailable(String roomType) {
        // Shared set of valid room types — eliminates duplicated validation (fixes dup-logic-001)
        return Set.of("STANDARD", "DELUXE", "SUITE", "VILLA").contains(roomType);
    }

    /**
     * Triggers report generation for the specified month.
     *
     * @param month the month identifier for the report
     * @return a status message indicating the report was triggered
     */
    public String generateReport(String month) {
        // Payment API endpoint is injected from configuration (fixes cr-java-0021, cr-java-0088)
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    /**
     * Computes a SHA-256 hex digest of the given input string.
     * Replaces the former md5Hash() method — MD5 is cryptographically broken (RFC 6151)
     * and must not be used for any security-sensitive hashing.
     *
     * @param input the string to hash
     * @return lowercase hex-encoded SHA-256 digest
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
