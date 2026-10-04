package pl.slogerski.heartistica;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.ArrayList;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class HeartStyles {
    private static final Identifier GUI_ATLAS = Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/gui.png");
    private static final Identifier[] HUD = {
            Identifier.withDefaultNamespace("hud/heart/full"), Identifier.withDefaultNamespace("hud/heart/half"),
            Identifier.withDefaultNamespace("hud/heart/container"), Identifier.withDefaultNamespace("hud/heart/absorbing_full"),
            Identifier.withDefaultNamespace("hud/heart/absorbing_half")
    };
    private static final Style VANILLA = new Style("resource_pack", "", null);
    private static volatile List<Style> styles = List.of(VANILLA);

    private HeartStyles() {}
    static void register() {
        List<Style> loaded = new ArrayList<>();
        loaded.add(VANILLA);
        try (var stream = HeartStyles.class.getResourceAsStream("/assets/heartistica/heart_styles.json")) {
            if (stream != null) {
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                        var data = entry.getAsJsonObject();
                        String id = data.get("id").getAsString();
                        if (!id.matches("[a-z0-9_-]+") || loaded.stream().anyMatch(style -> style.id.equals(id))) continue;
                        int w = data.get("width").getAsInt(), h = data.get("height").getAsInt();
                        int aw = data.get("atlasWidth").getAsInt(), ah = data.get("atlasHeight").getAsInt();
                        if (w < 1 || h < 1 || w > 1024 || h > 1024 || aw < (w + 2) * 5 || ah < h + 2) continue;
                        Identifier texture = Identifier.tryParse(data.get("texture").getAsString());
                        if (texture == null) continue;
                        Icon[] icons = new Icon[5];
                        for (int i = 0; i < icons.length; i++) {
                            int x = i * (w + 2) + 1;
                            icons[i] = new Icon(null, texture, x / (float)aw, 1F / ah,
                                    (x + w) / (float)aw, (1F + h) / ah, w, h, aw, ah);
                        }
                        loaded.add(new Style(id, data.get("name").getAsString(), icons));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        styles = List.copyOf(loaded);
    }
    static List<Style> all() { return styles; }
    static Style selected() {
        String selected = HeartisticaClient.config().heartStyle;
        return styles.stream().filter(style -> style.id.equals(selected)).findFirst().orElse(VANILLA);
    }

    static final class Style {
        final String id;
        private final String name;
        private Icon[] icons;
        private TextureAtlasSprite token;
        private RenderType layer;
        Style(String id, String name, Icon[] icons) {
            this.id = id; this.name = name; this.icons = icons;
            if (icons != null) layer = RenderTypes.text(icons[0].texture);
        }
        Component name() { return id.equals("resource_pack")
                ? Component.translatable("heartistica.style.resource_pack") : Component.literal(name); }
        void resolve(Minecraft minecraft) {
            if (!id.equals("resource_pack")) return;
            TextureAtlasSprite first = sprite(minecraft, HUD[0]);
            if (first == token) return;
            Icon[] resolved = new Icon[HUD.length];
            for (int i = 0; i < resolved.length; i++) {
                TextureAtlasSprite sprite = sprite(minecraft, HUD[i]);
                resolved[i] = new Icon(sprite, sprite.atlasLocation(), sprite.getU0(), sprite.getV0(),
                        sprite.getU1(), sprite.getV1(), sprite.contents().width(), sprite.contents().height(), 0, 0);
            }
            icons = resolved;
            layer = RenderTypes.text(first.atlasLocation());
            token = first;
        }
        private static TextureAtlasSprite sprite(Minecraft minecraft, Identifier id) {
            return minecraft.getAtlasManager().get(new SpriteId(GUI_ATLAS, id));
        }
        RenderType layer() { return layer; }
        Icon icon(int index) { return icons[index]; }
        boolean needsContainer(int index) { return id.equals("resource_pack") && index != HeartDisplayState.EMPTY; }
    }

    record Icon(TextureAtlasSprite sprite, Identifier texture, float u0, float v0, float u1, float v1,
                int width, int height, int atlasWidth, int atlasHeight) {}
}
