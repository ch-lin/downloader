/*=============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Che-Hung Lin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *===========================================================================*/
package ch.lin.downloader.backend.api.app.converter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * A final utility class for converting between {@link OffsetDateTime} objects
 * and their string representations.
 * <p>
 * This class enforces a consistent ISO 8601 format
 * ({@link DateTimeFormatter#ISO_OFFSET_DATE_TIME}) across the application for
 * serialization and deserialization of date-time values.
 */
public final class DateTimeConverter {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private DateTimeConverter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Parses a string in ISO 8601 format into an {@link OffsetDateTime} object.
     *
     * @param dateTimeString The string to parse (e.g.,
     * "2011-12-03T10:15:30+01:00"). Can be null.
     * @return The corresponding {@link OffsetDateTime} object.
     * @throws IllegalArgumentException if the string is non-null and cannot be
     * parsed into a valid OffsetDateTime.
     */
    public static OffsetDateTime parse(String dateTimeString) {
        try {
            return OffsetDateTime.parse(dateTimeString, formatter);
        } catch (DateTimeParseException e) {
            // The caller is responsible for logging. Wrapping the original exception
            // provides sufficient context.
            throw new IllegalArgumentException("Invalid DateTime format for string: \"" + dateTimeString + "\"", e);
        }
    }

    /**
     * Formats an {@link OffsetDateTime} object into an ISO 8601 compliant
     * string.
     *
     * @param offsetDateTime The date-time object to format. Can be null.
     * @return The formatted string, or null if the input was null.
     */
    public static String format(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.format(formatter);
    }
}
