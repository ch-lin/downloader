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

import org.springframework.stereotype.Component;

import ch.lin.downloader.backend.api.app.service.command.CreateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.UpdateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.YtDlpConfigCommand;
import ch.lin.downloader.backend.api.dto.CreateConfigRequest;
import ch.lin.downloader.backend.api.dto.UpdateConfigRequest;
import ch.lin.downloader.backend.api.dto.YtDlpConfigDto;

@Component
public class ConfigMapper {

    public CreateConfigCommand toCommand(CreateConfigRequest request) {
        if (request == null) {
            return null;
        }
        CreateConfigCommand command = new CreateConfigCommand();
        command.setName(request.getName());
        command.setEnabled(request.getEnabled());
        command.setDuration(request.getDuration());
        command.setStartDownloadAutomatically(request.getStartDownloadAutomatically());
        command.setRemoveCompletedJobAutomatically(request.getRemoveCompletedJobAutomatically());
        command.setClientId(request.getClientId());
        command.setClientSecret(request.getClientSecret());
        command.setThreadPoolSize(request.getThreadPoolSize());
        command.setYtDlpConfig(toCommand(request.getYtDlpConfig()));
        return command;
    }

    public UpdateConfigCommand toCommand(UpdateConfigRequest request) {
        if (request == null) {
            return null;
        }
        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(request.getEnabled());
        command.setDuration(request.getDuration());
        command.setStartDownloadAutomatically(request.getStartDownloadAutomatically());
        command.setRemoveCompletedJobAutomatically(request.getRemoveCompletedJobAutomatically());
        command.setClientId(request.getClientId());
        command.setClientSecret(request.getClientSecret());
        command.setThreadPoolSize(request.getThreadPoolSize());
        command.setYtDlpConfig(toCommand(request.getYtDlpConfig()));
        return command;
    }

    public YtDlpConfigCommand toCommand(YtDlpConfigDto request) {
        if (request == null) {
            return null;
        }
        YtDlpConfigCommand command = new YtDlpConfigCommand();
        command.setFormatFiltering(request.getFormatFiltering());
        command.setFormatSorting(request.getFormatSorting());
        command.setRemuxVideo(request.getRemuxVideo());
        command.setWriteDescription(request.getWriteDescription());
        command.setWriteSubs(request.getWriteSubs());
        command.setSubLang(request.getSubLang());
        command.setWriteAutoSubs(request.getWriteAutoSubs());
        command.setSubFormat(request.getSubFormat());
        command.setOutputTemplate(request.getOutputTemplate());
        command.setOverwrite(request.getOverwrite());
        command.setKeepVideo(request.getKeepVideo());
        command.setExtractAudio(request.getExtractAudio());
        command.setAudioFormat(request.getAudioFormat());
        command.setAudioQuality(request.getAudioQuality());
        command.setNoProgress(request.getNoProgress());
        command.setUseCookie(request.getUseCookie());
        command.setCookie(request.getCookie());
        return command;
    }
}
