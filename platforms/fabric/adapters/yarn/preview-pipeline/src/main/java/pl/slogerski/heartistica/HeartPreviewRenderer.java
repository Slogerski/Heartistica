package pl.slogerski.heartistica;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

final class HeartPreviewRenderer {
    private HeartPreviewRenderer() {}

    static void draw(DrawContext context, HeartStyles.Icon icon, int x, int y, int width, int height) {
        if (icon.sprite() != null) {
            context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, icon.sprite(), x, y, width, height);
        } else {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, icon.texture(), x, y,
                    icon.u0() * icon.atlasWidth(), icon.v0() * icon.atlasHeight(),
                    width, height, icon.width(), icon.height(), icon.atlasWidth(), icon.atlasHeight());
        }
    }
}
