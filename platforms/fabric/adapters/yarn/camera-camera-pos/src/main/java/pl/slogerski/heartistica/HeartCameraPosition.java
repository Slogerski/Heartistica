package pl.slogerski.heartistica;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
final class HeartCameraPosition {
    private HeartCameraPosition() {}
    static Vec3d get(Camera camera) { return camera.getCameraPos(); }
}
