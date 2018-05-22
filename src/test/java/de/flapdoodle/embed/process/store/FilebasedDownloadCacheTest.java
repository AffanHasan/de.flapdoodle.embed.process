/**
 * Copyright (C) 2011
 *   Michael Mosmann <michael@mosmann.de>
 *   Martin Jöhren <m.joehren@googlemail.com>
 *
 * with contributions from
 * 	konstantin-ba@github,
	Archimedes Trajano (trajano@github),
	Kevin D. Keck (kdkeck@github),
	Ben McCann (benmccann@github)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.flapdoodle.embed.process.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.junit.Test;

import de.flapdoodle.embed.process.HttpServers;
import de.flapdoodle.embed.process.config.store.TimeoutConfig;
import de.flapdoodle.embed.process.io.net.UrlStreams;
import de.flapdoodle.embed.process.io.net.UrlStreams.DownloadCopyListener;
import de.flapdoodle.embed.process.runtime.Network;
import de.flapdoodle.types.Try;

public class FilebasedDownloadCacheTest {

	@Test
	public void pathCouldEscapeDirectory() {
		assertTrue(FilebasedDownloadCache.willEscapeDirectory(Paths.get("..")));
		assertTrue(FilebasedDownloadCache.willEscapeDirectory(Paths.get("bar", "..")));
	}
	
	@Test
	public void pathDoesNotEscapeDirectory() {
		assertFalse(FilebasedDownloadCache.willEscapeDirectory(Paths.get("foo","bar")));
		assertFalse(FilebasedDownloadCache.willEscapeDirectory(Paths.get("foo","..","bar")));
	}
	
	@Test
	public void downloadShouldBeCompleteAndMatchContent() throws IOException {
		int httpPort = Network.getFreeServerPort();
		long contentLengt = 2*1024*1024;
		byte[] content = randomFilledByteArray((int) contentLengt);
		
		Path artifactDirectory = Files.createTempDirectory("artifactStore");
		
		HttpServers.Listener listener=(uri, method, headers, parms, files) -> {
			if (uri.equals("/download")) {
				return Optional.of(HttpServers.response(200, "text/text", content));
			}
			return Optional.empty();
		};
		
		List<Long> downloadSizes = new ArrayList<>();
		
		try (HttpServers.Server server = HttpServers.httpServer(httpPort, listener)) {
			DownloadCopyListener copyListener=(bytesCopied, downloadContentLength) -> {
				downloadSizes.add(bytesCopied);
				assertEquals("contentLengt", contentLengt, downloadContentLength);
			};
			FilebasedDownloadCache cache = new FilebasedDownloadCache(artifactDirectory, url -> Try.supplier(() -> UrlStreams.urlConnectionOf(url, "foo", TimeoutConfig.defaults(), Optional.empty())).mapCheckedException(RuntimeException::new).get(), copyListener);
			
			Path downloaded = cache.getOrDownload(Paths.get("sample"), new URL("http://localhost:"+httpPort+"/download?foo=bar"));
			assertNotNull(downloaded);
			
			// cache should be used
			downloaded = cache.getOrDownload(Paths.get("sample"), new URL("http://localhost:"+httpPort+"/download?foo=bar"));
			
			File asFile = downloaded.toFile();
			assertTrue(asFile.exists());
			assertTrue(asFile.isFile());
			byte[] transferedBytes = Files.readAllBytes(downloaded);
			assertSameContent(content, transferedBytes);
			
			Files.delete(downloaded);
		}
		
		List<Long> downloadSizesMatchingFullDownload = downloadSizes.stream()
		 	.filter(l -> l == contentLengt)
		 	.collect(Collectors.toList());
		
		assertEquals(1,downloadSizesMatchingFullDownload.size());
		
		List<Long> downloadSizesBiggerThanContentLength = downloadSizes.stream()
			.filter(l -> l > contentLengt)
			.collect(Collectors.toList());
		
		assertTrue(downloadSizesBiggerThanContentLength.isEmpty());
	}

	private void assertSameContent(byte[] expected, byte[] result) {
		assertEquals("length", expected.length, result.length);
		for (int i=0;i<expected.length;i++) {
			assertEquals("length", expected[i], result[i]);
		}
	}


	private byte[] randomFilledByteArray(int size) {
		byte[] content = new byte[size];
		for (int i=0;i<content.length;i++) {
			content[i]=(byte) ThreadLocalRandom.current().nextInt();
		}
		return content;
	}

}