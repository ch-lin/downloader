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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

/**
 * A JPA {@link AttributeConverter} to automatically convert between
 * {@link OffsetDateTime} and {@link java.sql.Timestamp}.
 * <p>
 * This converter, applied automatically to all {@code OffsetDateTime}
 * attributes, standardizes time storage. It ensures that when writing to the
 * database, the {@code OffsetDateTime} is converted to its equivalent point in
 * time (Instant) and stored as a timezone-agnostic {@code Timestamp}. When
 * reading from the database, the {@code Timestamp} is converted back to an
 * {@code OffsetDateTime} normalized to the UTC (Z) offset.
 */
@Converter(autoApply = true)
public class OffsetDateTimeConverter implements AttributeConverter<OffsetDateTime, Timestamp> {

    /**
     * Converts an {@link OffsetDateTime} to a {@link Timestamp} for database
     * persistence. This is achieved by extracting the {@code Instant}, which
     * represents a specific point in time without any timezone or offset
     * information. Note that the original offset is not stored.
     *
     * @param offsetDateTime The entity attribute to be converted. Can be null.
     * @return The corresponding {@code Timestamp} for the database column, or
     * null if the input was null.
     */
    @Override
    public Timestamp convertToDatabaseColumn(OffsetDateTime offsetDateTime) {
        return (offsetDateTime == null) ? null : Timestamp.from(offsetDateTime.toInstant());
    }

    /**
     * Converts a {@link Timestamp} from the database into an
     * {@link OffsetDateTime}. The resulting {@code OffsetDateTime} is
     * consistently normalized to the UTC timezone (ZoneOffset.UTC).
     *
     * @param timestamp The data from the database column. Can be null.
     * @return The corresponding {@link OffsetDateTime} object in UTC, or null
     * if the input was null.
     */
    @Override
    public OffsetDateTime convertToEntityAttribute(Timestamp timestamp) {
        return (timestamp == null) ? null : timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
}
