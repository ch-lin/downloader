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

import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.downloader.backend.api.app.service.DownloaderService;
import ch.lin.downloader.backend.api.app.service.model.DownloadTaskDetails;

@ExtendWith(MockitoExtension.class)
class DownloadTaskControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DownloaderService downloadService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        DownloadTaskController controller = new DownloadTaskController(downloadService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getTaskById_ShouldReturnTask_WhenFound() throws Exception {
        String taskId = UUID.randomUUID().toString();
        DownloadTaskDetails taskDetails = new DownloadTaskDetails();
        Objects.requireNonNull(taskId);
        when(downloadService.getTaskById(taskId)).thenReturn(taskDetails);

        mockMvc.perform(get("/download/tasks/{taskId}", taskId))
                .andExpect(status().isOk());
        verify(downloadService).getTaskById(taskId);
    }

    @Test
    void deleteTaskById_ShouldReturnNoContent() throws Exception {
        String taskId = UUID.randomUUID().toString();
        Objects.requireNonNull(taskId);
        doNothing().when(downloadService).deleteTaskById(taskId);

        mockMvc.perform(delete("/download/tasks/{taskId}", taskId))
                .andExpect(status().isNoContent());
        verify(downloadService).deleteTaskById(taskId);
    }
}
