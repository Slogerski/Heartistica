package pl.slogerski.heartistica;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

final class PlayerHeartRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int HEART_COLOR = 0xFFFF4057;
    private static final int ABSORPTION_COLOR = 0xFFFFC83D;
    private static final float HEART_HEIGHT = 8;
    private static final float ROW_ADVANCE = HEART_HEIGHT;
    private static final Map<PlayerEntity, PlayerState> STATES = new IdentityHashMap<>();
    private static final List<PlayerState> SELECTED = new ArrayList<>();
    private static final PriorityQueue<PlayerState> NEAREST = new PriorityQueue<>(
            Comparator.comparingDouble((PlayerState state) -> state.distanceSquared).reversed());
    private static ClientWorld world;
    private static long tick;
    private static HeartRenderBuffers renderBuffers;

    private PlayerHeartRenderer() { }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PlayerHeartRenderer::update);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        HeartWorldRender.register(PlayerHeartRenderer::render);
    }

    private static void clear() {
        STATES.clear();
        SELECTED.clear();
        NEAREST.clear();
        world = null;
        if (renderBuffers != null) {
            renderBuffers.close();
            renderBuffers = null;
        }
    }

    private static void update(MinecraftClient client) {
        HeartisticaConfig config = HeartisticaClient.config();
        if (client.world != world) clear();
        world = client.world;
        if (world == null || client.player == null || !config.enabled) {
            if (!STATES.isEmpty() || renderBuffers != null) clear();
            return;
        }
        tick++;
        long now = Util.getMeasuringTimeMs();
        double rangeSquared = config.rangeSquared();
        SELECTED.clear();
        NEAREST.clear();
        for (PlayerEntity player : world.getPlayers()) {
            if (!baseEligible(client.player, player)) continue;
            double distance = client.player.squaredDistanceTo(player);
            if (distance > rangeSquared) continue;
            PlayerState state = STATES.computeIfAbsent(player, PlayerState::new);
            state.seenTick = tick;
            state.distanceSquared = distance;
            if (state.display.update(player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount(),
                    config.numericDisplay, config.onlyAbsorption, now)) {
                state.text = Text.literal(state.display.label);
                state.textWidth = client.textRenderer.getWidth(state.text);
                state.anchorWidth = client.textRenderer.getWidth(state.display.anchorLabel);
                state.visibilityTick = -1;
            }
            if (!matchesDisplay(player, config)) continue;
            if (config.nearestPlayers == 0) SELECTED.add(state);
            else if (NEAREST.size() < config.nearestPlayers) NEAREST.offer(state);
            else if (distance < NEAREST.peek().distanceSquared) {
                NEAREST.poll();
                NEAREST.offer(state);
            }
        }
        if (config.nearestPlayers > 0) SELECTED.addAll(NEAREST);
        STATES.values().removeIf(state -> state.seenTick != tick);
    }

    private static boolean matchesDisplay(PlayerEntity player, HeartisticaConfig config) {
        return config.shouldDisplayHealth(player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount());
    }

    private static boolean baseEligible(PlayerEntity viewer, PlayerEntity player) {
        return player != viewer && !player.isRemoved() && player.isAlive() && !player.isSpectator()
                && (!player.isInvisible() || hasVisibleArmor(player));
    }

    private static boolean hasVisibleArmor(PlayerEntity player) {
        return !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private static void render(HeartWorldRender.Context context) {
        MinecraftClient client = MinecraftClient.getInstance();
        HeartisticaConfig config = HeartisticaClient.config();
        MatrixStack matrices = context.matrices();
        if (client.player == null || world == null || world != client.world || matrices == null
                || !config.enabled || SELECTED.isEmpty()) return;
        Camera camera = context.camera();
        Vec3d cameraPos = context.cameraPos();
        float tickDelta = context.tickDelta();
        float scale = 0.025F * config.scalePercent / 100;
        HeartStyles.Style style = HeartStyles.selected();
        style.resolve(client);
        float heartWidth = HEART_HEIGHT * style.icon(0).width() / style.icon(0).height();
        float heartAdvance = Math.max(0, heartWidth - 1);
        if (renderBuffers != null && renderBuffers.layer != style.layer()) {
            renderBuffers.close();
            renderBuffers = null;
        }
        boolean rendered = false;
        try {
            for (PlayerState state : SELECTED) {
                PlayerEntity player = state.player;
                if (!baseEligible(client.player, player) || !matchesDisplay(player, config)) continue;
                HeartDisplayState display = state.display;
                boolean numeric = !display.label.isEmpty();
                if (!numeric && display.slots == 0) continue;
                Vec3d position = player.getLerpedPos(tickDelta);
                double anchorY = position.y + player.getHeight() + 0.62 + config.heightOffsetPixels / 16.0;
                int rows = numeric ? 1 : (display.slots + HeartDisplayState.HEARTS_PER_ROW - 1)
                        / HeartDisplayState.HEARTS_PER_ROW;
                float startX = numeric ? -(state.anchorWidth + 1 + heartWidth) / 2
                        : -display.anchorSlots * heartAdvance / 2;
                float width = numeric ? state.textWidth + 1 + heartWidth
                        : (Math.min(display.slots, HeartDisplayState.HEARTS_PER_ROW) - 1) * heartAdvance + heartWidth;
                double radius = Math.hypot(Math.max(Math.abs(startX), Math.abs(startX + width)),
                        Math.max(HEART_HEIGHT, (rows - 1) * ROW_ADVANCE)) * scale;
                if (!context.isVisible(new Box(
                        position.x - radius, anchorY - radius, position.z - radius,
                        position.x + radius, anchorY + radius, position.z + radius))) continue;

                if (state.visibilityTick != tick || state.visibilityCamera == null
                        || state.visibilityCamera.squaredDistanceTo(cameraPos) > 1
                        || state.visibilityPosition.squaredDistanceTo(position) > 1
                        || state.visibilityHeight != config.heightOffsetPixels
                        || state.visibilityScale != config.scalePercent) {
                    state.visible = hasClearView(client, player, cameraPos, position, anchorY, rows, scale);
                    state.visibilityTick = tick;
                    state.visibilityCamera = cameraPos;
                    state.visibilityPosition = position;
                    state.visibilityHeight = config.heightOffsetPixels;
                    state.visibilityScale = config.scalePercent;
                }
                if (!state.visible) continue;
                if (renderBuffers == null) renderBuffers = new HeartRenderBuffers(style.layer());
                rendered = true;
                matrices.push();
                try {
                    matrices.translate(position.x - cameraPos.x, anchorY - cameraPos.y, position.z - cameraPos.z);
                    matrices.multiply(camera.getRotation());
                    HeartBillboard.scale(matrices, scale);
                    VertexConsumer vertices = renderBuffers.provider.getBuffer(style.layer());
                    if (numeric) {
                        HeartTextDraw.draw(client.textRenderer, state.text, startX,
                                display.goldLabel ? ABSORPTION_COLOR : HEART_COLOR,
                                matrices, renderBuffers.provider, FULL_BRIGHT);
                        drawHeart(vertices, matrices, style, display.goldLabel ? HeartDisplayState.GOLD : HeartDisplayState.FULL,
                                startX + state.textWidth + 1, 0);
                    } else {
                        for (int slot = 0; slot < display.slots; slot++) {
                            byte sprite = display.sprites[slot];
                            if (sprite == HeartDisplayState.HIDDEN) continue;
                            drawHeart(vertices, matrices, style, sprite,
                                    startX + slot % HeartDisplayState.HEARTS_PER_ROW * heartAdvance,
                                    -(slot / HeartDisplayState.HEARTS_PER_ROW) * ROW_ADVANCE);
                        }
                    }
                } finally {
                    matrices.pop();
                }
            }
        } finally {
            if (rendered) renderBuffers.provider.draw();
        }
    }

    private static boolean hasClearView(MinecraftClient client, PlayerEntity player, Vec3d camera,
                                        Vec3d position, double anchorY, int rows, float scale) {
        Vec3d eye = position.add(0, player.getEyeY() - player.getY(), 0);
        Vec3d top = new Vec3d(position.x,
                Math.max(eye.y + 0.05, anchorY + (rows - 1) * ROW_ADVANCE * scale + 0.05), position.z);
        if (!clearRay(client, player, eye, top)) return false;
        if (clearRay(client, player, camera, eye)) return true;
        return clearRay(client, player, camera, position.add(0, player.getHeight() * 0.5, 0));
    }

    private static boolean clearRay(MinecraftClient client, PlayerEntity player, Vec3d from, Vec3d to) {
        return client.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static void drawHeart(VertexConsumer vertices, MatrixStack matrices, HeartStyles.Style style,
                                  int sprite, float x, float y) {
        if (style.needsContainer(sprite)) drawIcon(vertices, matrices, style.icon(HeartDisplayState.EMPTY), x, y);
        drawIcon(vertices, matrices, style.icon(sprite), x, y);
    }

    private static void drawIcon(VertexConsumer vertices, MatrixStack matrices, HeartStyles.Icon icon, float x, float y) {
        var matrix = matrices.peek().getPositionMatrix();
        float u0 = icon.u0(), u1 = icon.u1(), v0 = icon.v0(), v1 = icon.v1();
        float heartWidth = HEART_HEIGHT * icon.width() / icon.height();
        vertices.vertex(matrix, x, y, 0).color(0xFFFFFFFF).texture(u0, v0).light(FULL_BRIGHT);
        HeartRenderBuffers.endVertex(vertices);
        vertices.vertex(matrix, x, y + HEART_HEIGHT, 0).color(0xFFFFFFFF).texture(u0, v1).light(FULL_BRIGHT);
        HeartRenderBuffers.endVertex(vertices);
        vertices.vertex(matrix, x + heartWidth, y + HEART_HEIGHT, 0).color(0xFFFFFFFF).texture(u1, v1).light(FULL_BRIGHT);
        HeartRenderBuffers.endVertex(vertices);
        vertices.vertex(matrix, x + heartWidth, y, 0).color(0xFFFFFFFF).texture(u1, v0).light(FULL_BRIGHT);
        HeartRenderBuffers.endVertex(vertices);
    }

    private static final class PlayerState {
        final PlayerEntity player;
        final HeartDisplayState display = new HeartDisplayState();
        long seenTick, visibilityTick = -1;
        double distanceSquared;
        Text text = Text.empty();
        int textWidth, anchorWidth, visibilityHeight, visibilityScale;
        Vec3d visibilityCamera, visibilityPosition;
        boolean visible;

        PlayerState(PlayerEntity player) { this.player = player; }
    }

}
