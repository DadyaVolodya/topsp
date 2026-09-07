package ru.infereco.demo.barista.stt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import ru.infereco.demo.barista.config.MeetProperties;

@Component
public class VoskSttService {

    private static final Logger LOG = LoggerFactory.getLogger(VoskSttService.class);
    private static final Pattern TEXT = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");

    private final MeetProperties properties;
    private final Object lock = new Object();
    private volatile String status = "loading";
    private volatile String error = "";
    private volatile boolean ready;
    private Model model;
    private final SttLexicon lexicon;

    public VoskSttService(MeetProperties properties, SttLexicon lexicon) {
        this.properties = properties;
        this.lexicon = lexicon;
        Thread.ofVirtual().name("vosk-model").start(this::preload);
    }

    public StatusView snapshot() {
        return new StatusView(ready, status, error, "vosk");
    }

    public String transcribe(byte[] wavOrPcm) {
        ensureReady();
        byte[] pcm = PcmWav.pcm16le(wavOrPcm);
        if (pcm.length < 3200) {
            return "";
        }
        synchronized (lock) {
            try (Recognizer recognizer = new Recognizer(model, (float) properties.stt().sampleRate())) {
                int offset = 0;
                byte[] buf = new byte[4000];
                while (offset < pcm.length) {
                    int n = Math.min(buf.length, pcm.length - offset);
                    if (n % 2 != 0) {
                        n--;
                    }
                    if (n <= 0) {
                        break;
                    }
                    System.arraycopy(pcm, offset, buf, 0, n);
                    recognizer.acceptWaveForm(buf, n);
                    offset += n;
                }
                return extractText(recognizer.getFinalResult());
            } catch (IOException ex) {
                throw new IllegalStateException("vosk failed: " + ex.getMessage(), ex);
            }
        }
    }

    public VoskStream openStream() {
        ensureReady();
        try {
            return new VoskStream(new Recognizer(model, (float) properties.stt().sampleRate()), lexicon);
        } catch (IOException ex) {
            throw new IllegalStateException("vosk stream failed: " + ex.getMessage(), ex);
        }
    }

    private void ensureReady() {
        if (ready && model != null) {
            return;
        }
        synchronized (lock) {
            if (ready && model != null) {
                return;
            }
            preloadLocked();
            if (!ready) {
                throw new SttNotReadyException(error.isBlank() ? status : error);
            }
        }
    }

    private void preload() {
        synchronized (lock) {
            preloadLocked();
        }
    }

    private void preloadLocked() {
        try {
            status = "downloading";
            Path dir = Path.of(properties.stt().modelDir()).toAbsolutePath().normalize();
            if (!isModelDir(dir)) {
                downloadAndUnzip(URI.create(properties.stt().modelUrl()), dir);
            }
            status = "loading-model";
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            model = new Model(dir.toString());
            ready = true;
            status = "ready";
            error = "";
            LOG.info("Vosk model ready at {}", dir);
        } catch (Throwable ex) {
            ready = false;
            error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            status = "error";
            LOG.warn("Vosk model failed: {}", error);
        }
    }

    private static boolean isModelDir(Path dir) {
        return Files.isDirectory(dir.resolve("am"))
                && Files.isDirectory(dir.resolve("conf"));
    }

    private void downloadAndUnzip(URI url, Path targetDir) throws IOException, InterruptedException {
        Files.createDirectories(targetDir.getParent() == null ? Path.of(".") : targetDir.getParent());
        Path zip = targetDir.getParent().resolve(targetDir.getFileName() + ".zip");
        LOG.info("Downloading Vosk model from {}", url);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(5)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IOException("model download HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        unzip(zip, targetDir.getParent());
        Files.deleteIfExists(zip);
        if (!isModelDir(targetDir)) {
            throw new IOException("model dir is incomplete: " + targetDir);
        }
    }

    private static void unzip(Path zip, Path dest) throws IOException {
        Path destAbs = dest.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = destAbs.resolve(entry.getName()).normalize();
                if (!out.startsWith(destAbs)) {
                    throw new IOException("zip slip: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String extractText(String json) {
        if (json == null) {
            return "";
        }
        Matcher matcher = TEXT.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim().toLowerCase(Locale.ROOT);
    }

    public record StatusView(boolean ready, String status, String error, String engine) {
    }
}
