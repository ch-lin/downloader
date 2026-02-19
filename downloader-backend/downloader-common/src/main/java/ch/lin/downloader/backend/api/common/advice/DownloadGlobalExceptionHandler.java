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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import ch.lin.downloader.backend.api.common.exception.UpdateException;
import ch.lin.platform.api.ApiError;
import ch.lin.platform.api.ApiResponse;
import ch.lin.platform.web.advice.BaseGlobalExceptionHandler;

/**
 * Global exception handler for the web layer.
 * <p>
 * This class uses {@link ControllerAdvice} to intercept exceptions thrown by
 * controllers and maps them to a standardized {@link ApiResponse} format. This
 * ensures that clients receive consistent and structured error responses for
 * different failure scenarios.
 */
@ControllerAdvice
public class DownloadGlobalExceptionHandler extends BaseGlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadGlobalExceptionHandler.class);

    /**
     * Handles exceptions related to the yt-dlp update process.
     * <p>
     * Catches {@link UpdateException} and returns an {@link ApiResponse} with
     * an "INTERNAL_SERVER_ERROR" error code. This prevents leaking internal
     * process error details to the client. The HTTP status is expected to be
     * set to 500 (Internal Server Error) via the {@code @ResponseStatus}
     * annotation on the exception class.
     *
     * @param ex The caught {@link UpdateException}.
     * @return An {@link ApiResponse} containing a generic error message.
     */
    @ExceptionHandler({
        UpdateException.class
    })
    public ResponseEntity<ApiResponse<ApiError>> handleUpdateException(UpdateException ex) {
        logger.error("yt-dlp update error: {}", ex.getMessage(), ex);
        ApiError apiError = new ApiError("INTERNAL_SERVER_ERROR", ex.getMessage());
        ApiResponse<ApiError> response = ApiResponse.failure(apiError);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
