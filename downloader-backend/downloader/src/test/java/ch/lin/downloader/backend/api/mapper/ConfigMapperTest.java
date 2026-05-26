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
package ch.lin.downloader.backend.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.downloader.backend.api.app.service.command.CreateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.UpdateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.YtDlpConfigCommand;
import ch.lin.downloader.backend.api.domain.OverwriteOption;
import ch.lin.downloader.backend.api.dto.CreateConfigRequest;
import ch.lin.downloader.backend.api.dto.UpdateConfigRequest;
import ch.lin.downloader.backend.api.dto.YtDlpConfigDto;

class ConfigMapperTest {

    private ConfigMapper configMapper;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        configMapper = new ConfigMapper();
    }

    @Test
    void toCommand_CreateConfigRequest_Null_ShouldReturnNull() {
        assertThat(configMapper.toCommand((CreateConfigRequest) null)).isNull();
    }

    @Test
    void toCommand_CreateConfigRequest_ValidInput_ShouldMapCorrectly() {
        CreateConfigRequest request = new CreateConfigRequest();
        request.setName("test-config");
        request.setEnabled(true);
        request.setDuration(3600);
        request.setStartDownloadAutomatically(true);
        request.setRemoveCompletedJobAutomatically(false);
        request.setClientId("client");
        request.setClientSecret("secret");
        request.setThreadPoolSize(5);

        YtDlpConfigDto ytReq = new YtDlpConfigDto();
        ytReq.setFormatFiltering("best");
        request.setYtDlpConfig(ytReq);

        CreateConfigCommand command = configMapper.toCommand(request);

        assertThat(command).isNotNull();
        assertThat(command.getName()).isEqualTo("test-config");
        assertThat(command.getEnabled()).isTrue();
        assertThat(command.getDuration()).isEqualTo(3600);
        assertThat(command.getStartDownloadAutomatically()).isTrue();
        assertThat(command.getRemoveCompletedJobAutomatically()).isFalse();
        assertThat(command.getClientId()).isEqualTo("client");
        assertThat(command.getClientSecret()).isEqualTo("secret");
        assertThat(command.getThreadPoolSize()).isEqualTo(5);

        assertThat(command.getYtDlpConfig()).isNotNull();
        assertThat(command.getYtDlpConfig().getFormatFiltering()).isEqualTo("best");
    }

    @Test
    void toCommand_UpdateConfigRequest_Null_ShouldReturnNull() {
        assertThat(configMapper.toCommand((UpdateConfigRequest) null)).isNull();
    }

    @Test
    void toCommand_UpdateConfigRequest_ValidInput_ShouldMapCorrectly() {
        UpdateConfigRequest request = new UpdateConfigRequest();
        request.setEnabled(false);
        request.setDuration(1800);
        request.setStartDownloadAutomatically(false);
        request.setRemoveCompletedJobAutomatically(true);
        request.setClientId("client2");
        request.setClientSecret("secret2");
        request.setThreadPoolSize(10);

        UpdateConfigCommand command = configMapper.toCommand(request);

        assertThat(command).isNotNull();
        assertThat(command.getEnabled()).isFalse();
        assertThat(command.getDuration()).isEqualTo(1800);
        assertThat(command.getStartDownloadAutomatically()).isFalse();
        assertThat(command.getRemoveCompletedJobAutomatically()).isTrue();
        assertThat(command.getClientId()).isEqualTo("client2");
        assertThat(command.getClientSecret()).isEqualTo("secret2");
        assertThat(command.getThreadPoolSize()).isEqualTo(10);
        assertThat(command.getYtDlpConfig()).isNull();
    }

    @Test
    void toCommand_YtDlpConfigDto_Null_ShouldReturnNull() {
        assertThat(configMapper.toCommand((YtDlpConfigDto) null)).isNull();
    }

    @Test
    void toCommand_YtDlpConfigDto_ValidInput_ShouldMapAllFields() {
        YtDlpConfigDto request = new YtDlpConfigDto();
        request.setFormatFiltering("best");
        request.setFormatSorting("res:1080");
        request.setRemuxVideo("mp4");
        request.setOverwrite(OverwriteOption.FORCE);
        request.setCookie("my-cookie");

        YtDlpConfigCommand command = configMapper.toCommand(request);

        assertThat(command).isNotNull();
        assertThat(command.getFormatFiltering()).isEqualTo("best");
        assertThat(command.getFormatSorting()).isEqualTo("res:1080");
        assertThat(command.getRemuxVideo()).isEqualTo("mp4");
        assertThat(command.getOverwrite()).isEqualTo(OverwriteOption.FORCE);
        assertThat(command.getCookie()).isEqualTo("my-cookie");
    }
}
