package pl.slogerski.heartistica;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class HeartisticaClient implements ClientModInitializer {
    private static HeartisticaConfig config;
    private static ServerProfiles profiles;
    private static CatalogService catalog;
    private static boolean catalogStarted;
    private static LanguageSettings languages;
    private static boolean clientStarted;

    static void changeLanguage() {
        languages.cycle();
        applyLanguage();
    }

    static void resetConfig() {
        config.reset();
        languages.reset();
        applyLanguage();
    }

    static String languageLabel() {
        String option = languages.mode().id;
        if (languages.mode() == LanguageSettings.Mode.OGULNIEGA && !languages.isOgulniegaPresent()) option = "ogulniega_missing";
        return UiText.get(config.language, "heartistica.language", UiText.get(config.language, "heartistica.language." + option));
    }

    private static void applyLanguage() {
        config.language = languages.language();
        profiles.saveLanguage();
    }

    public static HeartisticaConfig config() {
        return config;
    }

    static ServerProfiles profiles() {
        return profiles;
    }

    static CatalogService catalog() { return catalog; }

    @Override
    public void onInitializeClient() {
        config = HeartisticaConfig.load();
        var directory = FabricLoader.getInstance().getConfigDir();
        profiles = new ServerProfiles(config, directory.resolve("heartistica.json"), directory.resolve("heartistica-servers.json"));
        languages = new LanguageSettings(directory.resolve("heartistica/language.json"), config.language,
                new OgulniegaLanguage(FabricLoader.getInstance().isModLoaded("ogulniega-mod")));
        applyLanguage();
        HeartStyles.register();
        String fallback = "{\"schemaVersion\":1,\"categories\":{\"Event\":[],\"Popular\":[\"resource_pack\",\"heartistica\"]}}";
        try (var stream = HeartisticaClient.class.getResourceAsStream("/assets/heartistica/catalog.json")) {
            if (stream != null) fallback = new String(stream.readNBytes(262_144), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            System.getLogger("Heartistica").log(System.Logger.Level.WARNING, "Could not read bundled catalog", error);
        }
        catalog = new CatalogService(directory.resolve("heartistica-cache"), fallback, HeartStyles::installRemote);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client -> clientStarted = true);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!catalogStarted && clientStarted && HeartStyles.loadingComplete()) {
                catalogStarted = true;
                catalog.start();
            }
            if (catalogStarted && !languages.isStartupComplete() && languages.readAtStartup(System.nanoTime())) applyLanguage();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> catalog.close());
        PlayerHeartRenderer.register();
    }
}
