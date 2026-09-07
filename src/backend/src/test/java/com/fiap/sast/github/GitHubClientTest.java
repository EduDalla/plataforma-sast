package com.fiap.sast.github;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class GitHubClientTest {
    @Test void validatesPublicGithubUrlAndReference() {
        assertArrayEquals(new String[]{"acme", "demo"}, GitHubClient.validate("https://github.com/acme/demo.git/", "feature/test"));
        for (var url : new String[]{"http://github.com/acme/demo", "https://evil.com/acme/demo",
                "https://github.com.evil.com/acme/demo", "https://github.com/acme/demo?x=1", "https://github.com/a/.."}) {
            assertThrows(IllegalArgumentException.class, () -> GitHubClient.validate(url, null));
        }
        for (var ref : new String[]{"../main", "a\\b", "main\n"}) {
            assertThrows(IllegalArgumentException.class, () -> GitHubClient.validate("https://github.com/acme/demo", ref));
        }
    }
    @Test void archiveFilteringAndLimits() throws Exception {
        var client = new GitHubClient(); client.maxFile = 1000; client.maxFiles = 2; client.maxExtracted = 5000;
        var files = client.extract(zip("root/src/Safe.java", "root/target/Generated.java", "root/readme.txt"));
        assertEquals(1, files.size()); assertEquals("src/Safe.java", files.getFirst().path());
        assertThrows(SecurityException.class, () -> client.extract(zip("root/../Unsafe.java")));
        client.maxFile = 1;
        assertThrows(GitHubClient.LimitException.class, () -> client.extract(zip("root/Safe.java")));
    }
    private byte[] zip(String... names) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name)); zip.write("class Safe {}".getBytes()); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test void ignoresUnixSymlinkFromCentralDirectory() throws Exception {
        var client = new GitHubClient(); client.maxFile = 1000; client.maxFiles = 2; client.maxExtracted = 5000;
        var bytes = new ByteArrayOutputStream();
        try (var zip = new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(bytes)) {
            var entry = new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("root/Link.java");
            entry.setUnixMode(0120777);
            zip.putArchiveEntry(entry); zip.write("/etc/passwd".getBytes()); zip.closeArchiveEntry();
        }
        assertTrue(client.extract(bytes.toByteArray()).isEmpty());
    }
}
