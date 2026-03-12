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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class DateTimeConverterTest {

    @Test
    void parse_ShouldReturnOffsetDateTime_WhenStringIsValid() {
        String dateStr = "2023-10-05T10:15:30+01:00";
        OffsetDateTime result = DateTimeConverter.parse(dateStr);
        assertThat(result).isEqualTo(OffsetDateTime.of(2023, 10, 5, 10, 15, 30, 0, ZoneOffset.ofHours(1)));
    }

    @Test
    void parse_ShouldThrowIllegalArgumentException_WhenStringIsInvalid() {
        String invalidDateStr = "invalid-date";
        assertThatThrownBy(() -> DateTimeConverter.parse(invalidDateStr))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid DateTime format");
    }

    @Test
    void format_ShouldReturnString_WhenOffsetDateTimeIsValid() {
        OffsetDateTime dateTime = OffsetDateTime.of(2023, 10, 5, 10, 15, 30, 0, ZoneOffset.ofHours(1));
        String result = DateTimeConverter.format(dateTime);
        assertThat(result).isEqualTo("2023-10-05T10:15:30+01:00");
    }

    @Test
    void format_ShouldReturnNull_WhenOffsetDateTimeIsNull() {
        assertThat(DateTimeConverter.format(null)).isNull();
    }

    @Test
    void constructor_ShouldThrowUnsupportedOperationException() throws Exception {
        Constructor<DateTimeConverter> constructor = DateTimeConverter.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
