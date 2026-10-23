package pl.slogerski.heartistica;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
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
    private static final Style VANILLA = new Style("resource_pack", null);
    private static final Identifier[] HARDCORE_HUD = {
            Identifier.withDefaultNamespace("hud/heart/hardcore_full"), Identifier.withDefaultNamespace("hud/heart/hardcore_half"),
            Identifier.withDefaultNamespace("hud/heart/container_hardcore"), Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_full"),
            Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_half")
    };
    private static volatile List<Style> styles = List.of(VANILLA);
    private static TextureAtlasSprite checkedSprite;

    private HeartStyles() {}
    static boolean loadingComplete() {
        return Minecraft.getInstance().isGameLoadFinished();
    }

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
                        HeartSpriteBounds content = HeartSpriteBounds.read(data, w, h);
                        Icon[] icons = new Icon[5];
                        for (int i = 0; i < icons.length; i++) {
                            int x = i * (w + 2) + 1 + content.x(), y = 1 + content.y();
                            icons[i] = new Icon(null, texture, x / (float)aw, y / (float)ah,
                                    (x + content.width()) / (float)aw, (y + content.height()) / (float)ah,
                                    content.width(), content.height(), aw, ah);
                        }
                        loaded.add(new Style(id, icons, w));
                    }
                }
            }
        } catch (java.io.IOException | RuntimeException error) {
            System.getLogger("Heartistica").log(System.Logger.Level.WARNING, "Could not load heart styles; keeping available hearts", error);
        }
        styles = List.copyOf(loaded);
    }
    static Style selected() {
        checkResourcePack();
        String id = HeartisticaClient.catalog() == null ? HeartisticaClient.config().heartStyle
                : HeartisticaClient.catalog().selected(HeartisticaClient.config());
        Style selected = find(id);
        if (selected == null && (HeartisticaClient.catalog() == null
                || !HeartisticaClient.catalog().needsUpdate(HeartisticaClient.config().heartStyle)))
            selected = find(HeartisticaClient.config().heartStyle);
        return selected == null ? VANILLA : selected;
    }

    static Style find(String id) {
        checkResourcePack();
        if (HeartisticaClient.catalog() != null) HeartisticaClient.catalog().request(id);
        return styles.stream().filter(style -> style.id.equals(id)).findFirst().orElse(null);
    }

    private static void checkResourcePack() {
        if (HeartisticaClient.catalog() == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        TextureAtlasSprite sprite = minecraft.getAtlasManager().get(new SpriteId(GUI_ATLAS, HUD[0]));
        if (sprite != checkedSprite) {
            checkedSprite = sprite;
            var manager = minecraft.getResourceManager();
            HeartisticaClient.catalog().checkResourcePack(sprite, () -> ResourcePackHearts.modified(manager));
        }
    }

    static void installRemote(CatalogImages.Atlas atlas) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            try {
                Identifier texture = Identifier.fromNamespaceAndPath("heartistica", "catalog/" + atlas.id());
                var image = com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(atlas.png()));
                net.minecraft.client.renderer.texture.DynamicTexture uploaded = null;
                try {
                    uploaded = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Heartistica Catalog", image);
                    minecraft.getTextureManager().register(texture, uploaded);
                } catch (RuntimeException | LinkageError error) {
                    try { if (uploaded == null) image.close(); else uploaded.close(); }
                    catch (RuntimeException | LinkageError cleanupError) { error.addSuppressed(cleanupError); }
                    throw error;
                }
                HeartSpriteBounds content = atlas.content();
                Icon[] icons = new Icon[5];
                for (int i = 0; i < 5; i++) {
                    int x = i * (atlas.width() + 2) + 1 + content.x(), y = 1 + content.y();
                    icons[i] = new Icon(null, texture, x / (float) atlas.atlasWidth(), y / (float) atlas.atlasHeight(),
                            (x + content.width()) / (float) atlas.atlasWidth(), (y + content.height()) / (float) atlas.atlasHeight(),
                            content.width(), content.height(), atlas.atlasWidth(), atlas.atlasHeight());
                }
                List<Style> updated = new ArrayList<>(styles);
                updated.removeIf(existing -> existing.id.equals(atlas.id()));
                updated.add(new Style(atlas.id(), icons, atlas.width()));
                styles = List.copyOf(updated);
            } catch (java.io.IOException | RuntimeException | LinkageError error) {
                HeartisticaClient.catalog().installationFailed(atlas.id());
                System.getLogger("Heartistica").log(System.Logger.Level.WARNING, "Could not upload catalog heart " + atlas.id(), error);
            }
        });
    }

    static final class Style {
        final String id;
        private final int sourceWidth;
        private Icon[] icons;
        private TextureAtlasSprite token;
        private boolean lastHardcore;
        private RenderType layer;
        Style(String id, Icon[] icons) {
            this(id, icons, icons == null ? 0 : icons[0].width());
        }
        Style(String id, Icon[] icons, int sourceWidth) {
            this.id = id; this.icons = icons;
            this.sourceWidth = sourceWidth;
            if (icons != null) layer = RenderTypes.text(icons[0].texture);
        }
        void resolve(Minecraft minecraft) {
            if (!id.equals("resource_pack")) return;
            boolean hardcore = minecraft.level != null && minecraft.level.getLevelData().isHardcore();
            Identifier[] hud = hardcore ? HARDCORE_HUD : HUD;
            TextureAtlasSprite first = sprite(minecraft, hud[0]);
            if (first == token && hardcore == lastHardcore) return;
            Icon[] resolved = new Icon[hud.length];
            for (int i = 0; i < resolved.length; i++) {
                TextureAtlasSprite sprite = sprite(minecraft, hud[i]);
                resolved[i] = new Icon(sprite, sprite.atlasLocation(), sprite.getU0(), sprite.getV0(),
                        sprite.getU1(), sprite.getV1(), sprite.contents().width(), sprite.contents().height(), 0, 0);
            }
            icons = resolved;
            layer = RenderTypes.text(first.atlasLocation());
            token = first;
            lastHardcore = hardcore;
        }
        private static TextureAtlasSprite sprite(Minecraft minecraft, Identifier id) {
            return minecraft.getAtlasManager().get(new SpriteId(GUI_ATLAS, id));
        }
        RenderType layer() { return layer; }
        Icon icon(int index) { return icons[index]; }
        int sourceWidth() { return sourceWidth == 0 ? icons[0].width() : sourceWidth; }
        boolean needsContainer(int index) { return id.equals("resource_pack") && index != HeartDisplayState.EMPTY; }
    }

    record Icon(TextureAtlasSprite sprite, Identifier texture, float u0, float v0, float u1, float v1,
                int width, int height, int atlasWidth, int atlasHeight) {}
}
