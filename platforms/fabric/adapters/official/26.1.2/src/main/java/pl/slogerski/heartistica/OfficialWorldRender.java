package pl.slogerski.heartistica;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4fc;

final class OfficialWorldRender {
    private OfficialWorldRender() {}
    static void register(Callback callback) {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(raw -> callback.render(new Context(raw.poseStack(),
                raw.gameRenderer().getMainCamera(), raw.bufferSource(), Minecraft.getInstance())));
    }
    @FunctionalInterface interface Callback { void render(Context context); }
    @FunctionalInterface interface Geometry { void draw(Matrix4fc matrix, VertexConsumer vertices); }
    static final class Context {
        private final PoseStack pose; private final Camera camera;
        private final MultiBufferSource.BufferSource buffers; private final Minecraft minecraft;
        Context(PoseStack pose, Camera camera, MultiBufferSource.BufferSource buffers, Minecraft minecraft) {
            this.pose = pose; this.camera = camera; this.buffers = buffers; this.minecraft = minecraft;
        }
        PoseStack pose() { return pose; }
        Camera camera() { return camera; }
        float partialTick() { return minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false); }
        void geometry(RenderType layer, Geometry geometry) { geometry.draw(pose.last().pose(), buffers.getBuffer(layer)); }
        void text(Component text, float x, int color, int light) {
            minecraft.font.drawInBatch(text.getVisualOrderText(), x, 0, color, true, pose.last().pose(),
                    buffers, Font.DisplayMode.NORMAL, 0, light);
        }
        void finish() { buffers.endBatch(); }
    }
}
