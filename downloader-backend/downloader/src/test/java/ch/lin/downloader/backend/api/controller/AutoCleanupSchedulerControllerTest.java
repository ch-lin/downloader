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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.downloader.backend.api.app.service.AutoCleanupServiceImpl;

@ExtendWith(MockitoExtension.class)
class AutoCleanupSchedulerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AutoCleanupServiceImpl autoCleanupService;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        AutoCleanupSchedulerController controller = new AutoCleanupSchedulerController(autoCleanupService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void startScheduler_ShouldReturnOk() throws Exception {
        doNothing().when(autoCleanupService).start();

        mockMvc.perform(post("/cleanup/start"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cleanup scheduler started."));
        verify(autoCleanupService).start();
    }

    @Test
    void stopScheduler_ShouldReturnOk() throws Exception {
        doNothing().when(autoCleanupService).stop();

        mockMvc.perform(post("/cleanup/stop"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cleanup scheduler stopped."));
        verify(autoCleanupService).stop();
    }

    @Test
    void getSchedulerStatus_ShouldReturnStatus() throws Exception {
        when(autoCleanupService.isSchedulerRunning()).thenReturn(false);

        mockMvc.perform(get("/cleanup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRunning").value(false));
        verify(autoCleanupService).isSchedulerRunning();
    }
}
