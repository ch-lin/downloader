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
package ch.lin.downloader.backend.api.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ch.lin.downloader.backend.api.domain.OverwriteOption;

class DownloaderDefaultPropertiesTest {

    @Test
    @DisplayName("Should have correct default values")
    void testDefaultValues() {
        DownloaderDefaultProperties properties = new DownloaderDefaultProperties();

        assertEquals(60, properties.getDuration());
        assertEquals(3, properties.getThreadPoolSize());
        assertTrue(properties.isStartDownloadAutomatically());
        assertFalse(properties.isRemoveCompletedJobAutomatically());
        assertNotNull(properties.getYtdlp());

        // Ytdlp defaults
        DownloaderDefaultProperties.Ytdlp ytdlp = properties.getYtdlp();
        assertEquals("", ytdlp.getRemuxVideo());
        assertTrue(ytdlp.isWriteDescription());
        assertTrue(ytdlp.isWriteSubs());
        assertEquals("ja.*", ytdlp.getSubLang());
        assertTrue(ytdlp.isWriteAutoSubs());
        assertEquals("srt", ytdlp.getSubFormat());
        assertEquals("%(title)s.%(ext)s", ytdlp.getOutputTemplate());
        assertFalse(ytdlp.isKeepVideo());
        assertFalse(ytdlp.isExtractAudio());
        assertEquals("m4a", ytdlp.getAudioFormat());
        assertEquals(0, ytdlp.getAudioQuality());
        assertEquals(OverwriteOption.DEFAULT, ytdlp.getOverwrite());
        assertFalse(ytdlp.isNoProgress());
        assertFalse(ytdlp.isUseCookie());
    }

    @Test
    @DisplayName("Should verify Getters and Setters")
    void testGettersAndSetters() {
        DownloaderDefaultProperties properties = new DownloaderDefaultProperties();

        properties.setDownloadFolder("/tmp/downloads");
        properties.setNetscapeCookieFolder("/tmp/cookies");
        properties.setDuration(120);
        properties.setStartDownloadAutomatically(false);
        properties.setRemoveCompletedJobAutomatically(true);
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setThreadPoolSize(5);

        assertEquals("/tmp/downloads", properties.getDownloadFolder());
        assertEquals("/tmp/cookies", properties.getNetscapeCookieFolder());
        assertEquals(120, properties.getDuration());
        assertFalse(properties.isStartDownloadAutomatically());
        assertTrue(properties.isRemoveCompletedJobAutomatically());
        assertEquals("client-id", properties.getClientId());
        assertEquals("client-secret", properties.getClientSecret());
        assertEquals(5, properties.getThreadPoolSize());

        // Ytdlp setters
        DownloaderDefaultProperties.Ytdlp ytdlp = properties.getYtdlp();
        ytdlp.setFormatSorting("res:1080");
        ytdlp.setFormatFiltering("best");
        ytdlp.setRemuxVideo("mkv");
        ytdlp.setWriteDescription(false);
        ytdlp.setWriteSubs(false);
        ytdlp.setSubLang("en");
        ytdlp.setWriteAutoSubs(false);
        ytdlp.setSubFormat("vtt");
        ytdlp.setOutputTemplate("test.mp4");
        ytdlp.setKeepVideo(true);
        ytdlp.setExtractAudio(true);
        ytdlp.setAudioFormat("mp3");
        ytdlp.setAudioQuality(5);
        ytdlp.setOverwrite(OverwriteOption.FORCE);
        ytdlp.setNoProgress(true);
        ytdlp.setUseCookie(true);

        assertEquals("res:1080", ytdlp.getFormatSorting());
        assertEquals("best", ytdlp.getFormatFiltering());
        assertEquals("mkv", ytdlp.getRemuxVideo());
        assertFalse(ytdlp.isWriteDescription());
        assertFalse(ytdlp.isWriteSubs());
        assertEquals("en", ytdlp.getSubLang());
        assertFalse(ytdlp.isWriteAutoSubs());
        assertEquals("vtt", ytdlp.getSubFormat());
        assertEquals("test.mp4", ytdlp.getOutputTemplate());
        assertTrue(ytdlp.isKeepVideo());
        assertTrue(ytdlp.isExtractAudio());
        assertEquals("mp3", ytdlp.getAudioFormat());
        assertEquals(5, ytdlp.getAudioQuality());
        assertEquals(OverwriteOption.FORCE, ytdlp.getOverwrite());
        assertTrue(ytdlp.isNoProgress());
        assertTrue(ytdlp.isUseCookie());
    }

    @Test
    @DisplayName("Should verify Ytdlp Equals and HashCode")
    void testYtdlpEqualsAndHashCode() {
        DownloaderDefaultProperties.Ytdlp y1 = new DownloaderDefaultProperties.Ytdlp();
        DownloaderDefaultProperties.Ytdlp y2 = new DownloaderDefaultProperties.Ytdlp();
        DownloaderDefaultProperties.Ytdlp y3 = new DownloaderDefaultProperties.Ytdlp();
        y3.setFormatSorting("res:720");

        assertEquals(y1, y2);
        assertNotEquals(y1, y3);
        assertEquals(y1.hashCode(), y2.hashCode());
        assertNotEquals(y1.hashCode(), y3.hashCode());
    }

    @Test
    @DisplayName("Should verify ToString")
    void testToString() {
        DownloaderDefaultProperties properties = new DownloaderDefaultProperties();
        assertNotNull(properties.toString());
        assertNotNull(properties.getYtdlp().toString());
    }
}
