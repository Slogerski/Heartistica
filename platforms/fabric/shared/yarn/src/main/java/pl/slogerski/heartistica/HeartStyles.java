package pl.slogerski.heartistica;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

final class HeartStyles {
    private static final Identifier[] HUD = {
            Identifier.of("minecraft", "hud/heart/full"), Identifier.of("minecraft", "hud/heart/half"),
            Identifier.of("minecraft", "hud/heart/container"), Identifier.of("minecraft", "hud/heart/absorbing_full"),
            Identifier.of("minecraft", "hud/heart/absorbing_half")
    };
    private static final Style VANILLA = new Style("resource_pack", null);
    private static final Identifier[] HARDCORE_HUD = {
            Identifier.of("minecraft", "hud/heart/hardcore_full"), Identifier.of("minecraft", "hud/heart/hardcore_half"),
            Identifier.of("minecraft", "hud/heart/container_hardcore"), Identifier.of("minecraft", "hud/heart/absorbing_hardcore_full"),
            Identifier.of("minecraft", "hud/heart/absorbing_hardcore_half")
    };
    private static volatile List<Style> styles = List.of(VANILLA);
    private static final java.util.Map<String, Style> remote = new java.util.concurrent.ConcurrentHashMap<>();

    static boolean loadingComplete() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getOverlay() == null;
    }

    static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override public Identifier getFabricId() { return Identifier.of("heartistica", "heart_styles"); }
                    @Override public void reload(ResourceManager manager) { load(manager); }
                });
    }

    private static void load(ResourceManager manager) {
        if (HeartisticaClient.catalog() != null) {
            HeartisticaClient.catalog().checkResourcePack(new Object(), () -> ResourcePackHearts.modified(manager));
        }
        List<Style> loaded = new ArrayList<>();
        loaded.add(VANILLA);
        try (var reader = manager.getResourceOrThrow(Identifier.of("heartistica", "heart_styles.json")).getReader()) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                var data = entry.getAsJsonObject();
                String id = data.get("id").getAsString();
                if (loaded.stream().anyMatch(style -> style.id.equals(id))) continue;
                int w = data.get("width").getAsInt(), h = data.get("height").getAsInt();
                int aw = data.get("atlasWidth").getAsInt(), ah = data.get("atlasHeight").getAsInt();
                if (w < 1 || h < 1 || w > 1024 || h > 1024 || aw < (w + 2) * 5 || ah < h + 2) continue;
                Identifier texture = Identifier.tryParse(data.get("texture").getAsString());
                if (texture == null) continue;
                HeartSpriteBounds content = HeartSpriteBounds.read(data, w, h);
                Icon[] icons = new Icon[5];
                for (int i = 0; i < icons.length; i++) icons[i] = new Icon(null, texture,
                        (i * (w + 2) + 1 + content.x()) / (float) aw, (1F + content.y()) / ah,
                        (i * (w + 2) + 1 + content.x() + content.width()) / (float) aw,
                        (1F + content.y() + content.height()) / ah, content.width(), content.height(), aw, ah);
                loaded.add(new Style(id, icons, w));
            }
        } catch (java.io.IOException | RuntimeException exception) {
            LoggerFactory.getLogger("Heartistica").warn("Could not load heart styles", exception);
        }
        loaded.addAll(remote.values());
        styles = List.copyOf(loaded);
    }

    static Style selected() {
        String id = HeartisticaClient.catalog() == null ? HeartisticaClient.config().heartStyle
                : HeartisticaClient.catalog().selected(HeartisticaClient.config());
        Style selected = find(id);
        if (selected == null && (HeartisticaClient.catalog() == null
                || !HeartisticaClient.catalog().needsUpdate(HeartisticaClient.config().heartStyle)))
            selected = find(HeartisticaClient.config().heartStyle);
        return selected == null ? VANILLA : selected;
    }

    static Style find(String id) {
        if (HeartisticaClient.catalog() != null) HeartisticaClient.catalog().request(id);
        return styles.stream().filter(style -> style.id.equals(id)).findFirst().orElse(null);
    }

    static void installRemote(CatalogImages.Atlas atlas) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                Identifier texture = Identifier.of("heartistica", "catalog/" + atlas.id());
                CatalogTexture.install(client, texture, atlas.png());
                HeartSpriteBounds content = atlas.content();
                Icon[] icons = new Icon[5];
                for (int i = 0; i < 5; i++) {
                    int x = i * (atlas.width() + 2) + 1 + content.x(), y = 1 + content.y();
                    icons[i] = new Icon(null, texture, x / (float) atlas.atlasWidth(), y / (float) atlas.atlasHeight(),
                            (x + content.width()) / (float) atlas.atlasWidth(), (y + content.height()) / (float) atlas.atlasHeight(),
                            content.width(), content.height(), atlas.atlasWidth(), atlas.atlasHeight());
                }
                Style style = new Style(atlas.id(), icons, atlas.width());
                remote.put(atlas.id(), style);
                List<Style> updated = new ArrayList<>(styles);
                updated.removeIf(existing -> existing.id.equals(atlas.id()));
                updated.add(style);
                styles = List.copyOf(updated);
            } catch (java.io.IOException | RuntimeException | LinkageError error) {
                HeartisticaClient.catalog().installationFailed(atlas.id());
                LoggerFactory.getLogger("Heartistica").warn("Could not upload catalog heart " + atlas.id(), error);
            }
        });
    }

    static final class Style {
        final String id;
        private final int sourceWidth;
        private Icon[] icons;
        private Object lastHudSprite;
        private boolean lastHardcore;
        private RenderLayer layer;

        Style(String id, Icon[] icons) {
            this(id, icons, icons == null ? 0 : icons[0].width());
        }

        Style(String id, Icon[] icons, int sourceWidth) {
            this.id = id;
            this.icons = icons;
            this.sourceWidth = sourceWidth;
            if (icons != null) layer = HeartRenderLayer.text(icons[0].texture);
        }

        void resolve(MinecraftClient client) {
            if (!id.equals("resource_pack")) return;
            boolean hardcore = client.world != null && client.world.getLevelProperties().isHardcore();
            if (hardcore != lastHardcore) lastHudSprite = null;
            Resolved resolved = HeartStyleResolver.resolve(client, hardcore ? HARDCORE_HUD : HUD, lastHudSprite, hardcore);
            if (resolved == null) return;
            icons = resolved.icons;
            layer = resolved.layer;
            lastHudSprite = resolved.token;
            lastHardcore = hardcore;
        }

        RenderLayer layer() { return layer; }
        Icon icon(int index) { return icons[index]; }
        int sourceWidth() { return sourceWidth == 0 ? icons[0].width() : sourceWidth; }
        boolean needsContainer(int index) { return id.equals("resource_pack") && index != HeartDisplayState.EMPTY; }
    }

    record Resolved(Icon[] icons, RenderLayer layer, Object token) { }

    record Icon(Sprite sprite, Identifier texture, float u0, float v0, float u1, float v1,
                int width, int height, int atlasWidth, int atlasHeight) { }
}
