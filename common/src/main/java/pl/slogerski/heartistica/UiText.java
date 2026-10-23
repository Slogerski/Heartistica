package pl.slogerski.heartistica;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class UiText {
    private static final Map<String, String> ENGLISH = load("en_us");
    private static final Map<String, String> POLISH = load("pl_pl");
    static String get(String language, String key, Object... args) {
        String pattern = ("pl_pl".equals(language) ? POLISH : ENGLISH).getOrDefault(key, ENGLISH.getOrDefault(key, key));
        try { return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args); }
        catch (java.util.IllegalFormatException error) { return pattern; }
    }
    private static Map<String, String> load(String language) {
        Map<String, String> result = new HashMap<>();
        try (var stream = UiText.class.getResourceAsStream("/assets/heartistica/lang/" + language + ".json")) {
            if (stream != null) try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) result.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (java.io.IOException | RuntimeException error) {
            System.getLogger("Heartistica").log(System.Logger.Level.WARNING, "Could not read UI language " + language, error);
        }
        return Map.copyOf(result);
    }
}
