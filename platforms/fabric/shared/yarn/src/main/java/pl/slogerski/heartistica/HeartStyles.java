package pl.slogerski.heartistica;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
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
    private static final Style VANILLA = new Style("resource_pack", "", null);
    private static final Identifier[] HARDCORE_HUD = {
            Identifier.of("minecraft", "hud/heart/hardcore_full"), Identifier.of("minecraft", "hud/heart/hardcore_half"),
            Identifier.of("minecraft", "hud/heart/container_hardcore"), Identifier.of("minecraft", "hud/heart/absorbing_hardcore_full"),
            Identifier.of("minecraft", "hud/heart/absorbing_hardcore_half")
    };
    private static volatile List<Style> styles = List.of(VANILLA);

    static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override public Identifier getFabricId() { return Identifier.of("heartistica", "heart_styles"); }
                    @Override public void reload(ResourceManager manager) { load(manager); }
                });
    }

    private static void load(ResourceManager manager) {
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
                Icon[] icons = new Icon[5];
                for (int i = 0; i < icons.length; i++) icons[i] = new Icon(null, texture,
                        (i * (w + 2) + 1) / (float) aw, 1F / ah,
                        (i * (w + 2) + 1 + w) / (float) aw, (1F + h) / ah, w, h, aw, ah);
                loaded.add(new Style(id, data.get("name").getAsString(), icons));
            }
        } catch (Exception exception) {
            LoggerFactory.getLogger("Heartistica").warn("Could not load heart styles", exception);
        }
        styles = List.copyOf(loaded);
    }

    static List<Style> all() { return styles; }
    static Style selected() {
        String id = HeartisticaClient.config().heartStyle;
        return styles.stream().filter(style -> style.id.equals(id)).findFirst().orElse(VANILLA);
    }

    static final class Style {
        final String id;
        private final String name;
        private Icon[] icons;
        private Object lastHudSprite;
        private boolean lastHardcore;
        private RenderLayer layer;

        Style(String id, String name, Icon[] icons) {
            this.id = id;
            this.name = name;
            this.icons = icons;
            if (icons != null) layer = HeartRenderLayer.text(icons[0].texture);
        }

        Text name() { return id.equals("resource_pack") ? Text.translatable("heartistica.style.resource_pack") : Text.literal(name); }

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
        boolean needsContainer(int index) { return id.equals("resource_pack") && index != HeartDisplayState.EMPTY; }
    }

    record Resolved(Icon[] icons, RenderLayer layer, Object token) { }

    record Icon(Sprite sprite, Identifier texture, float u0, float v0, float u1, float v1,
                int width, int height, int atlasWidth, int atlasHeight) { }
}
