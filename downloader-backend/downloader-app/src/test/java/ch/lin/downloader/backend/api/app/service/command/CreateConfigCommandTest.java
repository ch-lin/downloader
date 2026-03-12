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

class CreateConfigCommandTest {

    @Test
    void testNoArgsConstructorAndSetters() {
        CreateConfigCommand command = new CreateConfigCommand();
        command.setName("test-config");
        command.setEnabled(true);
        command.setDuration(120);
        command.setStartDownloadAutomatically(true);
        command.setRemoveCompletedJobAutomatically(false);
        command.setClientId("client-id");
        command.setClientSecret("client-secret");
        command.setThreadPoolSize(5);

        YtDlpConfigCommand ytDlpConfig = new YtDlpConfigCommand();
        command.setYtDlpConfig(ytDlpConfig);

        assertThat(command.getName()).isEqualTo("test-config");
        assertThat(command.getEnabled()).isTrue();
        assertThat(command.getDuration()).isEqualTo(120);
        assertThat(command.getStartDownloadAutomatically()).isTrue();
        assertThat(command.getRemoveCompletedJobAutomatically()).isFalse();
        assertThat(command.getClientId()).isEqualTo("client-id");
        assertThat(command.getClientSecret()).isEqualTo("client-secret");
        assertThat(command.getThreadPoolSize()).isEqualTo(5);
        assertThat(command.getYtDlpConfig()).isEqualTo(ytDlpConfig);
    }

    @Test
    void testAllArgsConstructor() {
        YtDlpConfigCommand ytDlpConfig = new YtDlpConfigCommand();
        CreateConfigCommand command = new CreateConfigCommand("test", true, 60, false, true, "id", "secret", 2, ytDlpConfig);

        assertThat(command.getName()).isEqualTo("test");
        assertThat(command.getYtDlpConfig()).isEqualTo(ytDlpConfig);
    }
}
