package pl.slogerski.heartistica;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import java.util.ArrayDeque;
import java.util.Deque;

final class HeartCatalogCanvas implements CatalogView.Canvas {
    private final MatrixStack matrices;
    private final MinecraftClient client;
    private final Deque<int[]> clips = new ArrayDeque<>();
    HeartCatalogCanvas(MatrixStack matrices, MinecraftClient client) { this.matrices = matrices; this.client = client; }
    public void fill(int x, int y, int right, int bottom, int color) { DrawableHelper.fill(matrices, x, y, right, bottom, color); }
    public void text(String text, int centerX, int y, int color) { DrawableHelper.drawCenteredText(matrices, client.textRenderer, Text.literal(text), centerX, y, color); }
    public void heart(String id, int x, int y, int size) {
        HeartStyles.Style style = HeartStyles.find(id);
        if (style == null) { text("…", x + size / 2, y + size / 2 - 4, 0xFFAAAAAA); return; }
        style.resolve(client);
        int sprite = HeartisticaClient.config().onlyAbsorption ? HeartDisplayState.GOLD : HeartDisplayState.FULL;
        var icon = style.icon(sprite);
        double fit = size / (double) Math.max(icon.width(), icon.height());
        int w = Math.max(1, (int) Math.round(icon.width() * fit)), h = Math.max(1, (int) Math.round(icon.height() * fit));
        int left = x + (size - w) / 2, top = y + (size - h) / 2;
        if (style.needsContainer(sprite)) draw(style.icon(HeartDisplayState.EMPTY), left, top, w, h);
        draw(icon, left, top, w, h);
    }
    private void draw(HeartStyles.Icon icon, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, icon.texture());
        DrawableHelper.drawTexture(matrices, x, y, w, h, icon.u0() * icon.atlasWidth(),
                icon.v0() * icon.atlasHeight(), icon.width(), icon.height(), icon.atlasWidth(), icon.atlasHeight());
    }
    public void clip(int x, int y, int right, int bottom) {
        if (!clips.isEmpty()) {
            int[] previous = clips.peek();
            x = Math.max(x, previous[0]); y = Math.max(y, previous[1]);
            right = Math.min(right, previous[2]); bottom = Math.min(bottom, previous[3]);
        }
        int[] rect = {x, y, Math.max(x, right), Math.max(y, bottom)};
        clips.push(rect);
        apply(rect);
    }
    public void unclip() { clips.pop(); if (clips.isEmpty()) RenderSystem.disableScissor(); else apply(clips.peek()); }
    private void apply(int[] rect) {
        double scale = client.getWindow().getScaleFactor();
        int left = (int) Math.floor(rect[0] * scale), right = (int) Math.ceil(rect[2] * scale);
        int top = (int) Math.floor(rect[1] * scale), bottom = (int) Math.ceil(rect[3] * scale);
        RenderSystem.enableScissor(left, client.getWindow().getFramebufferHeight() - bottom, right - left, bottom - top);
    }
}
