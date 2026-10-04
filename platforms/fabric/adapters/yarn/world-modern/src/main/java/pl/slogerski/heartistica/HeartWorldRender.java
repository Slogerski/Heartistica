package pl.slogerski.heartistica;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class HeartWorldRender {
    private HeartWorldRender() {}

    static void register(Callback callback) {
        WorldRenderEvents.END_MAIN.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            callback.render(new Context(context.matrices(), client.gameRenderer.getCamera(),
                    client.getRenderTickCounter().getTickProgress(false)));
        });
    }

    @FunctionalInterface interface Callback { void render(Context context); }

    static final class Context {
        private final MatrixStack matrices;
        private final Camera camera;
        private final float tickDelta;
        Context(MatrixStack matrices, Camera camera, float tickDelta) {
            this.matrices = matrices;
            this.camera = camera;
            this.tickDelta = tickDelta;
        }
        MatrixStack matrices() { return matrices; }
        Camera camera() { return camera; }
        Vec3d cameraPos() { return HeartCameraPosition.get(camera); }
        float tickDelta() { return tickDelta; }
        boolean isVisible(Box box) { return true; }
    }
}
