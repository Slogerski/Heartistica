package pl.slogerski.heartistica;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;

final class HeartPreviewRenderer {
    private HeartPreviewRenderer() {}

    static void draw(DrawContext context, HeartStyles.Icon icon, int x, int y, int width, int height) {
        if (icon.sprite() != null) {
            context.drawSpriteStretched(RenderLayer::getGuiTextured, icon.sprite(), x, y, width, height);
        } else {
            context.drawTexture(RenderLayer::getGuiTextured, icon.texture(), x, y,
                    Math.round(icon.u0() * icon.atlasWidth()), Math.round(icon.v0() * icon.atlasHeight()),
                    width, height, icon.width(), icon.height(), icon.atlasWidth(), icon.atlasHeight());
        }
    }
}
