package com.fiap.sast.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class GitHubClient {
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15)).build();
    @Value("${sast.github.token:}") String token;
    @Value("${sast.github.max-archive-bytes}") long maxArchive;
    @Value("${sast.github.max-java-files}") int maxFiles;
    @Value("${sast.github.max-file-bytes}") long maxFile;
    @Value("${sast.github.max-extracted-bytes}") long maxExtracted;
    public record Snapshot(String owner, String repo, String url, String reference, List<File> files) {}
    public record File(String path, String content) {}

    public Snapshot download(String url, String ref) {
        try {
            var parts = validate(url, ref);
            var base = "https://api.github.com/repos/" + parts[0] + "/" + parts[1];
            // A token não pode ampliar o escopo da CP1 para repositórios privados.
            var metadata = http.send(apiRequest(URI.create(base), false), HttpResponse.BodyHandlers.discarding());
            checkStatus(metadata.statusCode());
            if (metadata.statusCode() != 200) throw new UnavailableException();
            var archive = URI.create(base + "/zipball" + (ref == null || ref.isBlank() ? "" :
                    "/" + URLEncoder.encode(ref, StandardCharsets.UTF_8).replace("+", "%20")));
            var first = http.send(apiRequest(archive, true), HttpResponse.BodyHandlers.discarding());
            checkStatus(first.statusCode());
            if (first.statusCode() != 302) throw new UnavailableException();
            var redirect = URI.create(first.headers().firstValue("Location").orElseThrow(UnavailableException::new));
            if (!"https".equals(redirect.getScheme()) || !"codeload.github.com".equals(redirect.getHost())
                    || redirect.getPort() != -1 || redirect.getUserInfo() != null) throw new SecurityException();
            var response = http.send(HttpRequest.newBuilder(redirect).timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "fiap-sast-cp1").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                checkStatus(response.statusCode());
                if (response.statusCode() != 200) throw new UnavailableException();
                return new Snapshot(parts[0], parts[1], "https://github.com/" + parts[0] + "/" + parts[1],
                        ref == null || ref.isBlank() ? null : ref, extract(readLimited(body, maxArchive)));
            }
        } catch (IllegalArgumentException | NoSuchElementException | RateLimitException | LimitException
                 | SecurityException | UnavailableException e) { throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new UnavailableException();
        } catch (Exception e) { throw new UnavailableException(); }
    }

    private HttpRequest apiRequest(URI uri, boolean authenticated) {
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "fiap-sast-cp1");
        if (authenticated && token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        return builder.GET().build();
    }

    static String[] validate(String raw, String ref) {
        var uri = URI.create(raw);
        if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || uri.getUserInfo() != null
                || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("URL de repositório inválida");
        String path = uri.getRawPath().replaceFirst("/$", "").replaceFirst("\\.git$", "");
        if (!path.matches("/[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9_.-]{1,100}"))
            throw new IllegalArgumentException("URL de repositório inválida");
        var parts = path.substring(1).split("/");
        if (parts[1].equals(".") || parts[1].equals("..")) throw new IllegalArgumentException("Repositório inválido");
        if (ref != null && !ref.isBlank() && (ref.length() > 255 || ref.contains("..")
                || !ref.matches("[A-Za-z0-9_./-]+"))) throw new IllegalArgumentException("Referência inválida");
        return parts;
    }

    List<File> extract(byte[] archive) throws IOException {
        var out = new ArrayList<File>();
        long total = 0; int entries = 0;
        try (var channel = new SeekableInMemoryByteChannel(archive);
             var zip = ZipFile.builder().setSeekableByteChannel(channel).get()) {
            var iterator = zip.getEntries();
            while (iterator.hasMoreElements()) {
                var entry = iterator.nextElement();
                if (++entries > 10000) throw new LimitException();
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/")).contains(".."))
                    throw new SecurityException();
                // Contabiliza inclusive conteúdo ignorado para limitar bombas ZIP.
                if (entry.isDirectory() || entry.isUnixSymlink()) continue;
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) {
                    bytes = readLimited(input, Math.min(maxExtracted - total, name.endsWith(".java") ? maxFile : maxExtracted));
                }
                total += bytes.length;
                if (!name.endsWith(".java")) continue;
                if (Arrays.stream(name.split("/")).anyMatch(Set.of("target", "build", "out", ".gradle", "node_modules", "vendor")::contains)) continue;
                if (out.size() >= maxFiles) throw new LimitException();
                int slash = name.indexOf('/');
                String relative = slash >= 0 ? name.substring(slash + 1) : name;
                if (relative.length() > 1000) throw new LimitException();
                out.add(new File(relative, new String(bytes, StandardCharsets.UTF_8)));
            }
        }
        return out;
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; long total = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            total += count; if (total > limit) throw new LimitException();
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void checkStatus(int status) {
        if (status == 404) throw new NoSuchElementException();
        if (status == 403 || status == 429) throw new RateLimitException();
    }
    public static class LimitException extends RuntimeException {}
    public static class RateLimitException extends RuntimeException {}
    public static class UnavailableException extends RuntimeException {}
}
