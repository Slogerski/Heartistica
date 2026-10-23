package pl.slogerski.heartistica;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class LanguageSettings {
    interface Source {
        boolean isPresent();
        String getLanguage();
    }

    enum Mode {
        POLISH("pl_pl"), ENGLISH("en_us"), OGULNIEGA("ogulniega");
        final String id;
        Mode(String id) { this.id = id; }
        static Mode parse(String id) {
            for (Mode mode : values()) if (mode.id.equals(id)) return mode;
            throw new IllegalArgumentException("Unknown language mode");
        }
    }

    private static final System.Logger LOGGER = System.getLogger("Heartistica");
    private static final long STARTUP_TIMEOUT = 10_000_000_000L;
    private static final long RETRY_INTERVAL = 250_000_000L;
    private final Path path;
    private final Source source;
    private final boolean present;
    private Mode mode;
    private String language, startupLanguage;
    private boolean writable = true, startupDone, startupPending;
    private long startedAt, nextAttempt;

    LanguageSettings(Path path, String initialLanguage, Source source) {
        this.path = path.toAbsolutePath();
        this.source = source;
        present = source.isPresent();
        language = localLanguage(initialLanguage);
        mode = present ? Mode.OGULNIEGA : Mode.parse(language);
        try {
            if (Files.exists(this.path)) {
                JsonObject data = read(this.path);
                requireVersion(data);
                Mode storedMode = Mode.parse(data.get("mode").getAsString());
                String storedLanguage = requireLanguage(data.get("lastLanguage").getAsString());
                mode = storedMode;
                language = mode == Mode.OGULNIEGA ? storedLanguage : mode.id;
            } else {
                Path legacy = this.path.resolveSibling("LanguageChanged.json");
                if (Files.exists(legacy)) {
                    JsonObject data = read(legacy);
                    requireVersion(data);
                    var changed = data.get("changed");
                    if (changed == null || !changed.isJsonPrimitive() || !changed.getAsJsonPrimitive().isNumber())
                        throw new IOException("Invalid legacy language override");
                    var value = changed.getAsBigDecimal();
                    if (value.compareTo(java.math.BigDecimal.ZERO) == 0) mode = Mode.OGULNIEGA;
                    else if (value.compareTo(java.math.BigDecimal.ONE) == 0) mode = Mode.parse(language);
                    else throw new IOException("Invalid legacy language override");
                }
                save();
            }
        } catch (IOException | RuntimeException error) {
            mode = Mode.parse(language);
            writable = false;
            LOGGER.log(System.Logger.Level.WARNING, "Could not read language settings; keeping local language and preserving file", error);
        }
        startupDone = !present;
    }

    Mode mode() { return mode; }
    String language() { return language; }
    boolean isOgulniegaPresent() { return present; }
    boolean isStartupComplete() { return startupDone; }

    boolean readAtStartup(long now) {
        if (startupDone) return false;
        if (!startupPending) {
            startedAt = now;
            nextAttempt = now;
            startupPending = true;
        }
        if (now - startedAt >= STARTUP_TIMEOUT) {
            startupDone = true;
            LOGGER.log(System.Logger.Level.WARNING, "Ogulniega language was not ready; keeping last language");
            return false;
        }
        if (now - nextAttempt < 0) return false;
        nextAttempt = now + RETRY_INTERVAL;
        try {
            String value = source.getLanguage();
            if (value == null) return false;
            startupDone = true;
            startupLanguage = switch (value) {
                case "POLISH" -> "pl_pl";
                case "ENGLISH" -> "en_us";
                default -> null;
            };
            if (startupLanguage == null) {
                LOGGER.log(System.Logger.Level.WARNING, "Ogulniega returned an unsupported language; keeping last language");
                return false;
            }
            if (mode != Mode.OGULNIEGA || startupLanguage.equals(language)) return false;
            language = startupLanguage;
            save();
            return true;
        } catch (RuntimeException | LinkageError error) {
            startupDone = true;
            LOGGER.log(System.Logger.Level.WARNING, "Could not read Ogulniega language; keeping last language", error);
            return false;
        }
    }

    void cycle() {
        select(switch (mode) {
            case OGULNIEGA -> Mode.POLISH;
            case POLISH -> Mode.ENGLISH;
            case ENGLISH -> present ? Mode.OGULNIEGA : Mode.POLISH;
        });
    }

    void reset() { select(present ? Mode.OGULNIEGA : Mode.ENGLISH); }

    private void select(Mode selected) {
        mode = selected;
        if (selected != Mode.OGULNIEGA) language = selected.id;
        else if (startupLanguage != null) language = startupLanguage;
        save();
    }

    private void save() {
        if (!writable) return;
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "language-", ".tmp");
            JsonObject data = new JsonObject();
            data.addProperty("schemaVersion", 1);
            data.addProperty("mode", mode.id);
            data.addProperty("lastLanguage", language);
            Files.writeString(temporary, data.toString() + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException | RuntimeException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save language settings", error);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException | RuntimeException error) { LOGGER.log(System.Logger.Level.WARNING, "Could not remove temporary language settings", error); }
        }
    }

    private static JsonObject read(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(4097);
            if (bytes.length > 4096) throw new IOException("Language settings file is too large");
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void requireVersion(JsonObject data) throws IOException {
        var version = data.get("schemaVersion");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || version.getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0)
            throw new IOException("Unsupported language settings schema");
    }

    private static String requireLanguage(String language) {
        if (!language.equals("pl_pl") && !language.equals("en_us")) throw new IllegalArgumentException("Unsupported language");
        return language;
    }

    private static String localLanguage(String language) { return "pl_pl".equals(language) ? "pl_pl" : "en_us"; }
}
