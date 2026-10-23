package pl.slogerski.heartistica;

import net.minecraft.network.chat.Component;

final class HeartText {
    static Component language() { return Component.literal(HeartisticaClient.languageLabel()); }
    static Component events() { return Component.literal(HeartisticaClient.catalog().eventsLabel(HeartisticaClient.config())); }
    static Component text(String key, Object... args) {
        return Component.literal(UiText.get(HeartisticaClient.config().language, key, args));
    }
    static Component toggle(String key, boolean enabled) {
        return Component.literal(UiText.get(HeartisticaClient.config().language, key) + ": "
                + UiText.get(HeartisticaClient.config().language, enabled ? "heartistica.on" : "heartistica.off"));
    }
}
