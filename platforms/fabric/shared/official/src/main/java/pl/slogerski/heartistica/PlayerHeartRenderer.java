package pl.slogerski.heartistica;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Matrix4f;

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
    private static final float HEART_HEIGHT = 8, ROW_ADVANCE = HEART_HEIGHT;
    private static final Map<AbstractClientPlayer, PlayerState> STATES = new IdentityHashMap<>();
    private static final List<PlayerState> SELECTED = new ArrayList<>();
    private static final PriorityQueue<PlayerState> NEAREST = new PriorityQueue<>(
            Comparator.comparingDouble((PlayerState state) -> state.distanceSquared).reversed());
    private static ClientLevel level;
    private static long tick;

    private PlayerHeartRenderer() {}
    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PlayerHeartRenderer::update);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var entry = client.getCurrentServer();
            HeartisticaClient.profiles().useServer(entry == null ? null : entry.ip);
            clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HeartisticaClient.profiles().useServer(null);
            clear();
        });
        OfficialWorldRender.register(PlayerHeartRenderer::render);
    }
    private static void clear() { STATES.clear(); SELECTED.clear(); NEAREST.clear(); level = null; }

    private static void update(Minecraft minecraft) {
        HeartisticaConfig config = HeartisticaClient.config();
        if (minecraft.level != level) clear();
        level = minecraft.level;
        if (level == null || minecraft.player == null || !config.enabled) { clear(); return; }
        tick++;
        long now = Util.getMillis();
        double rangeSquared = config.rangeSquared();
        SELECTED.clear(); NEAREST.clear();
        for (AbstractClientPlayer player : level.players()) {
            if (!baseEligible(minecraft.player, player)) continue;
            double distance = minecraft.player.distanceToSqr(player);
            if (distance > rangeSquared) continue;
            PlayerState state = STATES.computeIfAbsent(player, PlayerState::new);
            state.seenTick = tick; state.distanceSquared = distance;
            if (state.display.update(player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount(),
                    config.numericDisplay, config.onlyAbsorption, config.onlyWhenDamaged, now)) {
                state.text = Component.literal(state.display.label);
                state.textWidth = minecraft.font.width(state.text);
                state.anchorWidth = minecraft.font.width(state.display.anchorLabel);
                state.visibilityTick = -1;
            }
            if (!matchesDisplay(player, config)) continue;
            if (config.nearestPlayers == 0) SELECTED.add(state);
            else if (NEAREST.size() < config.nearestPlayers) NEAREST.offer(state);
            else if (distance < NEAREST.peek().distanceSquared) { NEAREST.poll(); NEAREST.offer(state); }
        }
        if (config.nearestPlayers > 0) SELECTED.addAll(NEAREST);
        STATES.values().removeIf(state -> state.seenTick != tick);
    }

    private static boolean matchesDisplay(AbstractClientPlayer player, HeartisticaConfig config) {
        return config.shouldDisplayHealth(player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount());
    }
    private static boolean baseEligible(AbstractClientPlayer viewer, AbstractClientPlayer player) {
        return player != viewer && !player.isRemoved() && player.isAlive() && !player.isSpectator()
                && (!player.isInvisible() || hasVisibleArmor(player));
    }
    private static boolean hasVisibleArmor(AbstractClientPlayer player) {
        return !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || !player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static void render(OfficialWorldRender.Context context) {
        Minecraft minecraft = Minecraft.getInstance();
        HeartisticaConfig config = HeartisticaClient.config();
        if (minecraft.player == null || minecraft.level != level || !config.enabled || SELECTED.isEmpty()) return;
        Camera camera = context.camera();
        Vec3 cameraPos = camera.position();
        float partialTick = context.partialTick();
        float scale = 0.025F * config.scalePercent / 100;
        HeartStyles.Style style = HeartStyles.selected();
        style.resolve(minecraft);
        float heartWidth = HEART_HEIGHT * style.icon(0).width() / style.icon(0).height();
        float advance = Math.max(0, heartWidth - 1);
        PoseStack pose = context.pose();
        Matrix4fc cameraRotation = new Matrix4f().rotation(camera.rotation());
        for (PlayerState state : SELECTED) {
            AbstractClientPlayer player = state.player;
            if (!baseEligible(minecraft.player, player) || !matchesDisplay(player, config)) continue;
            HeartDisplayState display = state.display;
            boolean numeric = !display.label.isEmpty();
            if (!numeric && display.slots == 0) continue;
            Vec3 position = player.getPosition(partialTick);
            double anchorY = position.y + player.getBbHeight() + config.heightAboveHead();
            int rows = numeric ? 1 : (display.slots + HeartDisplayState.HEARTS_PER_ROW - 1) / HeartDisplayState.HEARTS_PER_ROW;
            float startX = numeric ? -(state.anchorWidth + 1 + heartWidth) / 2 - 1
                    : display.iconStartX(heartWidth, advance);
            if (!inView(camera, cameraPos, position, anchorY)) continue;
            if (state.visibilityTick != tick || state.visibilityCamera == null
                    || state.visibilityCamera.distanceToSqr(cameraPos) > 1
                    || state.visibilityPosition.distanceToSqr(position) > 1
                    || state.visibilityHeight != config.heightOffsetPixels || state.visibilityScale != config.scalePercent) {
                state.visible = hasClearView(minecraft, player, cameraPos, position, anchorY, rows, scale);
                state.visibilityTick = tick; state.visibilityCamera = cameraPos; state.visibilityPosition = position;
                state.visibilityHeight = config.heightOffsetPixels; state.visibilityScale = config.scalePercent;
            }
            if (!state.visible) continue;
            pose.pushPose();
            pose.translate(position.x - cameraPos.x, anchorY - cameraPos.y, position.z - cameraPos.z);
            pose.mulPose(cameraRotation);
            pose.scale(scale, -scale, scale);
            if (numeric) {
                context.text(state.text, startX, display.goldLabel ? ABSORPTION_COLOR : HEART_COLOR, FULL_BRIGHT);
                int sprite = display.goldLabel ? HeartDisplayState.GOLD : HeartDisplayState.FULL;
                float x = startX + state.textWidth + 1;
                context.geometry(style.layer(), (matrix, vertices) -> drawHeart(vertices, matrix, style, sprite, x, 0));
            } else {
                context.geometry(style.layer(), (matrix, vertices) -> {
                    for (int slot = 0; slot < display.slots; slot++) {
                        byte sprite = display.sprites[slot];
                        if (sprite == HeartDisplayState.HIDDEN) continue;
                        drawHeart(vertices, matrix, style, sprite,
                                startX + slot % HeartDisplayState.HEARTS_PER_ROW * advance,
                                -(slot / HeartDisplayState.HEARTS_PER_ROW) * ROW_ADVANCE);
                    }
                });
            }
            pose.popPose();
        }
        context.finish();
    }

    private static boolean inView(Camera camera, Vec3 cameraPos, Vec3 target, double targetY) {
        double dx = target.x - cameraPos.x, dy = targetY - cameraPos.y, dz = target.z - cameraPos.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001) return true;
        var forward = camera.forwardVector();
        return (dx * forward.x() + dy * forward.y() + dz * forward.z()) / len > -0.15;
    }
    private static boolean hasClearView(Minecraft minecraft, AbstractClientPlayer player, Vec3 camera,
                                        Vec3 position, double anchorY, int rows, float scale) {
        Vec3 eye = position.add(0, player.getEyeY() - player.getY(), 0);
        Vec3 top = new Vec3(position.x, Math.max(eye.y + 0.05,
                anchorY + (rows - 1) * ROW_ADVANCE * scale + 0.05), position.z);
        if (!clearRay(minecraft, player, eye, top)) return false;
        return clearRay(minecraft, player, camera, eye)
                || clearRay(minecraft, player, camera, position.add(0, player.getBbHeight() * 0.5, 0));
    }
    private static boolean clearRay(Minecraft minecraft, AbstractClientPlayer player, Vec3 from, Vec3 to) {
        return minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }
    private static void drawHeart(VertexConsumer vertices, Matrix4fc matrix, HeartStyles.Style style,
                                  int sprite, float x, float y) {
        if (style.needsContainer(sprite)) drawIcon(vertices, matrix, style.icon(HeartDisplayState.EMPTY), x, y);
        drawIcon(vertices, matrix, style.icon(sprite), x, y);
    }
    private static void drawIcon(VertexConsumer vertices, Matrix4fc matrix, HeartStyles.Icon icon, float x, float y) {
        float width = HEART_HEIGHT * icon.width() / icon.height();
        vertices.addVertex(matrix, x, y, 0).setColor(0xFFFFFFFF).setUv(icon.u0(), icon.v0()).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, x, y + HEART_HEIGHT, 0).setColor(0xFFFFFFFF).setUv(icon.u0(), icon.v1()).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, x + width, y + HEART_HEIGHT, 0).setColor(0xFFFFFFFF).setUv(icon.u1(), icon.v1()).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, x + width, y, 0).setColor(0xFFFFFFFF).setUv(icon.u1(), icon.v0()).setLight(FULL_BRIGHT);
    }

    private static final class PlayerState {
        final AbstractClientPlayer player; final HeartDisplayState display = new HeartDisplayState();
        long seenTick, visibilityTick = -1; double distanceSquared;
        Component text = Component.empty(); int textWidth, anchorWidth, visibilityScale;
        double visibilityHeight;
        Vec3 visibilityCamera, visibilityPosition; boolean visible;
        PlayerState(AbstractClientPlayer player) { this.player = player; }
    }
}
