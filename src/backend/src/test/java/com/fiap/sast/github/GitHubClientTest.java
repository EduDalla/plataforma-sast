package com.fiap.sast.github;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class GitHubClientTest {
    @DisplayName("BDD-OP-05: origem pública e referência são validadas")
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
    @DisplayName("BDD-OP-06: filtragem, Zip Slip e limite por arquivo")
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

    @DisplayName("BDD-OP-06: link simbólico do archive é ignorado")
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

    @Test
    @DisplayName("BDD-OP-06: archive perigoso e limites de extração são verificados em memória")
    void operationArchiveLimits() throws Exception {
        var client = new GitHubClient();
        client.maxFile = 1000; client.maxFiles = 1; client.maxExtracted = 5000;
        assertThrows(GitHubClient.LimitException.class,
                () -> client.extract(zip("root/A.java", "root/B.java")));
        client.maxFiles = 10;
        client.maxExtracted = 15;
        assertThrows(GitHubClient.LimitException.class,
                () -> client.extract(zip("root/readme.txt", "root/A.java")));
        client.maxExtracted = 5000;
        assertThrows(SecurityException.class, () -> client.extract(zip("/absolute.java")));
        assertThrows(SecurityException.class, () -> client.extract(zip("root\\Unsafe.java")));
        assertTrue(client.extract(zip("root/target/A.java", "root/build/B.java", "root/out/C.java",
                "root/.gradle/D.java", "root/node_modules/E.java", "root/vendor/F.java")).isEmpty());
        client.maxExtracted = 500000;
        var entries = java.util.stream.IntStream.range(0, 10001)
                .mapToObj(index -> "root/entry" + index + ".txt").toArray(String[]::new);
        assertThrows(GitHubClient.LimitException.class, () -> client.extract(zip(entries)));
    }
}
