package pl.slogerski.heartistica;

import net.minecraft.client.gui.DrawContext;

final class HeartPreviewRenderer {
    private HeartPreviewRenderer() {}

    static void draw(DrawContext context, HeartStyles.Icon icon, int x, int y, int width, int height) {
        if (icon.sprite() != null) {
            context.drawSprite(x, y, 0, width, height, icon.sprite());
        } else {
            context.drawTexture(icon.texture(), x, y, width, height,
                    icon.u0() * icon.atlasWidth(), icon.v0() * icon.atlasHeight(),
                    icon.width(), icon.height(), icon.atlasWidth(), icon.atlasHeight());
        }
    }
}
