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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OffsetDateTimeConverterTest {

    private final OffsetDateTimeConverter converter = new OffsetDateTimeConverter();

    @Test
    void convertToDatabaseColumn_ShouldReturnTimestamp_WhenOffsetDateTimeIsValid() {
        OffsetDateTime dateTime = OffsetDateTime.of(2023, 10, 5, 10, 15, 30, 0, ZoneOffset.ofHours(2));
        Timestamp result = converter.convertToDatabaseColumn(dateTime);

        assertThat(result).isNotNull();
        assertThat(result.toInstant()).isEqualTo(dateTime.toInstant());
    }

    @Test
    void convertToDatabaseColumn_ShouldReturnNull_WhenOffsetDateTimeIsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_ShouldReturnOffsetDateTimeInUTC_WhenTimestampIsValid() {
        Instant instant = Instant.parse("2023-10-05T08:15:30Z");
        Timestamp timestamp = Timestamp.from(instant);

        OffsetDateTime result = converter.convertToEntityAttribute(timestamp);

        assertThat(result).isNotNull();
        assertThat(result.toInstant()).isEqualTo(instant);
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void convertToEntityAttribute_ShouldReturnNull_WhenTimestampIsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
