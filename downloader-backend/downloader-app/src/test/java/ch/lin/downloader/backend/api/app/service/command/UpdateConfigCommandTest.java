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
package ch.lin.downloader.backend.api.app.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class UpdateConfigCommandTest {

    @Test
    void testGettersAndSetters() {
        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(true);
        command.setDuration(300);
        command.setStartDownloadAutomatically(false);
        command.setRemoveCompletedJobAutomatically(true);
        command.setClientId("new-id");
        command.setClientSecret("new-secret");
        command.setThreadPoolSize(10);

        YtDlpConfigCommand ytDlpConfig = new YtDlpConfigCommand();
        command.setYtDlpConfig(ytDlpConfig);

        assertThat(command.getEnabled()).isTrue();
        assertThat(command.getDuration()).isEqualTo(300);
        assertThat(command.getStartDownloadAutomatically()).isFalse();
        assertThat(command.getRemoveCompletedJobAutomatically()).isTrue();
        assertThat(command.getClientId()).isEqualTo("new-id");
        assertThat(command.getClientSecret()).isEqualTo("new-secret");
        assertThat(command.getThreadPoolSize()).isEqualTo(10);
        assertThat(command.getYtDlpConfig()).isEqualTo(ytDlpConfig);
    }
}
