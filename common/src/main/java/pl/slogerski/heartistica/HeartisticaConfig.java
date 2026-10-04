package pl.slogerski.heartistica;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

public final class HeartisticaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 3;
    private static final double DEFAULT_HEIGHT = 4;
    private static final System.Logger LOGGER = System.getLogger("Heartistica");

    public int configVersion = 3;
    public boolean enabled = true;
    public int range = 48;
    public int nearestPlayers = 0;
    public int scalePercent = 100;
    public double heightOffsetPixels = DEFAULT_HEIGHT;
    public boolean numericDisplay = false;
    public boolean onlyAbsorption = false;
    public boolean onlyWhenDamaged = true;
    public String heartStyle = "resource_pack";

    static HeartisticaConfig load() {
        return load(configPath());
    }

    static HeartisticaConfig load(Path path) {
        if (!Files.isRegularFile(path)) return new HeartisticaConfig();
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            HeartisticaConfig loaded = GSON.fromJson(data, HeartisticaConfig.class);
            int storedVersion = data.has("configVersion") ? data.get("configVersion").getAsInt() : 1;
            if (storedVersion < 2) {
                if (loaded.range == 15) loaded.range = 48;
            }
            loaded.sanitize();
            return loaded;
        } catch (IOException | RuntimeException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read Heartistica settings: " + path, error);
            return new HeartisticaConfig();
        }
    }

    public void save() {
        save(configPath());
    }

    void save(Path path) {
        sanitize();
        Path temporary = null;
        try {
            path = path.toAbsolutePath();
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "heartistica-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save Heartistica settings: " + path, error);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException error) { LOGGER.log(System.Logger.Level.WARNING, "Could not remove temporary settings file", error); }
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("heartistica.json");
    }

    boolean shouldDisplayHealth(float health, float maxHealth, float absorption) {
        return HeartVisibilityRules.matches(health, maxHealth, absorption, numericDisplay,
                onlyAbsorption, onlyWhenDamaged);
    }

    double rangeSquared() {
        return range == 0 ? Double.POSITIVE_INFINITY : (double) range * range;
    }

    double heightAboveHead() {
        return 0.62 + (heightOffsetPixels + 2) / 16.0;
    }

    public void reset() {
        configVersion = CURRENT_VERSION;
        heartStyle = "resource_pack";
        enabled = true;
        range = 48;
        nearestPlayers = 0;
        scalePercent = 100;
        heightOffsetPixels = DEFAULT_HEIGHT;
        numericDisplay = false;
        onlyAbsorption = false;
        onlyWhenDamaged = true;
    }

    HeartisticaConfig copy() {
        HeartisticaConfig copy = new HeartisticaConfig();
        copy.copyFrom(this);
        return copy;
    }

    void copyFrom(HeartisticaConfig source) {
        enabled = source.enabled;
        range = source.range;
        nearestPlayers = source.nearestPlayers;
        scalePercent = source.scalePercent;
        heightOffsetPixels = source.heightOffsetPixels;
        numericDisplay = source.numericDisplay;
        onlyAbsorption = source.onlyAbsorption;
        onlyWhenDamaged = source.onlyWhenDamaged;
        heartStyle = source.heartStyle;
        sanitize();
    }

    static String heightText(double height) {
        String value = java.math.BigDecimal.valueOf(height).stripTrailingZeros().toPlainString();
        return height > 0 ? "+" + value : value;
    }

    void sanitize() {
        if (heartStyle == null || !heartStyle.matches("[a-z0-9_-]+")) heartStyle = "resource_pack";
        configVersion = CURRENT_VERSION;
        range = clamp(range, 0, 128);
        nearestPlayers = clamp(nearestPlayers, 0, 64);
        scalePercent = clamp(scalePercent, 50, 200);
        heightOffsetPixels = Double.isFinite(heightOffsetPixels)
                ? Math.round(Math.max(-8, Math.min(8, heightOffsetPixels)) * 2) / 2.0 : DEFAULT_HEIGHT;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
