package pl.slogerski.heartistica;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HeartDisplayStateTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        HeartDisplayState s = new HeartDisplayState();
        s.update(20, 20, 0, false, false, 0);
        check(s.slots == 0, "full normal health is hidden");
        for (int i = 0; i < 10; i++) check(s.sprites[i] == HeartDisplayState.HIDDEN, "full-health slots are hidden");
        check(!s.update(20, 20, 0, false, false, 1), "unchanged state reuses layout");
        s.update(15, 20, 3, false, false, 100);
        check(s.sprites[7] == HeartDisplayState.HALF, "15 HP includes a half heart");
        check(s.sprites[8] == HeartDisplayState.EMPTY && s.sprites[9] == HeartDisplayState.EMPTY, "empty slots persist");
        check(s.slots == 12 && s.sprites[10] == HeartDisplayState.GOLD
                && s.sprites[11] == HeartDisplayState.GOLD_HALF, "absorption follows regular capacity without overlap");

        s = new HeartDisplayState();
        s.update(60, 60, 0, false, false, 0);
        check(s.slots == 0, "custom full-health rows are hidden");
        s.update(40, 60, 0, false, false, 100);
        check(s.slots == 30 && s.sprites[20] == HeartDisplayState.EMPTY, "upper empties appear after damage");
        check(!s.update(40, 60, 0, false, false, 5099), "timer does not rebuild before expiry");
        s.update(40, 60, 0, false, false, 5100);
        check(s.slots == 20 && s.sprites[20] == HeartDisplayState.HIDDEN, "upper empties disappear at five seconds");
        s.update(41, 60, 0, false, false, 5200);
        check(s.slots == 21 && s.sprites[20] == HeartDisplayState.HALF, "half refill restores upper slot");
        s.update(40, 60, 0, false, false, 5300);
        check(s.sprites[20] == HeartDisplayState.EMPTY, "another loss restarts timer");
        s.update(40, 60, 0, false, false, 10300);
        check(s.slots == 20, "restarted timer expires");

        s = new HeartDisplayState();
        s.update(40, 60, 0, false, false, 0);
        check(s.slots == 20, "initial upper empties are hidden");
        s.update(1, 40, 0, false, false, 10000);
        check(s.slots == 20 && s.sprites[0] == HeartDisplayState.HALF
                && s.sprites[19] == HeartDisplayState.EMPTY, "two rows persist without recentering");
        s.update(20, 20, 4, true, true, 11000);
        check(s.label.equals("4/?") && s.goldLabel, "absorption-only numeric hides normal health");
        s.update(20, 20, 0, true, true, 12000);
        check(s.label.equals("0/?") && s.goldLabel, "zero absorption stays gold and displays zero");
        s.update(20, 20, 4, true, false, 13000);
        check(s.label.equals("24/20") && s.goldLabel, "combined numeric includes absorption");
        s.update(20, 20, 0, true, false, 14000);
        check(s.label.isEmpty() && !s.goldLabel, "full-health numeric is hidden after absorption ends");
        s.update(99, 100, 0, true, false, 15000);
        check(s.label.equals("99/100"), "custom maximum HP preserved");
        s.update(1, 20, 0.5F, false, true, 16000);
        check(s.slots == 1 && s.sprites[0] == HeartDisplayState.GOLD_HALF, "fractional absorption rounds to half heart");
        s.update(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, true, true, 17000);
        check(s.label.equals("0/?"), "invalid floats cannot corrupt layout");
        s.update(199, Float.MAX_VALUE, Float.MAX_VALUE, false, false, 18000);
        check(s.slots == 200, "server values cannot exceed layout buffers");

        s = new HeartDisplayState();
        s.update(60, 60, 0, false, false, 0);
        s.update(40, 60, 0, true, false, 100);
        check(s.slots == 0 && s.label.equals("40/60"), "numeric mode does not build unused icons");
        check(!s.update(40, 60, 0, true, false, 5100), "numeric mode ignores empty-icon expiry");
        s.update(40, 60, 0, false, false, 5200);
        check(s.slots == 20, "returning from numeric mode respects elapsed timers");
        s.update(60, 60, 4, false, true, 5300);
        s.update(40, 60, 4, false, true, 5400);
        check(!s.update(40, 60, 4, false, true, 10400), "absorption-only mode ignores normal-heart expiry");
        s.update(40, 60, 4, false, false, 10500);
        check(s.slots == 22 && s.sprites[20] == HeartDisplayState.GOLD,
                "returning from absorption-only mode preserves normal-heart timers");
        s.update(80, 100, 0, false, false, 10600);
        check(s.slots == 40, "increasing maximum does not invent upper empty-heart timers");

        check(HeartVisibilityRules.matches(15, 20, 0, false, false), "damage remains visible");
        check(!HeartVisibilityRules.matches(20, 20, 0, false, false), "full health without absorption is hidden");
        check(HeartVisibilityRules.matches(15, 20, 4, false, false), "damage and absorption remain visible together");
        check(HeartVisibilityRules.matches(20, 20, 4, false, false), "absorption remains visible at full health");
        check(!HeartVisibilityRules.matches(15, Float.POSITIVE_INFINITY, 0, false, false), "invalid maximum is not proof of damage");
        check(HeartVisibilityRules.matches(15, 20, Float.NaN, false, false), "invalid absorption does not hide known damage");
        check(!HeartVisibilityRules.matches(0, 20, 0, false, false), "dead players do not pass damage filter");
        check(!HeartVisibilityRules.matches(20, 20, Float.POSITIVE_INFINITY, false, true), "invalid absorption cannot show empty icon rows");
        check(HeartVisibilityRules.matches(20, 20, 0, true, true), "zero absorption remains visible numerically");
        check(!HeartVisibilityRules.matches(20, 20, 0, false, true), "zero absorption has no icon row");

        s = new HeartDisplayState();
        s.update(20, 20, 3, false, false, 0);
        check(s.slots == 2 && s.sprites[0] == HeartDisplayState.GOLD
                && s.sprites[1] == HeartDisplayState.GOLD_HALF, "full-health icons contain only absorption");
        s.update(19, 20, 3, false, false, 1);
        check(s.slots == 12 && s.sprites[9] == HeartDisplayState.HALF
                && s.sprites[10] == HeartDisplayState.GOLD, "damage restores normal hearts without hiding absorption");
        s.update(20, 20, 3, false, false, 2);
        check(s.slots == 2 && s.sprites[0] == HeartDisplayState.GOLD, "healing hides only normal hearts");
        s.update(20, 20, 0, false, false, 3);
        check(s.slots == 0, "full health without absorption contains no icons");
        s.update(20, 20, 0, true, false, 4);
        check(s.label.isEmpty() && s.anchorLabel.isEmpty(), "hidden numeric health has no unused anchor text");
        s.update(21, 20, 2, false, false, 5);
        check(s.slots == 1 && s.sprites[0] == HeartDisplayState.GOLD, "health above maximum does not hide absorption");
        s.update(Float.NaN, 20, 2, false, false, 6);
        check(s.slots == 1 && s.sprites[0] == HeartDisplayState.GOLD, "unknown normal health does not hide valid absorption");
        s.update(15, 20, 0, true, false, 7);
        check(s.label.equals("15/20") && !s.goldLabel, "damage restores red numeric health");
        verifyConfiguration();
        System.out.println("Heart display regression checks passed: " + assertions);
    }

    private static void verifyConfiguration() throws Exception {
        Path directory = Files.createTempDirectory("heartistica-config-test-");
        Path file = directory.resolve("settings.json");
        try {
            HeartisticaConfig config = HeartisticaConfig.load(file);
            check(config.enabled && config.range == 48 && config.nearestPlayers == 0, "missing config enables mod by default");
            Files.writeString(file, "{\"range\":15}");
            check(HeartisticaConfig.load(file).range == 48, "unversioned legacy config migrates");
            Files.writeString(file, "{\"range\":15,\"configVersion\":2}");
            check(HeartisticaConfig.load(file).range == 15, "current intentional range is retained");
            Files.writeString(file, "{\"range\":999,\"nearestPlayers\":-1,\"scalePercent\":1,\"heightOffsetPixels\":100,"
                    + "\"heartStyle\":\"../invalid\",\"onlyAbsorption\":true,\"onlyDamagedWithoutAbsorption\":true}");
            config = HeartisticaConfig.load(file);
            check(config.range == 128 && config.nearestPlayers == 0 && config.scalePercent == 50
                    && config.heightOffsetPixels == 4, "out-of-range config values are bounded");
            check(config.heartStyle.equals("resource_pack") && config.onlyAbsorption,
                    "invalid style is sanitized and legacy filter is ignored");
            config.range = 0;
            check(config.rangeSquared() == Double.POSITIVE_INFINITY, "OFF removes distance limit");
            config.heartStyle = "heartistica";
            config.save(file);
            check(!Files.readString(file).contains("onlyDamagedWithoutAbsorption"), "removed filter is not saved");
            config = HeartisticaConfig.load(file);
            check(config.range == 0 && config.heartStyle.equals("heartistica") && config.onlyAbsorption,
                    "replacing existing config retains settings on reload");
            try (var files = Files.list(directory)) {
                check(files.count() == 1, "successful atomic save leaves no temporary files");
            }
            config.reset();
            check(config.configVersion == 2 && config.range == 48 && !config.onlyAbsorption,
                    "reset restores version and defaults");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
