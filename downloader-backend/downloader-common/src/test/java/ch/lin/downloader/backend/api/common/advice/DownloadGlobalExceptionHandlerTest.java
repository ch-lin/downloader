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
package ch.lin.downloader.backend.api.common.advice;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;

import ch.lin.downloader.backend.api.common.exception.UpdateException;
import ch.lin.platform.api.ApiError;
import ch.lin.platform.api.ApiResponse;
import ch.lin.platform.web.advice.BaseGlobalExceptionHandler;

class DownloadGlobalExceptionHandlerTest {

    @Test
    void shouldBeAnnotatedWithControllerAdvice() {
        assertThat(DownloadGlobalExceptionHandler.class).hasAnnotation(ControllerAdvice.class);
    }

    @Test
    void shouldExtendBaseGlobalExceptionHandler() {
        assertThat(BaseGlobalExceptionHandler.class).isAssignableFrom(DownloadGlobalExceptionHandler.class);
    }

    @Test
    void shouldHaveDefaultConstructor() {
        assertThat(new DownloadGlobalExceptionHandler()).isNotNull();
    }

    @Test
    void shouldHandleUpdateException() {
        DownloadGlobalExceptionHandler handler = new DownloadGlobalExceptionHandler();
        String errorMessage = "Update failed";
        UpdateException ex = new UpdateException(errorMessage);

        ResponseEntity<ApiResponse<ApiError>> response = handler.handleUpdateException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(response.getBody())
                .isNotNull()
                .satisfies(body -> {
                    assertThat(body.getData()).isNotNull();
                    assertThat(body.getData().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
                    assertThat(body.getData().getMessage()).isEqualTo(errorMessage);
                });
    }
}
