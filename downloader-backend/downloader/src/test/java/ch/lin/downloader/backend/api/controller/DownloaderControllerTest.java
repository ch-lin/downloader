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
package ch.lin.downloader.backend.api.controller;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.downloader.backend.api.app.service.DownloaderService;
import ch.lin.downloader.backend.api.app.service.command.DownloadItem;
import ch.lin.downloader.backend.api.domain.DownloadJob;
import ch.lin.downloader.backend.api.domain.DownloadTask;
import ch.lin.downloader.backend.api.dto.DownloadRequest;
import ch.lin.platform.exception.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
class DownloaderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DownloaderService downloadService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        DownloaderController controller = new DownloaderController(downloadService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createDownloadJob_ShouldReturnAccepted_WhenRequestIsValid() throws Exception {
        DownloadItem item = new DownloadItem();
        item.setVideoId("vid1");
        item.setTitle("Title 1");

        DownloadRequest request = new DownloadRequest();
        request.setItems(List.of(item));
        request.setConfigName("default");

        DownloadJob job = new DownloadJob();
        DownloadTask task = new DownloadTask();
        task.setId("task1");
        task.setVideoId("vid1");
        job.setTasks(List.of(task));

        when(downloadService.createDownloadJob(anyList(), eq("default"))).thenReturn(job);

        String content = objectMapper.writeValueAsString(request);
        Objects.requireNonNull(content);
        mockMvc.perform(post("/download")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(content))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data[0].taskId").value("task1"))
                .andExpect(jsonPath("$.data[0].videoId").value("vid1"));
    }

    @Test
    void createDownloadJob_ShouldThrowException_WhenItemsIsEmpty() throws Exception {
        DownloadRequest request = new DownloadRequest();
        request.setItems(Collections.emptyList());
        request.setConfigName("default");

        String content = objectMapper.writeValueAsString(request);
        Objects.requireNonNull(content);

        mockMvc.perform(post("/download")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(content))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResolvedException())
                .isInstanceOf(InvalidRequestException.class));
    }

    @Test
    void createDownloadJob_ShouldThrowException_WhenItemsIsNull() throws Exception {
        DownloadRequest request = new DownloadRequest();
        // items is null by default
        request.setConfigName("default");

        String content = objectMapper.writeValueAsString(request);
        Objects.requireNonNull(content);

        mockMvc.perform(post("/download")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(content))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResolvedException())
                .isInstanceOf(InvalidRequestException.class));
    }

    @Test
    void createDownloadJob_ShouldThrowException_WhenRequestIsNull() {
        DownloaderController controller = new DownloaderController(downloadService);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.createDownloadJob(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void deleteAllJobs_ShouldReturnNoContent() throws Exception {
        doNothing().when(downloadService).deleteAllJobs();

        mockMvc.perform(delete("/download"))
                .andExpect(status().isNoContent());

        verify(downloadService).deleteAllJobs();
    }
}
