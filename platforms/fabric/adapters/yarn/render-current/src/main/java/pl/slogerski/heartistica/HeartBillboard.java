package pl.slogerski.heartistica;

import net.minecraft.client.util.math.MatrixStack;

final class HeartBillboard {
    private HeartBillboard() {}

    static void scale(MatrixStack matrices, float scale) {
        matrices.scale(scale, -scale, scale);
    }
}
