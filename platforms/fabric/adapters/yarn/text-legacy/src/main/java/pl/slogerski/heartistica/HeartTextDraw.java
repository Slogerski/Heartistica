package pl.slogerski.heartistica;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

final class HeartTextDraw {
    private HeartTextDraw() {}
    static void draw(TextRenderer renderer, Text text, float x, int color, MatrixStack matrices,
                     VertexConsumerProvider provider, int light) {
        renderer.draw(text, x, 0, color, true, matrices.peek().getPositionMatrix(), provider,
                false, 0, light);
    }
}
