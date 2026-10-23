package pl.slogerski.heartistica;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HeartCatalog {
    static final int SCHEMA_VERSION = 1;
    static final int MAX_THEMES = 256;
    final Map<String, Theme> themes;
    final List<Category> categories;
    final String eventId;
    final Instant startsAt, endsAt;

    record Asset(URI url, String hash) {}
    record Theme(String id, String name, String layout, Map<String, Asset> images) {}
    record Category(String id, String name, List<String> hearts) {}

    private HeartCatalog(Map<String, Theme> themes, List<Category> categories, String eventId, Instant startsAt, Instant endsAt) {
        this.themes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(themes));
        this.categories = List.copyOf(categories);
        this.eventId = eventId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    static HeartCatalog parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("schemaVersion") || !root.get("schemaVersion").isJsonPrimitive()
                || !root.get("schemaVersion").getAsJsonPrimitive().isNumber()
                || root.get("schemaVersion").getAsBigDecimal().compareTo(java.math.BigDecimal.valueOf(SCHEMA_VERSION)) != 0) {
            throw new IllegalArgumentException("Unsupported catalog schema");
        }
        Map<String, Theme> themes = new LinkedHashMap<>();
        themes.put("resource_pack", new Theme("resource_pack", "Active Resource Pack", "builtin", Map.of()));
        themes.put("heartistica", new Theme("heartistica", "Heartistica", "builtin", Map.of()));
        JsonObject categories = root.getAsJsonObject("categories");
        if (categories == null || categories.size() > 32) throw new IllegalArgumentException("Invalid categories");
        for (var category : categories.entrySet()) {
            if (!category.getValue().isJsonArray() || category.getValue().getAsJsonArray().size() > MAX_THEMES) {
                throw new IllegalArgumentException("Invalid category entries");
            }
            for (JsonElement entry : category.getValue().getAsJsonArray()) {
                if (!entry.isJsonObject()) continue;
                Theme theme = theme(entry.getAsJsonObject());
                if (themes.containsKey(theme.id())) throw new IllegalArgumentException("Duplicate heart id: " + theme.id());
                themes.put(theme.id(), theme);
                if (themes.size() > MAX_THEMES) throw new IllegalArgumentException("Too many hearts");
            }
        }
        List<Category> rows = new ArrayList<>();
        for (var category : categories.entrySet()) {
            String name = category.getKey().strip();
            if (name.isEmpty() || name.length() > 64) throw new IllegalArgumentException("Invalid category name");
            String id = name.toLowerCase(Locale.ROOT);
            if (rows.stream().anyMatch(row -> row.id().equals(id))) throw new IllegalArgumentException("Duplicate category");
            List<String> hearts = new ArrayList<>();
            for (JsonElement entry : category.getValue().getAsJsonArray()) {
                String reference = entry.isJsonObject() ? entry.getAsJsonObject().get("id").getAsString() : entry.getAsString();
                String resolved = themes.containsKey(reference) ? reference : themes.values().stream()
                        .filter(theme -> theme.name().equals(reference)).map(Theme::id).findFirst().orElse(null);
                if (resolved == null) throw new IllegalArgumentException("Unknown heart reference: " + reference);
                if (!hearts.contains(resolved)) hearts.add(resolved);
            }
            rows.add(new Category(id, name, List.copyOf(hearts)));
        }
        if (rows.stream().noneMatch(row -> row.id().equals("event"))) rows.add(0, new Category("event", "Event", List.of()));
        if (rows.stream().noneMatch(row -> row.id().equals("popular"))) {
            rows.add(new Category("popular", "Popular", List.of("resource_pack", "heartistica")));
        }
        List<Category> ordered = new ArrayList<>();
        for (String special : List.of("event", "popular")) rows.stream().filter(row -> row.id().equals(special)).forEach(ordered::add);
        rows.stream().filter(row -> !row.id().equals("event") && !row.id().equals("popular")).forEach(ordered::add);
        JsonObject event = root.has("event") ? root.getAsJsonObject("event") : new JsonObject();
        String eventId = text(event, "id", "event");
        Instant start = event.has("startsAt") ? Instant.parse(event.get("startsAt").getAsString()) : Instant.MIN;
        Instant end = event.has("endsAt") ? Instant.parse(event.get("endsAt").getAsString()) : Instant.MAX;
        if (!end.isAfter(start)) throw new IllegalArgumentException("Invalid event dates");
        return new HeartCatalog(themes, ordered, eventId, start, end);
    }

    private static Theme theme(JsonObject data) {
        String id = data.get("id").getAsString();
        if (!id.matches("[a-z0-9_-]{1,64}") || id.equals("resource_pack") || id.equals("heartistica")) {
            throw new IllegalArgumentException("Invalid heart id");
        }
        String name = text(data, "name", id);
        if (name.isBlank() || name.length() > 80) throw new IllegalArgumentException("Invalid heart name");
        String layout = text(data, "layout", "single");
        if (!List.of("single", "strip", "sprites").contains(layout)) throw new IllegalArgumentException("Invalid image layout");
        Map<String, Asset> assets = new LinkedHashMap<>();
        if (layout.equals("sprites")) {
            JsonObject images = data.getAsJsonObject("images");
            for (String key : List.of("full", "half", "empty", "absorptionFull", "absorptionHalf")) {
                if (images.has(key)) assets.put(key, asset(images.get(key)));
            }
            if (!assets.keySet().containsAll(List.of("full", "half", "empty"))) throw new IllegalArgumentException("Missing heart sprites");
        } else {
            JsonElement image = data.get("image");
            if (image == null) throw new IllegalArgumentException("Missing heart image");
            if (image.isJsonPrimitive() && data.has("sha")) {
                JsonObject source = new JsonObject();
                source.add("url", image);
                source.add("sha", data.get("sha"));
                image = source;
            }
            assets.put("image", asset(image));
        }
        return new Theme(id, name, layout, java.util.Collections.unmodifiableMap(assets));
    }

    private static Asset asset(JsonElement value) {
        String url = value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("url").getAsString();
        String hash = value.isJsonObject() ? text(value.getAsJsonObject(), "sha", "") : "";
        if (!hash.isEmpty() && !hash.matches("(?i)[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid image SHA");
        return new Asset(imageUri(url), hash.toLowerCase(Locale.ROOT));
    }

    static URI imageUri(String value) {
        URI uri = URI.create(value);
        if ("github.com".equalsIgnoreCase(uri.getHost())) {
            String[] parts = uri.getPath().split("/", 5);
            if (parts.length != 5 || !parts[3].equals("blob")) throw new IllegalArgumentException("Use a GitHub file link");
            uri = URI.create("https://raw.githubusercontent.com/" + parts[1] + "/" + parts[2] + "/" + parts[4]);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                || uri.getPort() != -1 && uri.getPort() != 443) throw new IllegalArgumentException("Untrusted catalog image URL");
        return uri;
    }

    List<String> category(String id) {
        return categories.stream().filter(row -> row.id().equalsIgnoreCase(id)).findFirst().map(Category::hearts).orElse(List.of());
    }

    boolean eventActive(Instant now) { return !category("event").isEmpty() && !now.isBefore(startsAt) && now.isBefore(endsAt); }
    private static String text(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }
}
