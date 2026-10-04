package pl.slogerski.heartistica;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class HeartWorldRender {
    private HeartWorldRender() {}

    static void register(Callback callback) {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> callback.render(new Context(
                context.matrixStack(), context.camera(), HeartClassicTickDelta.get(context),
                context.frustum() == null ? null : context.frustum()::isVisible)));
    }

    @FunctionalInterface interface Callback { void render(Context context); }
    @FunctionalInterface private interface Visibility { boolean test(Box box); }

    static final class Context {
        private final MatrixStack matrices;
        private final Camera camera;
        private final float tickDelta;
        private final Visibility visibility;

        Context(MatrixStack matrices, Camera camera, float tickDelta, Visibility visibility) {
            this.matrices = matrices;
            this.camera = camera;
            this.tickDelta = tickDelta;
            this.visibility = visibility;
        }
        MatrixStack matrices() { return matrices; }
        Camera camera() { return camera; }
        Vec3d cameraPos() { return camera.getPos(); }
        float tickDelta() { return tickDelta; }
        boolean isVisible(Box box) { return visibility == null || visibility.test(box); }
    }
}
