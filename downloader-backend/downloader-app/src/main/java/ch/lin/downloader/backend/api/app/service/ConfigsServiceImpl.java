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
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import ch.lin.downloader.backend.api.app.config.DefaultConfigFactory;
import ch.lin.downloader.backend.api.app.config.DownloaderDefaultProperties;
import ch.lin.downloader.backend.api.app.repository.DownloaderConfigRepository;
import ch.lin.downloader.backend.api.app.repository.YtDlpConfigRepository;
import ch.lin.downloader.backend.api.app.service.command.CreateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.UpdateConfigCommand;
import ch.lin.downloader.backend.api.app.service.command.YtDlpConfigCommand;
import ch.lin.downloader.backend.api.app.service.model.AllConfigsData;
import ch.lin.downloader.backend.api.domain.DownloaderConfig;
import ch.lin.downloader.backend.api.domain.YtDlpConfig;
import ch.lin.platform.exception.ConfigCreationException;
import ch.lin.platform.exception.ConfigNotFoundException;
import ch.lin.platform.exception.InvalidRequestException;

/**
 * Service for managing yt-dlp configurations.
 * <p>
 * This service implements the business logic for creating, reading, updating,
 * and deleting {@link DownloaderConfig} entities. It ensures that there is
 * always a 'default' configuration available, falling back to application
 * properties if needed. It also manages the 'enabled' state of configurations,
 * ensuring that only one can be active at a time.
 */
@Service
public class ConfigsServiceImpl implements ConfigsService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigsServiceImpl.class);

    private final YtDlpConfigRepository ytDlpConfigRepository;
    private final DownloaderConfigRepository downloaderConfigRepository;
    private final DownloaderDefaultProperties defaultProperties;
    private final DefaultConfigFactory defaultConfigFactory;

    /**
     * Constructs a new ConfigsServiceImpl with the necessary dependencies.
     *
     * @param ytDlpConfigRepository Repository for YtDlpConfig entities.
     * @param downloaderConfigRepository Repository for DownloaderConfig
     * entities.
     * @param defaultProperties Default properties for the downloader.
     * @param defaultConfigFactory Factory for creating default configurations.
     */
    public ConfigsServiceImpl(YtDlpConfigRepository ytDlpConfigRepository,
            DownloaderConfigRepository downloaderConfigRepository, DownloaderDefaultProperties defaultProperties,
            DefaultConfigFactory defaultConfigFactory) {
        this.ytDlpConfigRepository = ytDlpConfigRepository;
        this.downloaderConfigRepository = downloaderConfigRepository;
        this.defaultProperties = defaultProperties;
        this.defaultConfigFactory = defaultConfigFactory;
    }

    /**
     * Retrieves all available configuration names and identifies the currently
     * enabled configuration.
     *
     * @return An {@link AllConfigsData} containing the name of the enabled
     * configuration and a list of all configuration names.
     * @throws ConfigCreationException If the default configuration cannot be
     * created.
     */
    @Override
    public AllConfigsData getAllConfigs() {
        if (downloaderConfigRepository.count() == 0) {
            findOrCreateDefaultConfig();
        }
        List<String> allNames = downloaderConfigRepository.findAll()
                .stream()
                .map(DownloaderConfig::getName).collect(Collectors.toList());

        String enabledConfigName = downloaderConfigRepository.findFirstByEnabledTrue()
                .map(DownloaderConfig::getName)
                .orElse("default"); // Fallback to 'default' if no config is explicitly enabled
        return new AllConfigsData(enabledConfigName, allNames);
    }

    /**
     * Creates a new configuration based on the provided request.
     * <p>
     * The 'default' configuration name is reserved and cannot be used. If the
     * new configuration is set to be enabled, all other configurations will be
     * disabled.
     *
     * @param request The request containing the details for the new
     * configuration.
     * @return The created {@link DownloaderConfig}.
     * @throws InvalidRequestException If the name is 'default' or if a
     * configuration with the same name already exists.
     */
    @Override
    @Transactional
    public DownloaderConfig createConfig(CreateConfigCommand request) {
        String configName = request.getName();
        if ("default".equalsIgnoreCase(configName)) {
            throw new InvalidRequestException(
                    "The 'default' configuration is system-reserved and cannot be created manually.");
        }
        downloaderConfigRepository.findByName(configName).ifPresent(c -> {
            throw new InvalidRequestException("Configuration with name '" + configName + "' already exists.");
        });

        // If this new config is being enabled, disable all others first.
        if (Boolean.TRUE.equals(request.getEnabled())) {
            List<DownloaderConfig> enabledConfigs = downloaderConfigRepository.findAllByEnabledTrue();
            enabledConfigs.forEach(config -> config.setEnabled(false));
            downloaderConfigRepository.saveAll(enabledConfigs);
        }

        DownloaderConfig newDownloaderConfig = new DownloaderConfig();
        newDownloaderConfig.setName(configName);
        newDownloaderConfig.setEnabled(request.getEnabled());
        newDownloaderConfig.setDuration(request.getDuration());
        newDownloaderConfig.setStartDownloadAutomatically(request.getStartDownloadAutomatically());
        newDownloaderConfig.setRemoveCompletedJobAutomatically(request.getRemoveCompletedJobAutomatically());
        newDownloaderConfig.setClientId(request.getClientId());
        newDownloaderConfig.setClientSecret(request.getClientSecret());
        newDownloaderConfig.setThreadPoolSize(request.getThreadPoolSize());

        YtDlpConfigCommand ytDlpConfigDto = request.getYtDlpConfig();
        YtDlpConfig newYtDlpConfig = new YtDlpConfig();
        newYtDlpConfig.setName(configName); // Match the name for the relationship
        newYtDlpConfig.setFormatFiltering(ytDlpConfigDto.getFormatFiltering());
        newYtDlpConfig.setFormatSorting(ytDlpConfigDto.getFormatSorting());
        newYtDlpConfig.setRemuxVideo(ytDlpConfigDto.getRemuxVideo());
        newYtDlpConfig.setWriteDescription(ytDlpConfigDto.getWriteDescription());
        newYtDlpConfig.setWriteSubs(ytDlpConfigDto.getWriteSubs());
        newYtDlpConfig.setSubLang(ytDlpConfigDto.getSubLang());
        newYtDlpConfig.setWriteAutoSubs(ytDlpConfigDto.getWriteAutoSubs());
        newYtDlpConfig.setSubFormat(ytDlpConfigDto.getSubFormat());
        newYtDlpConfig.setOutputTemplate(ytDlpConfigDto.getOutputTemplate());
        newYtDlpConfig.setOverwrite(ytDlpConfigDto.getOverwrite());
        newYtDlpConfig.setKeepVideo(ytDlpConfigDto.getKeepVideo());
        newYtDlpConfig.setExtractAudio(ytDlpConfigDto.getExtractAudio());
        newYtDlpConfig.setAudioFormat(ytDlpConfigDto.getAudioFormat());
        newYtDlpConfig.setAudioQuality(ytDlpConfigDto.getAudioQuality());
        newYtDlpConfig.setNoProgress(ytDlpConfigDto.getNoProgress());
        newYtDlpConfig.setUseCookie(ytDlpConfigDto.getUseCookie());

        newDownloaderConfig.setYtDlpConfig(newYtDlpConfig);

        // Handle cookie content
        if (StringUtils.hasText(ytDlpConfigDto.getCookie())) {
            saveCookieToFile(configName, ytDlpConfigDto.getCookie());
        }

        DownloaderConfig savedConfig = downloaderConfigRepository.save(newDownloaderConfig);
        readCookieContent(savedConfig);
        return savedConfig;
    }

    /**
     * Deletes all configurations from the database and removes their associated
     * cookie files.
     */
    @Override
    @Transactional
    public void deleteAllConfigs() {
        logger.info("Deleting all configurations and associated cookie files.");

        // Find all configs to delete their associated cookie files.
        List<DownloaderConfig> allConfigs = downloaderConfigRepository.findAll();
        for (DownloaderConfig config : allConfigs) {
            deleteCookieFile(config.getName());
        }

        ytDlpConfigRepository.cleanTable();
        downloaderConfigRepository.cleanTable();
    }

    /**
     * Deletes a specific configuration by name.
     * <p>
     * The 'default' configuration cannot be deleted. If the deleted
     * configuration was the only enabled one, the 'default' configuration is
     * automatically enabled.
     *
     * @param name The name of the configuration to delete.
     * @throws InvalidRequestException If the name is 'default'.
     * @throws ConfigNotFoundException If the configuration with the given name
     * does not exist.
     * @throws ConfigCreationException If the default configuration cannot be
     * created.
     */
    @Override
    @Transactional
    public void deleteConfig(String name) {
        if ("default".equalsIgnoreCase(name)) {
            throw new InvalidRequestException("The 'default' configuration is system-reserved and cannot be deleted.");
        }
        DownloaderConfig config = downloaderConfigRepository.findByName(name)
                .orElseThrow(() -> new ConfigNotFoundException("Configuration with name '" + name + "' not found."));

        // Delete associated cookie file before deleting the configuration from the
        // database.
        deleteCookieFile(name);
        downloaderConfigRepository.delete(Objects.requireNonNull(config));

        // After deleting, if no other configuration is enabled, enable the default one.
        if (downloaderConfigRepository.findAllByEnabledTrue().isEmpty()) {
            DownloaderConfig defaultConfig = findOrCreateDefaultConfig();
            if (!Boolean.TRUE.equals(defaultConfig.getEnabled())) {
                defaultConfig.setEnabled(true);
                downloaderConfigRepository.save(defaultConfig);
                logger.info("Enabled 'default' configuration as no other configuration was active.");
            }
        }
    }

    /**
     * Retrieves a specific configuration by name.
     * <p>
     * If the name is 'default' and it does not exist, it will be created. The
     * associated cookie content is read from the file system and populated in
     * the returned object.
     *
     * @param name The name of the configuration to retrieve.
     * @return The requested {@link DownloaderConfig}.
     * @throws ConfigNotFoundException If the configuration is not found.
     * @throws ConfigCreationException If the default configuration cannot be
     * created.
     */
    @Override
    public DownloaderConfig getConfig(String name) {
        DownloaderConfig config = downloaderConfigRepository.findByName(name)
                .or(() -> "default".equals(name) ? Optional.of(findOrCreateDefaultConfig()) : Optional.empty())
                .orElseThrow(() -> new ConfigNotFoundException("Configuration with name '" + name + "' not found."));
        readCookieContent(config);
        return config;
    }

    /**
     * Updates an existing configuration or creates a new one if it doesn't
     * exist.
     * <p>
     * If the configuration is set to be enabled, all other configurations are
     * disabled. This method handles partial updates for {@link YtDlpConfig}
     * fields.
     *
     * @param name The name of the configuration to update.
     * @param request The request containing the fields to update.
     * @return The updated {@link DownloaderConfig}.
     * @throws ConfigCreationException If the default configuration cannot be
     * created.
     */
    @Override
    @Transactional
    public DownloaderConfig saveConfig(String name, UpdateConfigCommand request) {
        // If this config is being enabled, disable all others first.
        if (Boolean.TRUE.equals(request.getEnabled())) {
            List<DownloaderConfig> enabledConfigs = downloaderConfigRepository.findAllByEnabledTrue();
            for (DownloaderConfig enabledConfig : enabledConfigs) {
                if (!enabledConfig.getName().equals(name)) {
                    enabledConfig.setEnabled(false);
                }
            }
            downloaderConfigRepository.saveAll(Objects.requireNonNull(enabledConfigs));
        }

        DownloaderConfig downloaderConfig = downloaderConfigRepository.findByName(name)
                .orElseGet(() -> {
                    DownloaderConfig newConfig = new DownloaderConfig();
                    newConfig.setName(name);
                    YtDlpConfig newYtDlpConfig = new YtDlpConfig();
                    newYtDlpConfig.setName(name); // Match name for relationship
                    newConfig.setYtDlpConfig(newYtDlpConfig);
                    return newConfig;
                });

        // Update DownloaderConfig fields
        if (request.getEnabled() != null) {
            downloaderConfig.setEnabled(request.getEnabled());
        }
        if (request.getDuration() != null) {
            downloaderConfig.setDuration(request.getDuration());
        }
        if (request.getStartDownloadAutomatically() != null) {
            downloaderConfig.setStartDownloadAutomatically(request.getStartDownloadAutomatically());
        }
        if (request.getRemoveCompletedJobAutomatically() != null) {
            downloaderConfig.setRemoveCompletedJobAutomatically(request.getRemoveCompletedJobAutomatically());
        }
        if (request.getClientId() != null) {
            downloaderConfig.setClientId(request.getClientId());
        }
        if (request.getClientSecret() != null) {
            downloaderConfig.setClientSecret(request.getClientSecret());
        }
        if (request.getThreadPoolSize() != null) {
            downloaderConfig.setThreadPoolSize(request.getThreadPoolSize());
        }

        // Update the associated YtDlpConfig fields
        YtDlpConfig ytDlpConfig = downloaderConfig.getYtDlpConfig();
        YtDlpConfigCommand ytDlpConfigDto = request.getYtDlpConfig();
        if (ytDlpConfigDto != null) {
            // Allow unsetting the value by passing an empty string
            if (ytDlpConfigDto.getFormatFiltering() != null) {
                ytDlpConfig.setFormatFiltering(ytDlpConfigDto.getFormatFiltering());
            }
            if (ytDlpConfigDto.getFormatSorting() != null) {
                ytDlpConfig.setFormatSorting(ytDlpConfigDto.getFormatSorting());
            }
            if (ytDlpConfigDto.getRemuxVideo() != null) {
                ytDlpConfig.setRemuxVideo(ytDlpConfigDto.getRemuxVideo());
            }
            if (ytDlpConfigDto.getWriteDescription() != null) {
                ytDlpConfig.setWriteDescription(ytDlpConfigDto.getWriteDescription());
            }
            if (ytDlpConfigDto.getWriteSubs() != null) {
                ytDlpConfig.setWriteSubs(ytDlpConfigDto.getWriteSubs());
            }
            if (ytDlpConfigDto.getSubLang() != null) {
                ytDlpConfig.setSubLang(ytDlpConfigDto.getSubLang());
            }
            if (ytDlpConfigDto.getWriteAutoSubs() != null) {
                ytDlpConfig.setWriteAutoSubs(ytDlpConfigDto.getWriteAutoSubs());
            }
            if (ytDlpConfigDto.getSubFormat() != null) {
                ytDlpConfig.setSubFormat(ytDlpConfigDto.getSubFormat());
            }
            if (ytDlpConfigDto.getOutputTemplate() != null) {
                ytDlpConfig.setOutputTemplate(ytDlpConfigDto.getOutputTemplate());
            }
            if (ytDlpConfigDto.getOverwrite() != null) {
                ytDlpConfig.setOverwrite(ytDlpConfigDto.getOverwrite());
            }
            if (ytDlpConfigDto.getKeepVideo() != null) {
                ytDlpConfig.setKeepVideo(ytDlpConfigDto.getKeepVideo());
            }
            if (ytDlpConfigDto.getExtractAudio() != null) {
                ytDlpConfig.setExtractAudio(ytDlpConfigDto.getExtractAudio());
            }
            if (ytDlpConfigDto.getAudioFormat() != null) {
                ytDlpConfig.setAudioFormat(ytDlpConfigDto.getAudioFormat());
            }
            if (ytDlpConfigDto.getAudioQuality() != null) {
                ytDlpConfig.setAudioQuality(ytDlpConfigDto.getAudioQuality());
            }
            if (ytDlpConfigDto.getNoProgress() != null) {
                ytDlpConfig.setNoProgress(ytDlpConfigDto.getNoProgress());
            }
            if (ytDlpConfigDto.getUseCookie() != null) {
                ytDlpConfig.setUseCookie(ytDlpConfigDto.getUseCookie());
            }

            // Handle cookie content, allowing for deletion with an empty string
            if (ytDlpConfigDto.getCookie() != null) {
                handleCookieUpdate(name, ytDlpConfigDto.getCookie());
            }
        }

        DownloaderConfig savedConfig = downloaderConfigRepository.save(downloaderConfig);

        // After saving, if no configuration is enabled, enable the default one.
        if (downloaderConfigRepository.findAllByEnabledTrue().isEmpty()) {
            DownloaderConfig defaultConfig = findOrCreateDefaultConfig();
            defaultConfig.setEnabled(true);
            downloaderConfigRepository.save(defaultConfig);
        }
        readCookieContent(savedConfig);
        return savedConfig;
    }

    /**
     * Retrieves the active download configuration, falling back to defaults if
     * necessary.
     * <p>
     * The selection logic is as follows: 1. If a {@code configName} is
     * provided, it attempts to find that specific configuration. 2. If not
     * found or not provided, it searches for the first enabled configuration in
     * the database. 3. If no configurations exist, it creates and saves a new
     * "default" one. 4. The resolved configuration is merged with default
     * properties to ensure all fields are populated.
     *
     * @param configName The name of the desired configuration (can be null or
     * empty).
     * @return The fully resolved {@link DownloaderConfig} to use for a
     * download.
     * @throws ConfigCreationException If the default configuration cannot be
     * created.
     */
    @Override
    @Transactional
    public DownloaderConfig getResolvedConfig(String configName) {
        Optional<DownloaderConfig> configOpt = Optional.empty();

        if (StringUtils.hasText(configName)) {
            configOpt = downloaderConfigRepository.findByName(configName);
        }

        if (configOpt.isEmpty()) {
            logger.debug("Config '{}' not found or not specified. Searching for a default enabled config.", configName);
            configOpt = downloaderConfigRepository.findFirstByEnabledTrue();
        }

        // If still no config, and the DB is empty, create the 'default' one.
        if (configOpt.isEmpty() && downloaderConfigRepository.count() == 0) {
            return findOrCreateDefaultConfig();
        }

        // Use the found config or fall back to application properties if no config is
        // active.
        return configOpt.map(dbConfig -> {
            logger.debug("Using config '{}' from database.", dbConfig.getName());
            DownloaderConfig defaultConfig = defaultConfigFactory.create(defaultProperties);
            YtDlpConfig defaultYtDlpConfig = defaultConfig.getYtDlpConfig();

            if (dbConfig.getDuration() == null) {
                dbConfig.setDuration(defaultConfig.getDuration());
            }
            if (dbConfig.getStartDownloadAutomatically() == null) {
                dbConfig.setStartDownloadAutomatically(defaultConfig.getStartDownloadAutomatically());
            }

            if (dbConfig.getRemoveCompletedJobAutomatically() == null) {
                dbConfig.setRemoveCompletedJobAutomatically(defaultConfig.getRemoveCompletedJobAutomatically());
            }

            if (!StringUtils.hasText(dbConfig.getClientId())) {
                dbConfig.setClientId(defaultConfig.getClientId());
            }
            if (!StringUtils.hasText(dbConfig.getClientSecret())) {
                dbConfig.setClientSecret(defaultConfig.getClientSecret());
            }
            if (dbConfig.getThreadPoolSize() == null) {
                dbConfig.setThreadPoolSize(defaultConfig.getThreadPoolSize());
            }

            YtDlpConfig dbYtDlpConfig = dbConfig.getYtDlpConfig();
            // Fallback to default properties if the config values are blank
            if (!StringUtils.hasText(dbYtDlpConfig.getFormatFiltering())) {
                dbYtDlpConfig.setFormatFiltering(defaultYtDlpConfig.getFormatFiltering());
            }
            if (!StringUtils.hasText(dbYtDlpConfig.getFormatSorting())) {
                dbYtDlpConfig.setFormatSorting(defaultYtDlpConfig.getFormatSorting());
            }
            if (!StringUtils.hasText(dbYtDlpConfig.getRemuxVideo())) {
                dbYtDlpConfig.setRemuxVideo(defaultYtDlpConfig.getRemuxVideo());
            }
            if (dbYtDlpConfig.getWriteDescription() == null) {
                dbYtDlpConfig.setWriteDescription(defaultYtDlpConfig.getWriteDescription());
            }
            if (dbYtDlpConfig.getWriteSubs() == null) {
                dbYtDlpConfig.setWriteSubs(defaultYtDlpConfig.getWriteSubs());
            }
            if (!StringUtils.hasText(dbYtDlpConfig.getSubLang())) {
                dbYtDlpConfig.setSubLang(defaultYtDlpConfig.getSubLang());
            }
            if (dbYtDlpConfig.getWriteAutoSubs() == null) {
                dbYtDlpConfig.setWriteAutoSubs(defaultYtDlpConfig.getWriteAutoSubs());
            }
            if (!StringUtils.hasText(dbYtDlpConfig.getSubFormat())) {
                dbYtDlpConfig.setSubFormat(defaultYtDlpConfig.getSubFormat());
            }
            if (!StringUtils.hasText(dbYtDlpConfig.getOutputTemplate())) {
                dbYtDlpConfig.setOutputTemplate(defaultYtDlpConfig.getOutputTemplate());
            }
            if (dbYtDlpConfig.getOverwrite() == null) {
                dbYtDlpConfig.setOverwrite(defaultYtDlpConfig.getOverwrite());
            }
            if (dbYtDlpConfig.getKeepVideo() == null) {
                dbYtDlpConfig.setKeepVideo(defaultYtDlpConfig.getKeepVideo());
            }
            if (dbYtDlpConfig.getExtractAudio() == null) {
                dbYtDlpConfig.setExtractAudio(defaultYtDlpConfig.getExtractAudio());
            }
            if (!StringUtils.hasText(dbYtDlpConfig.getAudioFormat())) {
                dbYtDlpConfig.setAudioFormat(defaultYtDlpConfig.getAudioFormat());
            }
            if (dbYtDlpConfig.getAudioQuality() == null) {
                dbYtDlpConfig.setAudioQuality(defaultYtDlpConfig.getAudioQuality());
            }
            if (dbYtDlpConfig.getNoProgress() == null) {
                dbYtDlpConfig.setNoProgress(defaultYtDlpConfig.getNoProgress());
            }
            if (dbYtDlpConfig.getUseCookie() == null) {
                dbYtDlpConfig.setUseCookie(defaultYtDlpConfig.getUseCookie());
            }
            return dbConfig;
        }).orElseGet(() -> {
            logger.debug("No active config found in the database. Using default application properties.");
            return findOrCreateDefaultConfig();
        });
    }

    /**
     * Finds the 'default' configuration in the database or creates it if it
     * does not exist. This method is idempotent and ensures a default
     * configuration is always available.
     *
     * @return The 'default' {@link DownloaderConfig} entity.
     * @throws ConfigCreationException If the default configuration cannot be
     * created.
     */
    private DownloaderConfig findOrCreateDefaultConfig() {
        return downloaderConfigRepository.findByName("default").orElseGet(() -> {
            logger.info("No 'default' configuration found. Creating one with application properties.");
            DownloaderConfig defaultDownloaderConfig = defaultConfigFactory.create(defaultProperties);
            try {
                return downloaderConfigRepository.save(Objects.requireNonNull(defaultDownloaderConfig));
            } catch (Exception e) {
                logger.error("Failed to create default config", e);
                throw new ConfigCreationException("Cannot create default config. Application properties not found.");
            }
        });
    }

    /**
     * Reads the cookie content from the file system and populates the
     * configuration object.
     *
     * @param config The configuration object to update with cookie content.
     */
    private void readCookieContent(DownloaderConfig config) {
        if (config == null || config.getYtDlpConfig() == null) {
            return;
        }
        try {
            Path cookiePath = Paths.get(defaultProperties.getNetscapeCookieFolder(), config.getName() + "-cookie.txt");
            if (Files.exists(cookiePath)) {
                String cookieContent = Files.readString(cookiePath);
                config.getYtDlpConfig().setCookie(cookieContent);
            }
        } catch (IOException e) {
            logger.error("Failed to read cookie file for config '{}'", config.getName(), e);
        }
    }

    /**
     * Handles updates to the cookie content, saving or deleting the file as
     * appropriate.
     *
     * @param configName The name of the configuration.
     * @param cookieContent The new cookie content. If empty or null, the file
     * is deleted.
     */
    private void handleCookieUpdate(String configName, String cookieContent) {
        if (StringUtils.hasText(cookieContent)) {
            saveCookieToFile(configName, cookieContent);
        } else {
            // If the cookie content is an empty string, delete the file.
            deleteCookieFile(configName);
        }
    }

    /**
     * Saves the provided cookie content to a file associated with the
     * configuration name.
     *
     * @param configName The name of the configuration.
     * @param cookieContent The content to write to the file.
     */
    private void saveCookieToFile(String configName, String cookieContent) {
        try {
            Path cookiePath = Paths.get(defaultProperties.getNetscapeCookieFolder(), configName + "-cookie.txt");
            Files.createDirectories(cookiePath.getParent());
            Files.writeString(cookiePath, cookieContent);
            logger.info("Saved cookie file for config '{}' at {}", configName, cookiePath);
        } catch (IOException e) {
            logger.error("Failed to save cookie file for config '{}'", configName, e);
            deleteCookieFile(configName, true); // Attempt to delete the potentially corrupted file to avoid issues.
        }
    }

    /**
     * Deletes the cookie file associated with the given configuration name.
     *
     * @param configName The name of the configuration.
     */
    private void deleteCookieFile(String configName) {
        deleteCookieFile(configName, false);
    }

    /**
     * Deletes the cookie file associated with the given configuration name,
     * with optional cleanup logging.
     *
     * @param configName The name of the configuration.
     * @param isCleanup True if this is a cleanup operation (logs as warning),
     * false otherwise (logs as info).
     */
    private void deleteCookieFile(String configName, boolean isCleanup) {
        try {
            Path cookiePath = Paths.get(defaultProperties.getNetscapeCookieFolder(), configName + "-cookie.txt");
            if (Files.deleteIfExists(cookiePath)) {
                if (isCleanup) {
                    logger.warn("Deleted potentially corrupted cookie file for config '{}'", configName);
                } else {
                    logger.info("Deleted cookie file for config '{}'", configName);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to delete cookie file for config '{}'", configName, e);
        }
    }
}
