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

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.downloader.backend.api.app.service.ConfigsService;
import ch.lin.downloader.backend.api.app.service.command.CreateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.UpdateConfigCommand;
import ch.lin.downloader.backend.api.app.service.model.AllConfigsData;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.dto.CreateConfigRequest;
import ch.lin.downloader.backend.api.dto.UpdateConfigRequest;
import ch.lin.downloader.backend.api.mapper.ConfigMapper;

@ExtendWith(MockitoExtension.class)
class ConfigsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ConfigsService configsService;

    @Mock
    private ConfigMapper configMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        ConfigsController controller = new ConfigsController(configsService, configMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAllConfigs_ShouldReturnConfigs() throws Exception {
        AllConfigsData data = new AllConfigsData();
        data.setAllConfigNames(List.of("default", "custom"));
        data.setEnabledConfigName("default");

        when(configsService.getAllConfigs()).thenReturn(data);

        mockMvc.perform(get("/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allConfigNames[0]").value("default"))
                .andExpect(jsonPath("$.enabledConfigName").value("default"));
    }

    @Test
    void createConfig_ShouldReturnCreated() throws Exception {
        CreateConfigRequest request = new CreateConfigRequest();
        request.setName("new-config");

        CreateConfigCommand command = new CreateConfigCommand();
        DownloaderConfig config = new DownloaderConfig();
        config.setName("new-config");

        when(configMapper.toCommand(any(CreateConfigRequest.class))).thenReturn(command);
        when(configsService.createConfig(command)).thenReturn(config);

        String content = objectMapper.writeValueAsString(request);
        Objects.requireNonNull(content);
        mockMvc.perform(post("/configs")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(content))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/configs/new-config"))
                .andExpect(jsonPath("$.data.name").value("new-config"));
    }

    @Test
    void deleteAllConfigs_ShouldReturnNoContent() throws Exception {
        doNothing().when(configsService).deleteAllConfigs();

        mockMvc.perform(delete("/configs"))
                .andExpect(status().isNoContent());

        verify(configsService).deleteAllConfigs();
    }

    @Test
    void getConfig_ShouldReturnConfig() throws Exception {
        DownloaderConfig config = new DownloaderConfig();
        config.setName("default");

        when(configsService.getConfig("default")).thenReturn(config);

        mockMvc.perform(get("/configs/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("default"));
    }

    @Test
    void deleteConfig_ShouldReturnNoContent() throws Exception {
        doNothing().when(configsService).deleteConfig("custom");

        mockMvc.perform(delete("/configs/custom"))
                .andExpect(status().isNoContent());

        verify(configsService).deleteConfig("custom");
    }

    @Test
    void saveConfig_ShouldReturnOk() throws Exception {
        UpdateConfigRequest request = new UpdateConfigRequest();
        request.setEnabled(true);

        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(true);

        DownloaderConfig config = new DownloaderConfig();
        config.setName("custom");
        config.setEnabled(true);

        when(configMapper.toCommand(any(UpdateConfigRequest.class))).thenReturn(command);
        when(configsService.saveConfig(eq("custom"), any(UpdateConfigCommand.class))).thenReturn(config);

        String content = objectMapper.writeValueAsString(request);
        Objects.requireNonNull(content);
        mockMvc.perform(patch("/configs/custom")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("custom"))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }
}
