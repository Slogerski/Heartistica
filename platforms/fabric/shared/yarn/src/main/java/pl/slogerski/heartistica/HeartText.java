package pl.slogerski.heartistica;

import net.minecraft.text.Text;

final class HeartText {
    static Text language() { return Text.literal(HeartisticaClient.languageLabel()); }
    static Text events() { return Text.literal(HeartisticaClient.catalog().eventsLabel(HeartisticaClient.config())); }
    static Text text(String key, Object... args) {
        return Text.literal(UiText.get(HeartisticaClient.config().language, key, args));
    }
    static Text toggle(String key, boolean enabled) {
        return Text.literal(UiText.get(HeartisticaClient.config().language, key) + ": "
                + UiText.get(HeartisticaClient.config().language, enabled ? "heartistica.on" : "heartistica.off"));
    }
}
