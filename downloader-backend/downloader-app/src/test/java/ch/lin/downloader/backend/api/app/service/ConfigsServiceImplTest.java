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
package ch.lin.downloader.backend.api.app.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.downloader.backend.api.app.config.DefaultConfigFactory;
import ch.lin.downloader.backend.api.app.config.DownloaderDefaultProperties;
import ch.lin.downloader.backend.api.app.repository.DownloaderConfigRepository;
import ch.lin.downloader.backend.api.app.repository.YtDlpConfigRepository;
import ch.lin.downloader.backend.api.app.service.command.CreateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.UpdateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.YtDlpConfigCommand;
import ch.lin.downloader.backend.api.app.service.model.AllConfigsData;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.OverwriteOption;
import ch.lin.downloader.backend.api.domain.YtDlpConfig;
import ch.lin.platform.exception.ConfigCreationException;
import ch.lin.platform.exception.ConfigNotFoundException;
import ch.lin.platform.exception.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
class ConfigsServiceImplTest {

    @Mock
    private YtDlpConfigRepository ytDlpConfigRepository;
    @Mock
    private DownloaderConfigRepository downloaderConfigRepository;
    @Mock
    private DownloaderDefaultProperties defaultProperties;
    @Mock
    private DefaultConfigFactory defaultConfigFactory;

    @InjectMocks
    private ConfigsServiceImpl configsService;

    private DownloaderConfig defaultConfig;
    private YtDlpConfig defaultYtDlpConfig;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        defaultYtDlpConfig = new YtDlpConfig();
        defaultYtDlpConfig.setName("default");
        defaultYtDlpConfig.setFormatFiltering("best");
        defaultYtDlpConfig.setFormatSorting("res:1080");
        defaultYtDlpConfig.setRemuxVideo("mp4");
        defaultYtDlpConfig.setWriteDescription(true);
        defaultYtDlpConfig.setWriteSubs(true);
        defaultYtDlpConfig.setSubLang("en");
        defaultYtDlpConfig.setWriteAutoSubs(false);
        defaultYtDlpConfig.setSubFormat("srt");
        defaultYtDlpConfig.setOutputTemplate("%(title)s.%(ext)s");
        defaultYtDlpConfig.setOverwrite(OverwriteOption.FORCE);
        defaultYtDlpConfig.setKeepVideo(true);
        defaultYtDlpConfig.setExtractAudio(false);
        defaultYtDlpConfig.setAudioFormat("mp3");
        defaultYtDlpConfig.setAudioQuality(0);
        defaultYtDlpConfig.setNoProgress(true);
        defaultYtDlpConfig.setUseCookie(false);

        defaultConfig = new DownloaderConfig();
        defaultConfig.setName("default");
        defaultConfig.setEnabled(true);
        defaultConfig.setYtDlpConfig(defaultYtDlpConfig);
        defaultConfig.setDuration(60);
        defaultConfig.setStartDownloadAutomatically(true);
        defaultConfig.setRemoveCompletedJobAutomatically(false);
        defaultConfig.setClientId("client-id");
        defaultConfig.setClientSecret("client-secret");
        defaultConfig.setThreadPoolSize(3);
    }

    @Test
    void getAllConfigs_ShouldReturnDefault_WhenDbEmpty() {
        when(downloaderConfigRepository.count()).thenReturn(0L);
        when(downloaderConfigRepository.findByName("default")).thenReturn(Optional.empty());
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenReturn(defaultConfig);
        when(downloaderConfigRepository.findAll()).thenReturn(List.of(defaultConfig));
        when(downloaderConfigRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(defaultConfig));

        AllConfigsData result = configsService.getAllConfigs();

        assertThat(result.getEnabledConfigName()).isEqualTo("default");
        assertThat(result.getAllConfigNames()).containsExactly("default");
        verify(downloaderConfigRepository).save(Objects.requireNonNull(anyDownloaderConfig()));
    }

    @Test
    void getAllConfigs_ShouldReturnConfigs_WhenDbNotEmpty() {
        DownloaderConfig config = new DownloaderConfig();
        config.setName("custom");
        config.setEnabled(true);

        when(downloaderConfigRepository.count()).thenReturn(1L);
        when(downloaderConfigRepository.findAll()).thenReturn(List.of(config));
        when(downloaderConfigRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(config));

        AllConfigsData result = configsService.getAllConfigs();

        assertThat(result.getEnabledConfigName()).isEqualTo("custom");
        assertThat(result.getAllConfigNames()).containsExactly("custom");
    }

    @Test
    void getAllConfigs_ShouldThrow_WhenDefaultConfigCreationFails() {
        when(downloaderConfigRepository.count()).thenReturn(0L);
        when(downloaderConfigRepository.findByName("default")).thenReturn(Optional.empty());
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);
        when(downloaderConfigRepository.save(Objects.requireNonNull(defaultConfig))).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> configsService.getAllConfigs())
                .isInstanceOf(ConfigCreationException.class)
                .hasMessageContaining("Cannot create default config");
    }

    @Test
    void createConfig_ShouldThrow_WhenNameIsDefault() {
        CreateConfigCommand command = mock(CreateConfigCommand.class);
        when(command.getName()).thenReturn("default");

        assertThatThrownBy(() -> configsService.createConfig(command))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("system-reserved");
    }

    @Test
    void createConfig_ShouldThrow_WhenConfigExists() {
        CreateConfigCommand command = mock(CreateConfigCommand.class);
        when(command.getName()).thenReturn("existing");
        when(downloaderConfigRepository.findByName("existing")).thenReturn(Optional.of(new DownloaderConfig()));

        assertThatThrownBy(() -> configsService.createConfig(command))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createConfig_ShouldSaveAndDisableOthers_WhenEnabled() {
        CreateConfigCommand command = new CreateConfigCommand();
        command.setName("new");
        command.setEnabled(true);
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        command.setYtDlpConfig(ytDlpConfigCommand);

        when(downloaderConfigRepository.findByName("new")).thenReturn(Optional.empty());
        DownloaderConfig otherConfig = new DownloaderConfig();
        otherConfig.setName("other");
        otherConfig.setEnabled(true);
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(otherConfig));
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        DownloaderConfig result = configsService.createConfig(command);

        assertThat(result.getName()).isEqualTo("new");
        assertThat(result.getEnabled()).isTrue();
        assertThat(otherConfig.getEnabled()).isFalse();
        verify(downloaderConfigRepository).saveAll(Objects.requireNonNull(anyList()));
    }

    @Test
    void createConfig_ShouldSaveWithoutDisablingOthers_WhenDisabled() {
        CreateConfigCommand command = new CreateConfigCommand();
        command.setName("newDisabled");
        command.setEnabled(false);
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        command.setYtDlpConfig(ytDlpConfigCommand);

        when(downloaderConfigRepository.findByName("newDisabled")).thenReturn(Optional.empty());
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        DownloaderConfig result = configsService.createConfig(command);

        assertThat(result.getName()).isEqualTo("newDisabled");
        assertThat(result.getEnabled()).isFalse();
        verify(downloaderConfigRepository, never()).saveAll(Objects.requireNonNull(anyList()));
    }

    @Test
    void createConfig_ShouldSaveCookie_WhenCookieProvided(@TempDir Path tempDir) throws IOException {
        CreateConfigCommand command = new CreateConfigCommand();
        command.setName("cookieConfig");
        command.setEnabled(true);
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        ytDlpConfigCommand.setCookie("cookie-content");
        command.setYtDlpConfig(ytDlpConfigCommand);

        when(downloaderConfigRepository.findByName("cookieConfig")).thenReturn(Optional.empty());
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        configsService.createConfig(command);

        Path cookiePath = tempDir.resolve("cookieConfig-cookie.txt");
        assertThat(Files.exists(cookiePath)).isTrue();
        assertThat(Files.readString(cookiePath)).isEqualTo("cookie-content");
    }

    @Test
    void deleteAllConfigs_ShouldCallCleanTable() {
        when(downloaderConfigRepository.findAll()).thenReturn(Collections.emptyList());
        configsService.deleteAllConfigs();
        verify(downloaderConfigRepository).cleanTable();
        verify(ytDlpConfigRepository).cleanTable();
    }

    @Test
    void deleteAllConfigs_ShouldDeleteCookies(@TempDir Path tempDir) throws IOException {
        DownloaderConfig config = new DownloaderConfig();
        config.setName("configToDelete");
        when(downloaderConfigRepository.findAll()).thenReturn(List.of(config));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        Path cookiePath = tempDir.resolve("configToDelete-cookie.txt");
        Files.writeString(cookiePath, "dummy");

        configsService.deleteAllConfigs();

        assertThat(Files.exists(cookiePath)).isFalse();
    }

    @Test
    void deleteConfig_ShouldThrow_WhenNameIsDefault() {
        assertThatThrownBy(() -> configsService.deleteConfig("default"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("system-reserved");
    }

    @Test
    void deleteConfig_ShouldDeleteAndEnableDefault_WhenNoOthersEnabled() {
        String configName = "toDelete";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(Collections.emptyList());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        // Mock finding default config to enable it
        when(downloaderConfigRepository.findByName("default")).thenReturn(Optional.of(defaultConfig));
        defaultConfig.setEnabled(false); // Initially false to verify it gets enabled

        configsService.deleteConfig(configName);

        verify(downloaderConfigRepository).delete(config);
        assertThat(defaultConfig.getEnabled()).isTrue();
        verify(downloaderConfigRepository).save(Objects.requireNonNull(defaultConfig));
    }

    @Test
    void deleteConfig_ShouldThrow_WhenConfigNotFound() {
        String configName = "nonExistent";
        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configsService.deleteConfig(configName))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteConfig_ShouldNotEnableDefault_WhenOtherConfigsEnabled() {
        String configName = "toDelete";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        DownloaderConfig otherConfig = new DownloaderConfig();
        otherConfig.setName("other");
        otherConfig.setEnabled(true);
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(otherConfig));

        configsService.deleteConfig(configName);

        verify(downloaderConfigRepository).delete(config);
        verify(downloaderConfigRepository, never()).findByName("default");
    }

    @Test
    void deleteConfig_ShouldNotSaveDefault_WhenDefaultAlreadyEnabled() {
        String configName = "toDelete";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(Collections.emptyList());
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        // Mock finding default config which is already enabled
        when(downloaderConfigRepository.findByName("default")).thenReturn(Optional.of(defaultConfig));
        defaultConfig.setEnabled(true);

        configsService.deleteConfig(configName);

        verify(downloaderConfigRepository).delete(config);
        verify(downloaderConfigRepository, never()).save(Objects.requireNonNull(defaultConfig));
    }

    @Test
    void deleteConfig_ShouldDeleteCookieFile(@TempDir Path tempDir) throws IOException {
        String configName = "configToDelete";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(new DownloaderConfig()));

        Path cookiePath = tempDir.resolve(configName + "-cookie.txt");
        Files.writeString(cookiePath, "content");

        configsService.deleteConfig(configName);

        assertThat(Files.exists(cookiePath)).isFalse();
    }

    @Test
    void getResolvedConfig_ShouldReturnConfig_WhenExists() {
        String configName = "custom";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        config.setDuration(100);
        YtDlpConfig ytDlpConfig = new YtDlpConfig();
        config.setYtDlpConfig(ytDlpConfig);

        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);

        DownloaderConfig result = configsService.getResolvedConfig(configName);

        assertThat(result.getName()).isEqualTo(configName);
        // Should fill missing properties from default
        assertThat(result.getThreadPoolSize()).isEqualTo(defaultConfig.getThreadPoolSize());
    }

    @Test
    void getResolvedConfig_ShouldFallbackToDefault_WhenNameNotFound() {
        when(downloaderConfigRepository.findByName("unknown")).thenReturn(Optional.empty());
        when(downloaderConfigRepository.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        when(downloaderConfigRepository.count()).thenReturn(0L);

        // Mock creation of default
        when(downloaderConfigRepository.findByName("default")).thenReturn(Optional.empty());
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenReturn(defaultConfig);

        DownloaderConfig result = configsService.getResolvedConfig("unknown");

        assertThat(result.getName()).isEqualTo("default");
    }

    @Test
    void getResolvedConfig_ShouldReturnEnabledConfig_WhenNameIsNull() {
        DownloaderConfig enabledConfig = new DownloaderConfig();
        enabledConfig.setName("enabled");
        enabledConfig.setEnabled(true);
        enabledConfig.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findFirstByEnabledTrue()).thenReturn(Optional.of(enabledConfig));
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);

        DownloaderConfig result = configsService.getResolvedConfig(null);

        assertThat(result.getName()).isEqualTo("enabled");
    }

    @Test
    void getConfig_ShouldReturnConfig_WhenExists() {
        String configName = "existing";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        config.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        DownloaderConfig result = configsService.getConfig(configName);

        assertThat(result).isEqualTo(config);
    }

    @Test
    void getConfig_ShouldReturnDefault_WhenNameIsDefaultAndNotExists() {
        when(downloaderConfigRepository.findByName("default")).thenReturn(Optional.empty());
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenReturn(defaultConfig);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        DownloaderConfig result = configsService.getConfig("default");

        assertThat(result).isEqualTo(defaultConfig);
    }

    @Test
    void getConfig_ShouldThrow_WhenNotFoundAndNotDefault() {
        String configName = "nonExistent";
        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configsService.getConfig(configName))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void saveConfig_ShouldUpdateExisting() {
        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(true);
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        command.setYtDlpConfig(ytDlpConfigCommand);

        DownloaderConfig existing = new DownloaderConfig();
        existing.setName("existing");
        existing.setEnabled(false);
        existing.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findByName("existing")).thenReturn(Optional.of(existing));
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        // Mock that we have enabled configs so it doesn't try to create default
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(existing));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        DownloaderConfig result = configsService.saveConfig("existing", command);

        assertThat(result.getEnabled()).isTrue();
    }

    @Test
    void saveConfig_ShouldCreateNew_WhenNotExists() {
        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(false);
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        command.setYtDlpConfig(ytDlpConfigCommand);

        when(downloaderConfigRepository.findByName("newConfig")).thenReturn(Optional.empty());
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        // Mock default config check
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(defaultConfig));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        DownloaderConfig result = configsService.saveConfig("newConfig", command);

        assertThat(result.getName()).isEqualTo("newConfig");
    }

    @Test
    void saveConfig_ShouldUpdateAllFields_WhenProvided(@TempDir Path tempDir) throws IOException {
        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(true);
        command.setDuration(123);
        command.setStartDownloadAutomatically(true);
        command.setRemoveCompletedJobAutomatically(true);
        command.setClientId("new-client-id");
        command.setClientSecret("new-client-secret");
        command.setThreadPoolSize(10);

        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        ytDlpConfigCommand.setFormatFiltering("new-filter");
        ytDlpConfigCommand.setFormatSorting("new-sort");
        ytDlpConfigCommand.setRemuxVideo("mkv");
        ytDlpConfigCommand.setWriteDescription(true);
        ytDlpConfigCommand.setWriteSubs(true);
        ytDlpConfigCommand.setSubLang("fr");
        ytDlpConfigCommand.setWriteAutoSubs(true);
        ytDlpConfigCommand.setSubFormat("vtt");
        ytDlpConfigCommand.setOutputTemplate("new-template");
        ytDlpConfigCommand.setOverwrite(OverwriteOption.SKIP);
        ytDlpConfigCommand.setKeepVideo(false);
        ytDlpConfigCommand.setExtractAudio(true);
        ytDlpConfigCommand.setAudioFormat("wav");
        ytDlpConfigCommand.setAudioQuality(9);
        ytDlpConfigCommand.setNoProgress(false);
        ytDlpConfigCommand.setUseCookie(true);
        ytDlpConfigCommand.setCookie("new-cookie");

        command.setYtDlpConfig(ytDlpConfigCommand);

        DownloaderConfig existing = new DownloaderConfig();
        existing.setName("existing");
        existing.setEnabled(false);
        existing.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findByName("existing")).thenReturn(Optional.of(existing));
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(existing));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        DownloaderConfig result = configsService.saveConfig("existing", command);

        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getDuration()).isEqualTo(123);
        assertThat(result.getStartDownloadAutomatically()).isTrue();
        assertThat(result.getRemoveCompletedJobAutomatically()).isTrue();
        assertThat(result.getClientId()).isEqualTo("new-client-id");
        assertThat(result.getClientSecret()).isEqualTo("new-client-secret");
        assertThat(result.getThreadPoolSize()).isEqualTo(10);

        YtDlpConfig resultYtDlp = result.getYtDlpConfig();
        assertThat(resultYtDlp.getFormatFiltering()).isEqualTo("new-filter");
        assertThat(resultYtDlp.getFormatSorting()).isEqualTo("new-sort");
        assertThat(resultYtDlp.getRemuxVideo()).isEqualTo("mkv");
        assertThat(resultYtDlp.getWriteDescription()).isTrue();
        assertThat(resultYtDlp.getWriteSubs()).isTrue();
        assertThat(resultYtDlp.getSubLang()).isEqualTo("fr");
        assertThat(resultYtDlp.getWriteAutoSubs()).isTrue();
        assertThat(resultYtDlp.getSubFormat()).isEqualTo("vtt");
        assertThat(resultYtDlp.getOutputTemplate()).isEqualTo("new-template");
        assertThat(resultYtDlp.getOverwrite()).isEqualTo(OverwriteOption.SKIP);
        assertThat(resultYtDlp.getKeepVideo()).isFalse();
        assertThat(resultYtDlp.getExtractAudio()).isTrue();
        assertThat(resultYtDlp.getAudioFormat()).isEqualTo("wav");
        assertThat(resultYtDlp.getAudioQuality()).isEqualTo(9);
        assertThat(resultYtDlp.getNoProgress()).isFalse();
        assertThat(resultYtDlp.getUseCookie()).isTrue();
    }

    @Test
    void saveConfig_ShouldNotUpdateYtDlpConfig_WhenDtoIsNull(@TempDir Path tempDir) {
        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(true);
        // ytDlpConfig is null by default

        DownloaderConfig existing = new DownloaderConfig();
        existing.setName("existing");
        existing.setEnabled(false);
        YtDlpConfig existingYtDlp = new YtDlpConfig();
        existingYtDlp.setFormatFiltering("old-filter");
        existing.setYtDlpConfig(existingYtDlp);

        when(downloaderConfigRepository.findByName("existing")).thenReturn(Optional.of(existing));
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(existing));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        DownloaderConfig result = configsService.saveConfig("existing", command);

        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getYtDlpConfig().getFormatFiltering()).isEqualTo("old-filter");
    }

    @Test
    void saveConfig_ShouldUpdateCookie(@TempDir Path tempDir) throws IOException {
        UpdateConfigCommand command = new UpdateConfigCommand();
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        ytDlpConfigCommand.setCookie("new-cookie");
        command.setYtDlpConfig(ytDlpConfigCommand);

        DownloaderConfig existing = new DownloaderConfig();
        existing.setName("existing");
        existing.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findByName("existing")).thenReturn(Optional.of(existing));
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(existing));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        configsService.saveConfig("existing", command);

        Path cookiePath = tempDir.resolve("existing-cookie.txt");
        assertThat(Files.exists(cookiePath)).isTrue();
        assertThat(Files.readString(cookiePath)).isEqualTo("new-cookie");
    }

    @Test
    void saveConfig_ShouldDeleteCookie_WhenCookieIsEmpty(@TempDir Path tempDir) throws IOException {
        UpdateConfigCommand command = new UpdateConfigCommand();
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        ytDlpConfigCommand.setCookie("");
        command.setYtDlpConfig(ytDlpConfigCommand);

        DownloaderConfig existing = new DownloaderConfig();
        existing.setName("existing");
        existing.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findByName("existing")).thenReturn(Optional.of(existing));
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(existing));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        Path cookiePath = tempDir.resolve("existing-cookie.txt");
        Files.writeString(cookiePath, "old-cookie");

        configsService.saveConfig("existing", command);

        assertThat(Files.exists(cookiePath)).isFalse();
    }

    @Test
    void saveConfig_ShouldDisableOtherConfigs_WhenEnabled() {
        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(true);
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        command.setYtDlpConfig(ytDlpConfigCommand);

        DownloaderConfig otherConfig = new DownloaderConfig();
        otherConfig.setName("otherConfig");
        otherConfig.setEnabled(true);

        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(otherConfig));

        DownloaderConfig targetConfig = new DownloaderConfig();
        targetConfig.setName("targetConfig");
        targetConfig.setEnabled(false);
        targetConfig.setYtDlpConfig(new YtDlpConfig());
        when(downloaderConfigRepository.findByName("targetConfig")).thenReturn(Optional.of(targetConfig));

        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        configsService.saveConfig("targetConfig", command);

        assertThat(otherConfig.getEnabled()).isFalse();
        assertThat(targetConfig.getEnabled()).isTrue();
    }

    @Test
    void saveConfig_ShouldEnableDefault_WhenNoConfigEnabled() {
        UpdateConfigCommand command = new UpdateConfigCommand();
        command.setEnabled(false);
        YtDlpConfigCommand ytDlpConfigCommand = new YtDlpConfigCommand();
        command.setYtDlpConfig(ytDlpConfigCommand);

        DownloaderConfig myConfig = new DownloaderConfig();
        myConfig.setName("myConfig");
        myConfig.setEnabled(true);
        myConfig.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findByName("myConfig")).thenReturn(Optional.of(myConfig));
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);

        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(Collections.emptyList());

        when(downloaderConfigRepository.findByName("default")).thenReturn(Optional.of(defaultConfig));
        defaultConfig.setEnabled(false);

        when(defaultProperties.getNetscapeCookieFolder()).thenReturn("build/tmp/cookies");

        configsService.saveConfig("myConfig", command);

        assertThat(defaultConfig.getEnabled()).isTrue();
        verify(downloaderConfigRepository).save(Objects.requireNonNull(defaultConfig));
    }

    @Test
    void getResolvedConfig_ShouldPopulateAllMissingFields_WhenFieldsAreNull() {
        String configName = "sparseConfig";
        DownloaderConfig sparseConfig = new DownloaderConfig();
        sparseConfig.setName(configName);
        sparseConfig.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(sparseConfig));
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);

        DownloaderConfig result = configsService.getResolvedConfig(configName);

        assertThat(result.getDuration()).isEqualTo(defaultConfig.getDuration());
        assertThat(result.getStartDownloadAutomatically()).isEqualTo(defaultConfig.getStartDownloadAutomatically());
        assertThat(result.getRemoveCompletedJobAutomatically()).isEqualTo(defaultConfig.getRemoveCompletedJobAutomatically());
        assertThat(result.getClientId()).isEqualTo(defaultConfig.getClientId());
        assertThat(result.getClientSecret()).isEqualTo(defaultConfig.getClientSecret());
        assertThat(result.getThreadPoolSize()).isEqualTo(defaultConfig.getThreadPoolSize());

        YtDlpConfig resultYtDlp = result.getYtDlpConfig();
        YtDlpConfig defaultYtDlp = defaultConfig.getYtDlpConfig();

        assertThat(resultYtDlp.getFormatFiltering()).isEqualTo(defaultYtDlp.getFormatFiltering());
        assertThat(resultYtDlp.getFormatSorting()).isEqualTo(defaultYtDlp.getFormatSorting());
        assertThat(resultYtDlp.getRemuxVideo()).isEqualTo(defaultYtDlp.getRemuxVideo());
        assertThat(resultYtDlp.getWriteDescription()).isEqualTo(defaultYtDlp.getWriteDescription());
        assertThat(resultYtDlp.getWriteSubs()).isEqualTo(defaultYtDlp.getWriteSubs());
        assertThat(resultYtDlp.getSubLang()).isEqualTo(defaultYtDlp.getSubLang());
        assertThat(resultYtDlp.getWriteAutoSubs()).isEqualTo(defaultYtDlp.getWriteAutoSubs());
        assertThat(resultYtDlp.getSubFormat()).isEqualTo(defaultYtDlp.getSubFormat());
        assertThat(resultYtDlp.getOutputTemplate()).isEqualTo(defaultYtDlp.getOutputTemplate());
        assertThat(resultYtDlp.getOverwrite()).isEqualTo(defaultYtDlp.getOverwrite());
        assertThat(resultYtDlp.getKeepVideo()).isEqualTo(defaultYtDlp.getKeepVideo());
        assertThat(resultYtDlp.getExtractAudio()).isEqualTo(defaultYtDlp.getExtractAudio());
        assertThat(resultYtDlp.getAudioFormat()).isEqualTo(defaultYtDlp.getAudioFormat());
        assertThat(resultYtDlp.getAudioQuality()).isEqualTo(defaultYtDlp.getAudioQuality());
        assertThat(resultYtDlp.getNoProgress()).isEqualTo(defaultYtDlp.getNoProgress());
        assertThat(resultYtDlp.getUseCookie()).isEqualTo(defaultYtDlp.getUseCookie());
    }

    @Test
    void getResolvedConfig_ShouldFallbackToDefault_WhenNoConfigFoundAndDbNotEmpty() {
        when(downloaderConfigRepository.findByName("unknown")).thenReturn(Optional.empty());
        when(downloaderConfigRepository.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        when(downloaderConfigRepository.count()).thenReturn(1L);

        when(downloaderConfigRepository.findByName("default")).thenReturn(Optional.empty());
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenReturn(defaultConfig);

        DownloaderConfig result = configsService.getResolvedConfig("unknown");

        assertThat(result).isEqualTo(defaultConfig);
    }

    @Test
    void getResolvedConfig_ShouldNotOverwriteFields_WhenFieldsArePresent() {
        String configName = "fullConfig";
        DownloaderConfig fullConfig = new DownloaderConfig();
        fullConfig.setName(configName);
        fullConfig.setDuration(999);
        fullConfig.setStartDownloadAutomatically(false);
        fullConfig.setRemoveCompletedJobAutomatically(true);
        fullConfig.setClientId("existing-client-id");
        fullConfig.setClientSecret("existing-client-secret");
        fullConfig.setThreadPoolSize(5);

        YtDlpConfig ytDlpConfig = new YtDlpConfig();
        ytDlpConfig.setName(configName);
        ytDlpConfig.setFormatFiltering("existing-filter");
        ytDlpConfig.setFormatSorting("existing-sort");
        ytDlpConfig.setRemuxVideo("mkv");
        ytDlpConfig.setWriteDescription(false);
        ytDlpConfig.setWriteSubs(false);
        ytDlpConfig.setSubLang("fr");
        ytDlpConfig.setWriteAutoSubs(true);
        ytDlpConfig.setSubFormat("vtt");
        ytDlpConfig.setOutputTemplate("existing-template");
        ytDlpConfig.setOverwrite(OverwriteOption.SKIP);
        ytDlpConfig.setKeepVideo(false);
        ytDlpConfig.setExtractAudio(true);
        ytDlpConfig.setAudioFormat("wav");
        ytDlpConfig.setAudioQuality(5);
        ytDlpConfig.setNoProgress(false);
        ytDlpConfig.setUseCookie(true);

        fullConfig.setYtDlpConfig(ytDlpConfig);

        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(fullConfig));
        when(defaultConfigFactory.create(defaultProperties)).thenReturn(defaultConfig);

        DownloaderConfig result = configsService.getResolvedConfig(configName);

        assertThat(result.getDuration()).isEqualTo(999);
        assertThat(result.getStartDownloadAutomatically()).isFalse();
        assertThat(result.getRemoveCompletedJobAutomatically()).isTrue();
        assertThat(result.getClientId()).isEqualTo("existing-client-id");
        assertThat(result.getClientSecret()).isEqualTo("existing-client-secret");
        assertThat(result.getThreadPoolSize()).isEqualTo(5);

        YtDlpConfig resultYtDlp = result.getYtDlpConfig();
        assertThat(resultYtDlp.getFormatFiltering()).isEqualTo("existing-filter");
        assertThat(resultYtDlp.getFormatSorting()).isEqualTo("existing-sort");
        assertThat(resultYtDlp.getRemuxVideo()).isEqualTo("mkv");
        assertThat(resultYtDlp.getWriteDescription()).isFalse();
        assertThat(resultYtDlp.getWriteSubs()).isFalse();
        assertThat(resultYtDlp.getSubLang()).isEqualTo("fr");
        assertThat(resultYtDlp.getWriteAutoSubs()).isTrue();
        assertThat(resultYtDlp.getSubFormat()).isEqualTo("vtt");
        assertThat(resultYtDlp.getOutputTemplate()).isEqualTo("existing-template");
        assertThat(resultYtDlp.getOverwrite()).isEqualTo(OverwriteOption.SKIP);
        assertThat(resultYtDlp.getKeepVideo()).isFalse();
        assertThat(resultYtDlp.getExtractAudio()).isTrue();
        assertThat(resultYtDlp.getAudioFormat()).isEqualTo("wav");
        assertThat(resultYtDlp.getAudioQuality()).isEqualTo(5);
        assertThat(resultYtDlp.getNoProgress()).isFalse();
        assertThat(resultYtDlp.getUseCookie()).isTrue();
    }

    @Test
    void getConfig_ShouldReturnEarlyInReadCookie_WhenYtDlpConfigIsNull() {
        String configName = "noYtDlp";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        config.setYtDlpConfig(null);

        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));

        DownloaderConfig result = configsService.getConfig(configName);

        assertThat(result).isEqualTo(config);
        verify(defaultProperties, never()).getNetscapeCookieFolder();
    }

    @Test
    void getConfig_ShouldLogException_WhenCookieFileReadFails(@TempDir Path tempDir) throws IOException {
        String configName = "badCookie";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);
        config.setYtDlpConfig(new YtDlpConfig());

        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        // Create a directory with the cookie filename to force IOException on readString
        Path badPath = tempDir.resolve(configName + "-cookie.txt");
        Files.createDirectory(badPath);

        DownloaderConfig result = configsService.getConfig(configName);

        assertThat(result).isEqualTo(config);
    }

    @Test
    void createConfig_ShouldHandleNullSavedConfig() {
        CreateConfigCommand command = new CreateConfigCommand();
        command.setName("nullConfig");
        command.setYtDlpConfig(new YtDlpConfigCommand());

        when(downloaderConfigRepository.findByName("nullConfig")).thenReturn(Optional.empty());
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenReturn(null);

        DownloaderConfig result = configsService.createConfig(command);

        assertThat(result).isNull();
    }

    @Test
    void createConfig_ShouldHandleCookieSaveFailureAndCleanup(@TempDir Path tempDir) throws IOException {
        String configName = "failSaveCookie";
        CreateConfigCommand command = new CreateConfigCommand();
        command.setName(configName);
        YtDlpConfigCommand ytDlp = new YtDlpConfigCommand();
        ytDlp.setCookie("some-cookie");
        command.setYtDlpConfig(ytDlp);

        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.empty());
        when(downloaderConfigRepository.save(Objects.requireNonNull(anyDownloaderConfig()))).thenAnswer(i -> i.getArguments()[0]);
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());

        // Create a directory where the file should be to cause IOException on writeString
        Path cookiePath = tempDir.resolve(configName + "-cookie.txt");
        Files.createDirectory(cookiePath);

        configsService.createConfig(command);

        // Verify file (directory) is gone because of cleanup
        assertThat(Files.exists(cookiePath)).isFalse();
    }

    @Test
    void deleteConfig_ShouldHandleCookieDeleteFailure(@TempDir Path tempDir) throws IOException {
        String configName = "failDeleteCookie";
        DownloaderConfig config = new DownloaderConfig();
        config.setName(configName);

        when(downloaderConfigRepository.findByName(configName)).thenReturn(Optional.of(config));
        when(defaultProperties.getNetscapeCookieFolder()).thenReturn(tempDir.toString());
        when(downloaderConfigRepository.findAllByEnabledTrue()).thenReturn(List.of(new DownloaderConfig()));

        // Create a non-empty directory to cause DirectoryNotEmptyException on deleteIfExists
        Path cookiePath = tempDir.resolve(configName + "-cookie.txt");
        Files.createDirectory(cookiePath);
        Files.createFile(cookiePath.resolve("child"));

        configsService.deleteConfig(configName);

        verify(downloaderConfigRepository).delete(config);
        assertThat(Files.exists(cookiePath)).isTrue();
    }

    private DownloaderConfig anyDownloaderConfig() {
        any(DownloaderConfig.class);
        return new DownloaderConfig();
    }
}
