package pl.slogerski.heartistica;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class HeartisticaClient implements ClientModInitializer {
    private static HeartisticaConfig config;
    private static ServerProfiles profiles;

    public static HeartisticaConfig config() {
        return config;
    }

    static ServerProfiles profiles() {
        return profiles;
    }

    @Override
    public void onInitializeClient() {
        config = HeartisticaConfig.load();
        var directory = FabricLoader.getInstance().getConfigDir();
        profiles = new ServerProfiles(config, directory.resolve("heartistica.json"), directory.resolve("heartistica-servers.json"));
        HeartStyles.register();
        PlayerHeartRenderer.register();
    }
}
