package pl.slogerski.heartistica;

import java.nio.file.Files;
import java.nio.file.Path;

final class LanguageSettingsTest {
    private static int checks;

    private static final class Source implements LanguageSettings.Source {
        final boolean present;
        String value = "POLISH";
        int calls;
        Source(boolean present) { this.present = present; }
        public boolean isPresent() { return present; }
        public String getLanguage() { calls++; return value; }
    }

    static void verify() throws Exception {
        checks = 0;
        Path directory = Files.createTempDirectory("heartistica-language-settings-");
        try {
            verifyModes(directory);
            verifyStartup(directory);
            verifyMigration(directory);
            verifyFiles(directory);
            verifyApi(directory);
            System.out.println("Language settings regression checks passed: " + checks);
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyModes(Path directory) {
        Source absent = new Source(false);
        var local = new LanguageSettings(directory.resolve("local/language.json"), "en_us", absent);
        check(local.mode() == LanguageSettings.Mode.ENGLISH, "without Ogulniega the default is the local language");
        check(local.isStartupComplete() && !local.readAtStartup(0) && absent.calls == 0, "missing mod never waits or invokes the API");
        local.cycle();
        check(local.mode() == LanguageSettings.Mode.POLISH && local.language().equals("pl_pl"), "local English cycles to Polish");
        local.cycle();
        check(local.mode() == LanguageSettings.Mode.ENGLISH, "missing mod is not offered as a new choice");
        local.reset();
        check(local.mode() == LanguageSettings.Mode.ENGLISH, "local reset selects English");

        Path path = directory.resolve("auto/language.json");
        Source source = new Source(true);
        var automatic = new LanguageSettings(path, "en_us", source);
        check(automatic.mode() == LanguageSettings.Mode.OGULNIEGA && source.calls == 0, "present mod is the default without early API access");
        check(automatic.readAtStartup(0) && automatic.language().equals("pl_pl"), "startup maps POLISH");
        source.value = "ENGLISH";
        check(!automatic.readAtStartup(1_000_000_000L) && source.calls == 1, "later external changes are ignored after the startup sample");
        automatic.cycle();
        check(automatic.mode() == LanguageSettings.Mode.POLISH, "Ogulniega cycles to Polish");
        automatic.cycle();
        check(automatic.mode() == LanguageSettings.Mode.ENGLISH && automatic.language().equals("en_us"), "Polish cycles to English");
        automatic.cycle();
        check(automatic.mode() == LanguageSettings.Mode.OGULNIEGA && automatic.language().equals("pl_pl") && source.calls == 1,
                "returning to Ogulniega reuses the startup sample without another API call");

        var removed = new LanguageSettings(path, "en_us", absent);
        check(removed.mode() == LanguageSettings.Mode.OGULNIEGA && removed.language().equals("pl_pl"), "removed mod preserves the selected mode and last language");
        check(removed.isStartupComplete() && !removed.readAtStartup(0) && absent.calls == 0, "removed mod never delays startup");
        removed.cycle();
        check(removed.mode() == LanguageSettings.Mode.POLISH, "unavailable selected mode can be changed normally");
        removed.cycle();
        removed.cycle();
        check(removed.mode() == LanguageSettings.Mode.POLISH, "unavailable Ogulniega is skipped in subsequent cycles");
        var manual = new LanguageSettings(path, "en_us", source);
        check(manual.mode() == LanguageSettings.Mode.POLISH, "reinstalling the mod does not override a saved manual choice");
        manual.readAtStartup(0);
        check(manual.language().equals("pl_pl"), "startup sample cannot overwrite manual mode");
        manual.reset();
        check(manual.mode() == LanguageSettings.Mode.OGULNIEGA && manual.language().equals("en_us"), "reset with mod present restores the external mode and startup sample");
        var restarted = new LanguageSettings(path, "pl_pl", source);
        check(restarted.language().equals("en_us"), "last external language survives a restart");
        source.value = "POLISH";
        check(restarted.readAtStartup(0) && restarted.language().equals("pl_pl"), "next launch takes a fresh startup sample");
    }

    private static void verifyStartup(Path directory) {
        Source source = new Source(true);
        source.value = null;
        var delayed = new LanguageSettings(directory.resolve("delayed/language.json"), "en_us", source);
        check(!delayed.readAtStartup(-1_000_000_000L) && !delayed.isStartupComplete(), "unready API starts a bounded window even with negative nanoTime");
        check(!delayed.readAtStartup(-999_999_999L) && source.calls == 1, "startup retries are rate limited");
        source.value = "POLISH";
        check(delayed.readAtStartup(-750_000_000L) && source.calls == 2 && delayed.isStartupComplete(), "one valid sample completes initialization");
        delayed.readAtStartup(50_000_000_000L);
        check(source.calls == 2, "no polling remains after initialization");

        Source unready = new Source(true);
        unready.value = null;
        var timeout = new LanguageSettings(directory.resolve("timeout/language.json"), "pl_pl", unready);
        timeout.readAtStartup(0);
        timeout.readAtStartup(10_000_000_000L);
        check(timeout.isStartupComplete() && timeout.language().equals("pl_pl") && unready.calls == 1, "unready API times out without blocking or losing last language");
        timeout.readAtStartup(20_000_000_000L);
        check(unready.calls == 1, "timeout stops API attempts permanently for this launch");

        Source invalid = new Source(true);
        invalid.value = "polish";
        var badValue = new LanguageSettings(directory.resolve("invalid-value/language.json"), "en_us", invalid);
        badValue.readAtStartup(0);
        check(badValue.isStartupComplete() && badValue.language().equals("en_us"), "only uppercase API values are accepted");
        var earlyManual = new LanguageSettings(directory.resolve("early-manual/language.json"), "en_us", source);
        earlyManual.cycle();
        source.value = "ENGLISH";
        earlyManual.readAtStartup(0);
        check(earlyManual.mode() == LanguageSettings.Mode.POLISH && earlyManual.language().equals("pl_pl"), "manual click before API readiness cannot be overwritten");
    }

    private static void verifyMigration(Path directory) throws Exception {
        Source present = new Source(true), absent = new Source(false);
        Path manualDirectory = directory.resolve("legacy-manual");
        Files.createDirectories(manualDirectory);
        Path legacy = manualDirectory.resolve("LanguageChanged.json");
        Files.writeString(legacy, "{\"schemaVersion\":1,\"changed\":1}");
        var manual = new LanguageSettings(manualDirectory.resolve("language.json"), "pl_pl", present);
        check(manual.mode() == LanguageSettings.Mode.POLISH, "legacy manual override migrates to an explicit language");
        check(Files.readString(legacy).contains("\"changed\":1"), "migration leaves the old runtime file intact as a fallback");
        Files.writeString(legacy, "{\"schemaVersion\":1,\"changed\":0}");
        var reloaded = new LanguageSettings(manualDirectory.resolve("language.json"), "en_us", present);
        check(reloaded.mode() == LanguageSettings.Mode.POLISH, "new file takes precedence over the old flag");
        Path autoDirectory = directory.resolve("legacy-auto");
        Files.createDirectories(autoDirectory);
        Files.writeString(autoDirectory.resolve("LanguageChanged.json"), "{\"schemaVersion\":1,\"changed\":0}");
        var automatic = new LanguageSettings(autoDirectory.resolve("language.json"), "pl_pl", absent);
        check(automatic.mode() == LanguageSettings.Mode.OGULNIEGA && automatic.language().equals("pl_pl"), "legacy automatic mode survives migration even without the mod");
        Path corruptDirectory = directory.resolve("legacy-invalid");
        Files.createDirectories(corruptDirectory);
        Files.writeString(corruptDirectory.resolve("LanguageChanged.json"), "{\"schemaVersion\":1,\"changed\":0.5}");
        var corrupt = new LanguageSettings(corruptDirectory.resolve("language.json"), "pl_pl", present);
        check(corrupt.mode() == LanguageSettings.Mode.POLISH && !Files.exists(corruptDirectory.resolve("language.json")),
                "invalid legacy flags cannot enable automatic changes or destroy the source");
    }

    private static void verifyFiles(Path directory) throws Exception {
        Path path = directory.resolve("files/language.json");
        Files.createDirectories(path.getParent());
        String[] invalid = {
            "{\"schemaVersion\":2,\"mode\":\"ogulniega\",\"lastLanguage\":\"pl_pl\"}",
            "{\"schemaVersion\":1,\"mode\":\"unknown\",\"lastLanguage\":\"pl_pl\"}",
            "{\"schemaVersion\":1,\"mode\":\"ogulniega\",\"lastLanguage\":\"unknown\"}",
            "{\"schemaVersion\":1.5,\"mode\":\"ogulniega\",\"lastLanguage\":\"pl_pl\"}",
            "{\"schemaVersion\":\"1\",\"mode\":\"ogulniega\",\"lastLanguage\":\"pl_pl\"}",
            "{\"schemaVersion\":1,\"mode\":null,\"lastLanguage\":\"pl_pl\"}",
            "broken", "x".repeat(4097)
        };
        for (String content : invalid) {
            Files.writeString(path, content);
            var settings = new LanguageSettings(path, "en_us", new Source(true));
            check(settings.mode() == LanguageSettings.Mode.ENGLISH && settings.language().equals("en_us"), "invalid settings keep local fallback");
            settings.cycle();
            check(Files.readString(path).equals(content), "invalid or future files are never overwritten");
        }
        Files.writeString(path, "{\"schemaVersion\":1,\"mode\":\"pl_pl\",\"lastLanguage\":\"en_us\"}");
        var manual = new LanguageSettings(path, "en_us", new Source(false));
        check(manual.language().equals("pl_pl"), "manual mode is authoritative over a stale language cache");
        manual.cycle();
        var loaded = new LanguageSettings(path, "pl_pl", new Source(false));
        check(loaded.mode() == LanguageSettings.Mode.ENGLISH && loaded.language().equals("en_us"), "mode and language are persisted together");
        try (var files = Files.list(path.getParent())) {
            check(files.noneMatch(file -> file.toString().endsWith(".tmp")), "atomic saves clean up temporary files");
        }
    }

    private static void verifyApi(Path directory) {
        com.ogulniega.OgulniegaModApi.calls = 0;
        com.ogulniega.OgulniegaModApi.language = null;
        com.ogulniega.OgulniegaModApi.failure = new NullPointerException("Settings not ready");
        var api = new OgulniegaLanguage(true);
        var delayed = new LanguageSettings(directory.resolve("api/language.json"), "en_us", api);
        delayed.readAtStartup(0);
        check(!delayed.isStartupComplete(), "API initialization NPE is treated as not ready");
        com.ogulniega.OgulniegaModApi.failure = null;
        com.ogulniega.OgulniegaModApi.language = "POLISH";
        check(delayed.readAtStartup(250_000_000L) && delayed.language().equals("pl_pl"), "reflective adapter reads the supported public API");
        delayed.readAtStartup(1_000_000_000L);
        check(com.ogulniega.OgulniegaModApi.calls == 2, "reflective API stops after one valid result");

        var loaderCalls = new int[1];
        ClassLoader missing = new ClassLoader(null) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                loaderCalls[0]++;
                throw new ClassNotFoundException(name);
            }
        };
        var absent = new LanguageSettings(directory.resolve("absent-api/language.json"), "en_us", new OgulniegaLanguage(false, missing));
        check(absent.isStartupComplete() && loaderCalls[0] == 0, "absent mod does not load or search API classes");
        var oldMod = new LanguageSettings(directory.resolve("old-api/language.json"), "pl_pl", new OgulniegaLanguage(true, missing));
        oldMod.readAtStartup(0);
        check(oldMod.isStartupComplete() && oldMod.language().equals("pl_pl"), "missing API terminates immediately with the last language");
        oldMod.readAtStartup(500_000_000L);
        check(loaderCalls[0] == 1, "missing API is not repeatedly searched");
        com.ogulniega.OgulniegaModApi.failure = new IllegalStateException("API broken");
        var broken = new LanguageSettings(directory.resolve("broken-api/language.json"), "pl_pl", new OgulniegaLanguage(true));
        broken.readAtStartup(0);
        check(broken.isStartupComplete() && broken.language().equals("pl_pl"), "permanent API failures stop without crashing");
        com.ogulniega.OgulniegaModApi.failure = null;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
