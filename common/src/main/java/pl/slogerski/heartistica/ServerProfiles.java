package pl.slogerski.heartistica;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class ServerProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final System.Logger LOGGER = System.getLogger("Heartistica");
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_PROFILES = 1024;
    private static final long MAX_BYTES = 1_048_576;
    private final Path defaultsPath, profilesPath;
    private final HeartisticaConfig config, defaults;
    private final Map<String, HeartisticaConfig> profiles = new LinkedHashMap<>();
    private String server;
    private boolean writable = true;
    private boolean recovered;

    ServerProfiles(HeartisticaConfig config, Path defaultsPath, Path profilesPath) {
        this.config = config;
        this.defaults = config.copy();
        this.defaultsPath = defaultsPath;
        this.profilesPath = profilesPath;
        load();
    }

    void useServer(String address) {
        String key = serverKey(address);
        if (Objects.equals(server, key)) return;
        server = key;
        config.copyFrom(profiles.getOrDefault(server, defaults));
    }

    boolean canSave() {
        return server != null && writable && (profiles.containsKey(server) || profiles.size() < MAX_PROFILES);
    }

    boolean hasProfile() {
        return server != null && profiles.containsKey(server);
    }

    boolean saveForServer() {
        if (!canSave()) return false;
        Map<String, HeartisticaConfig> updated = new LinkedHashMap<>(profiles);
        updated.put(server, config.copy());
        if (!write(updated)) return false;
        profiles.clear();
        profiles.putAll(updated);
        return true;
    }

    boolean backToDefault() {
        if (!hasProfile() || !writable) return false;
        Map<String, HeartisticaConfig> updated = new LinkedHashMap<>(profiles);
        updated.remove(server);
        if (!write(updated)) return false;
        profiles.clear();
        profiles.putAll(updated);
        config.copyFrom(defaults);
        return true;
    }

    boolean canRestore() {
        return writable && hasProfile();
    }

    void saveCurrent() {
        if (hasProfile()) {
            saveForServer();
        } else {
            defaults.copyFrom(config);
            defaults.save(defaultsPath);
            config.copyFrom(defaults);
        }
    }

    static String serverKey(String address) {
        if (address == null) return null;
        String key = address.strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || key.length() > 255
                || key.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)
                || c == '/' || c == '\\' || c == '@' || c == '?' || c == '#')) return null;
        if (key.endsWith(":25565") && (key.indexOf(':') == key.lastIndexOf(':') || key.startsWith("["))) {
            key = key.substring(0, key.length() - 6);
        }
        if (key.endsWith(".") && key.indexOf(':') < 0) key = key.substring(0, key.length() - 1);
        return key.isEmpty() ? null : key;
    }

    private Path backupPath() {
        return profilesPath.resolveSibling(profilesPath.getFileName() + ".bak");
    }

    private void load() {
        if (!Files.exists(profilesPath) && !Files.exists(backupPath())) return;
        try {
            profiles.putAll(read(profilesPath));
        } catch (UnsupportedSchema error) {
            writable = false;
            LOGGER.log(System.Logger.Level.WARNING, "Server profiles use an unsupported schema; file left unchanged");
        } catch (IOException | RuntimeException error) {
            try {
                profiles.putAll(read(backupPath()));
                recovered = true;
                LOGGER.log(System.Logger.Level.WARNING, "Recovered server profiles from backup", error);
            } catch (IOException | RuntimeException backupError) {
                writable = false;
                LOGGER.log(System.Logger.Level.WARNING, "Could not read server profiles; files left unchanged", error);
            }
        }
    }

    private Map<String, HeartisticaConfig> read(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Server profiles file is too large");
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        if (!root.has("schemaVersion") || !root.get("schemaVersion").isJsonPrimitive()
                || !root.get("schemaVersion").getAsJsonPrimitive().isNumber()
                || root.get("schemaVersion").getAsBigDecimal().compareTo(java.math.BigDecimal.valueOf(SCHEMA_VERSION)) != 0) {
            throw new UnsupportedSchema();
        }
        JsonObject entries = root.getAsJsonObject("servers");
        if (entries == null || entries.size() > MAX_PROFILES) throw new IOException("Invalid server profile count");
        Map<String, HeartisticaConfig> loaded = new LinkedHashMap<>();
        for (var entry : entries.entrySet()) {
            String key = serverKey(entry.getKey());
            if (key == null || !entry.getValue().isJsonObject()) throw new IOException("Invalid server profile");
            HeartisticaConfig settings = GSON.fromJson(entry.getValue(), HeartisticaConfig.class);
            settings.sanitize();
            if (loaded.put(key, settings) != null) throw new IOException("Duplicate server profile");
        }
        return loaded;
    }

    private boolean write(Map<String, HeartisticaConfig> updated) {
        Path temporary = null;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", SCHEMA_VERSION);
            root.add("servers", GSON.toJsonTree(updated));
            byte[] bytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IOException("Server profiles file is too large");
            Path target = profilesPath.toAbsolutePath();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "heartistica-servers-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.isRegularFile(target)) {
                boolean valid = true;
                try { read(target); }
                catch (UnsupportedSchema error) { throw error; }
                catch (IOException | RuntimeException error) {
                    if (!recovered) throw error;
                    valid = false;
                }
                if (valid) Files.copy(target, backupPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            recovered = false;
            return true;
        } catch (IOException | RuntimeException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save server profiles", error);
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException error) { LOGGER.log(System.Logger.Level.WARNING, "Could not remove profile temporary file", error); }
            }
        }
    }

    private static final class UnsupportedSchema extends RuntimeException {}
}
