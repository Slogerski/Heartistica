package pl.slogerski.heartistica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

final class HeartCatalogCanvas implements CatalogView.Canvas {
    private final DrawContext context;
    private final MinecraftClient client;
    HeartCatalogCanvas(DrawContext context, MinecraftClient client) { this.context = context; this.client = client; }
    public void fill(int x, int y, int right, int bottom, int color) { context.fill(x, y, right, bottom, color); }
    public void text(String text, int centerX, int y, int color) {
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(text), centerX, y, color);
    }
    public void heart(String id, int x, int y, int size) {
        HeartStyles.Style style = HeartStyles.find(id);
        if (style == null) { text("…", x + size / 2, y + size / 2 - 4, 0xFFAAAAAA); return; }
        style.resolve(client);
        int sprite = HeartisticaClient.config().onlyAbsorption ? HeartDisplayState.GOLD : HeartDisplayState.FULL;
        var icon = style.icon(sprite);
        double fit = size / (double) Math.max(icon.width(), icon.height());
        int w = Math.max(1, (int) Math.round(icon.width() * fit)), h = Math.max(1, (int) Math.round(icon.height() * fit));
        int left = x + (size - w) / 2, top = y + (size - h) / 2;
        if (style.needsContainer(sprite)) HeartPreviewRenderer.draw(context, style.icon(HeartDisplayState.EMPTY), left, top, w, h);
        HeartPreviewRenderer.draw(context, icon, left, top, w, h);
    }
    public void clip(int x, int y, int right, int bottom) { context.enableScissor(x, y, right, bottom); }
    public void unclip() { context.disableScissor(); }
}
