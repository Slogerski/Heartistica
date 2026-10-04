package pl.slogerski.heartistica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

final class HeartGuiSprites {
    private static final Identifier GUI_ATLAS = Identifier.of("minecraft", "textures/atlas/gui.png");
    private HeartGuiSprites() {}
    static Sprite get(MinecraftClient client, Identifier id) {
        return client.getAtlasManager().getSprite(new SpriteIdentifier(GUI_ATLAS, id));
    }
}
