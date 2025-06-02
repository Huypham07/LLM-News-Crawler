package com.vdt.crawler.llm_parsing_service.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateTimeUtil {
    private static final Logger logger = LoggerFactory.getLogger(DateTimeUtil.class);
    private static final Pattern GMT_OFFSET_PATTERN = Pattern.compile("GMT([+-]\\d{1,2})");

    public static Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        String cleanDateStr = dateStr.trim();

        // Try different parsing strategies
        Instant result = tryParseWithTimezone(cleanDateStr);
        if (result != null) return result;

        result = tryParseAsLocalDateTime(cleanDateStr);
        if (result != null) return result;

        result = tryParseAsLocalDate(cleanDateStr);
        if (result != null) return result;

        result = tryParseWithCustomFormats(cleanDateStr);
        if (result != null) return result;

        logger.debug("Could not parse date: {}", dateStr);
        return null;
    }

    /**
     * Parse dates with timezone info like "30/05/2025 13:00 GMT+7"
     */
    private static Instant tryParseWithTimezone(String dateStr) {
        // Handle GMT+X format
        Matcher gmtMatcher = GMT_OFFSET_PATTERN.matcher(dateStr);
        if (gmtMatcher.find()) {
            try {
                String offsetStr = gmtMatcher.group(1);
                int offsetHours = Integer.parseInt(offsetStr);
                ZoneOffset zoneOffset = ZoneOffset.ofHours(offsetHours);

                // Remove GMT+X part and parse the datetime
                String cleanDateStr = dateStr.replaceAll("\\s*GMT[+-]\\d{1,2}\\s*", "").trim();

                LocalDateTime localDateTime = tryParseLocalDateTime(cleanDateStr);
                if (localDateTime != null) {
                    return localDateTime.atOffset(zoneOffset).toInstant();
                }
            } catch (Exception e) {
                // Continue to next strategy
            }
        }

        // Try standard timezone formats
        String[] timezonePatterns = {
                "dd/MM/yyyy HH:mm XXX",        // 30/05/2025 13:00 +07:00
                "dd/MM/yyyy HH:mm zzz",        // 30/05/2025 13:00 ICT
                "yyyy-MM-dd'T'HH:mm:ss'Z'",    // ISO format with Z
                "yyyy-MM-dd'T'HH:mm:ssXXX",    // ISO format with offset
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", // ISO with milliseconds
                "MMM dd, yyyy HH:mm XXX",      // Jan 01, 2025 13:00 +07:00
                "dd MMM yyyy HH:mm XXX"        // 01 Jan 2025 13:00 +07:00
        };

        for (String pattern : timezonePatterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                return ZonedDateTime.parse(dateStr, formatter).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    /**
     * Parse as LocalDateTime and assume system timezone
     */
    private static Instant tryParseAsLocalDateTime(String dateStr) {
        String[] patterns = {
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "MMM dd, yyyy HH:mm:ss",
                "MMM dd, yyyy HH:mm",
                "dd MMM yyyy HH:mm:ss",
                "dd MMM yyyy HH:mm",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm"
        };

        for (String pattern : patterns) {
            LocalDateTime localDateTime = tryParseLocalDateTime(dateStr, pattern);
            if (localDateTime != null) {
                // Convert to Instant using system default timezone
                return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
            }
        }

        return null;
    }

    /**
     * Parse as LocalDate (date only, no time)
     */
    private static Instant tryParseAsLocalDate(String dateStr) {
        String[] patterns = {
                "dd/MM/yyyy",
                "yyyy-MM-dd",
                "dd-MM-yyyy",
                "MMM dd, yyyy",
                "dd MMM yyyy",
                "yyyy/MM/dd",
                "MM/dd/yyyy"
        };

        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                LocalDate localDate = LocalDate.parse(dateStr, formatter);
                // Start of day in system timezone
                return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    /**
     * Parse with custom/flexible formats
     */
    private static Instant tryParseWithCustomFormats(String dateStr) {
        // Handle relative dates
        if (dateStr.toLowerCase().contains("today")) {
            return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        }

        if (dateStr.toLowerCase().contains("yesterday")) {
            return LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        }

        // Handle "X days ago", "X hours ago", etc.
        Pattern relativePattern = Pattern.compile("(\\d+)\\s+(day|hour|minute)s?\\s+ago", Pattern.CASE_INSENSITIVE);
        Matcher matcher = relativePattern.matcher(dateStr);
        if (matcher.find()) {
            try {
                int amount = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();

                Instant now = Instant.now();
                switch (unit) {
                    case "day":
                        return now.minus(Duration.ofDays(amount));
                    case "hour":
                        return now.minus(Duration.ofHours(amount));
                    case "minute":
                        return now.minus(Duration.ofMinutes(amount));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return null;
    }

    /**
     * Helper method to parse LocalDateTime with given pattern
     */
    private static LocalDateTime tryParseLocalDateTime(String dateStr, String pattern) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
            return LocalDateTime.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Helper method to parse LocalDateTime with common patterns
     */
    private static LocalDateTime tryParseLocalDateTime(String dateStr) {
        String[] patterns = {
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm"
        };

        for (String pattern : patterns) {
            LocalDateTime result = tryParseLocalDateTime(dateStr, pattern);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    // Test method to demonstrate
    public static void main(String[] args) {
        DateTimeUtil parser = new DateTimeUtil();

        String[] testDates = {
                "30/05/2025 13:00 GMT+7",
                "2025-05-30T13:00:00Z",
                "2025-05-30T13:00:00+07:00",
                "30/05/2025 13:00",
                "30/05/2025",
                "May 30, 2025",
                "30 May 2025 13:00",
                "2 days ago",
                "3 hours ago",
                "today",
                "yesterday"
        };

        for (String dateStr : testDates) {
            Instant result = parser.parseDate(dateStr);
            System.out.printf("Input: %-25s → Output: %s%n",
                    dateStr, result != null ? result.toString() : "null");
        }
    }
}

