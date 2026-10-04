package pl.slogerski.heartistica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

final class HeartGuiSprites {
    private HeartGuiSprites() {}
    static Sprite get(MinecraftClient client, Identifier id) { return client.getGuiAtlasManager().getSprite(id); }
}
