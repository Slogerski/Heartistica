package pl.slogerski.heartistica;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4fc;
import org.joml.Matrix4f;

final class OfficialWorldRender {
    private OfficialWorldRender() {}
    static void register(Callback callback) {
        LevelRenderEvents.COLLECT_SUBMITS.register(raw -> callback.render(new Context(raw.poseStack(),
                raw.gameRenderer().mainCamera(), raw.submitNodeCollector(), Minecraft.getInstance())));
    }
    @FunctionalInterface interface Callback { void render(Context context); }
    @FunctionalInterface interface Geometry { void draw(Matrix4fc matrix, VertexConsumer vertices); }
    static final class Context {
        private final PoseStack pose; private final Camera camera;
        private final SubmitNodeCollector submits; private final Minecraft minecraft;
        Context(PoseStack pose, Camera camera, SubmitNodeCollector submits, Minecraft minecraft) {
            this.pose = pose; this.camera = camera; this.submits = submits; this.minecraft = minecraft;
        }
        PoseStack pose() { return pose; }
        Camera camera() { return camera; }
        float partialTick() { return minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false); }
        void geometry(RenderType layer, Geometry geometry) {
            submits.order(0).submitCustom(SubmitRenderPhases.AFTER_TERRAIN,
                    new CustomFeatureRenderer.Submit(pose.last().copy(), layer,
                            (entry, vertices) -> geometry.draw(entry.pose(), vertices)));
        }
        void text(Component text, float x, int color, int light) {
            submits.order(1).submitCustom(SubmitRenderPhases.AFTER_TERRAIN,
                    new TextFeatureRenderer.Submit(new Matrix4f(pose.last().pose()), x, 0,
                            text.getVisualOrderText(), false, Font.DisplayMode.NORMAL, light, color, 0, 0));
        }
        void finish() {}
    }
}
