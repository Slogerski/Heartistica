package pl.slogerski.heartistica;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

final class HeartTickDelta {
    private HeartTickDelta() {}
    static float get(WorldRenderContext context) { return context.tickCounter().getTickProgress(false); }
}
