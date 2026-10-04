package pl.slogerski.heartistica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

final class HeartStyleResolver {
    private static final Identifier GUI_ATLAS = Identifier.of("minecraft", "textures/atlas/gui.png");
    private HeartStyleResolver() {}
    static HeartStyles.Resolved resolve(MinecraftClient client, Identifier[] ids, Object previous, boolean hardcore) {
        Sprite first = get(client, ids[0]);
        if (first == previous) return null;
        HeartStyles.Icon[] icons = new HeartStyles.Icon[ids.length];
        for (int i = 0; i < icons.length; i++) {
            Sprite sprite = get(client, ids[i]);
            icons[i] = new HeartStyles.Icon(sprite, sprite.getAtlasId(), sprite.getMinU(), sprite.getMinV(),
                    sprite.getMaxU(), sprite.getMaxV(), sprite.getContents().getWidth(),
                    sprite.getContents().getHeight(), 0, 0);
        }
        return new HeartStyles.Resolved(icons, HeartRenderLayer.text(first.getAtlasId()), first);
    }
    private static Sprite get(MinecraftClient client, Identifier id) {
        return client.getAtlasManager().getSprite(new SpriteIdentifier(GUI_ATLAS, id));
    }
}
