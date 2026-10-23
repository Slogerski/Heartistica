package pl.slogerski.heartistica;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

final class HeartCatalogCanvas implements CatalogView.Canvas {
    private final GuiGraphicsExtractor graphics;
    private final Minecraft minecraft;
    HeartCatalogCanvas(GuiGraphicsExtractor graphics, Minecraft minecraft) { this.graphics = graphics; this.minecraft = minecraft; }
    public void fill(int x, int y, int right, int bottom, int color) { graphics.fill(x, y, right, bottom, color); }
    public void text(String text, int centerX, int y, int color) { graphics.centeredText(minecraft.font, Component.literal(text), centerX, y, color); }
    public void heart(String id, int x, int y, int size) {
        HeartStyles.Style style = HeartStyles.find(id);
        if (style == null) { text("…", x + size / 2, y + size / 2 - 4, 0xFFAAAAAA); return; }
        style.resolve(minecraft);
        int sprite = HeartisticaClient.config().onlyAbsorption ? HeartDisplayState.GOLD : HeartDisplayState.FULL;
        var icon = style.icon(sprite);
        double fit = size / (double) Math.max(icon.width(), icon.height());
        int w = Math.max(1, (int) Math.round(icon.width() * fit)), h = Math.max(1, (int) Math.round(icon.height() * fit));
        int left = x + (size - w) / 2, top = y + (size - h) / 2;
        if (style.needsContainer(sprite)) draw(style.icon(HeartDisplayState.EMPTY), left, top, w, h);
        draw(icon, left, top, w, h);
    }
    private void draw(HeartStyles.Icon icon, int x, int y, int w, int h) {
        if (icon.sprite() != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite(), x, y, w, h);
        else graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), x, y,
                icon.u0() * icon.atlasWidth(), icon.v0() * icon.atlasHeight(), w, h,
                icon.width(), icon.height(), icon.atlasWidth(), icon.atlasHeight());
    }
    public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
    public void unclip() { graphics.disableScissor(); }
}
