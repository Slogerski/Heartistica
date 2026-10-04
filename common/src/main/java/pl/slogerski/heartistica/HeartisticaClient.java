package pl.slogerski.heartistica;

import net.fabricmc.api.ClientModInitializer;

public final class HeartisticaClient implements ClientModInitializer {
    private static HeartisticaConfig config;

    public static HeartisticaConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = HeartisticaConfig.load();
        HeartStyles.register();
        PlayerHeartRenderer.register();
    }
}
