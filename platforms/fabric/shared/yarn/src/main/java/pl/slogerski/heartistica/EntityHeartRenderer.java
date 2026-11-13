package pl.slogerski.heartistica;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import static pl.slogerski.heartistica.HeartDisplayState.ICON_HEIGHT;

final class EntityHeartRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int HEART_COLOR = 0xFFFF4057;
    private static final int ABSORPTION_COLOR = 0xFFFFC83D;
    private static final HeartTargets<LivingEntity, EntityState> TARGETS = new HeartTargets<>(EntityState::new);
    private static ClientWorld world;
    private static HeartRenderBuffers renderBuffers;

    private EntityHeartRenderer() { }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EntityHeartRenderer::update);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var entry = client.getCurrentServerEntry();
            HeartisticaClient.profiles().useServer(entry == null ? null : entry.address);
            clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HeartisticaClient.profiles().useServer(null);
            clear();
        });
        HeartWorldRender.register(EntityHeartRenderer::render);
    }

    private static void clear() {
        TARGETS.clear();
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
            clear();
            return;
        }
        TARGETS.beginTick();
        long now = Util.getMeasuringTimeMs();
        double rangeSquared = config.rangeSquared();
        for (var player : world.getPlayers()) updateEntity(client, player, config, rangeSquared, now);
        TARGETS.finishPlayers();
        if (mobsEnabled(client, config)) {
            Iterable<? extends Entity> nearby = config.range == 0 ? world.getEntities()
                    : world.getEntitiesByClass(MobEntity.class, client.player.getBoundingBox().expand(config.range), mob -> true);
            for (Entity entity : nearby) {
                if (entity instanceof MobEntity mob) updateEntity(client, mob, config, rangeSquared, now);
            }
        }
        TARGETS.finishTick();
    }

    private static void updateEntity(MinecraftClient client, LivingEntity entity, HeartisticaConfig config,
                                     double rangeSquared, long now) {
        if (!baseEligible(client.player, entity)) return;
        double distance = client.player.squaredDistanceTo(entity);
        if (distance > rangeSquared) return;
        EntityState state = TARGETS.update(entity, entity instanceof MobEntity, distance,
                entity.getHealth(), entity.getMaxHealth(), entity.getAbsorptionAmount(), config, now);
        if (state == null) return;
        state.text = Text.literal(state.display.label);
        state.textWidth = client.textRenderer.getWidth(state.text);
        state.anchorWidth = client.textRenderer.getWidth(state.display.anchorLabel);
        state.visibilityTick = -1;
    }

    private static boolean mobsEnabled(MinecraftClient client, HeartisticaConfig config) {
        var server = client.getServer();
        return config.showMobs(server != null && !server.isRemote());
    }

    private static boolean baseEligible(LivingEntity viewer, LivingEntity entity) {
        return entity != viewer && !entity.isRemoved() && entity.isAlive() && !entity.isSpectator()
                && (!entity.isInvisible() || hasVisibleArmor(entity));
    }

    private static boolean hasVisibleArmor(LivingEntity entity) {
        return !entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private static void render(HeartWorldRender.Context context) {
        MinecraftClient client = MinecraftClient.getInstance();
        HeartisticaConfig config = HeartisticaClient.config();
        MatrixStack matrices = context.matrices();
        if (client.player == null || world == null || world != client.world || matrices == null
                || !config.enabled || TARGETS.selected().isEmpty()) return;
        long tick = TARGETS.tick();
        boolean showMobs = mobsEnabled(client, config);
        Camera camera = context.camera();
        Vec3d cameraPos = context.cameraPos();
        float tickDelta = context.tickDelta();
        float scale = 0.025F * config.scalePercent / 100;
        HeartStyles.Style style = HeartStyles.selected();
        style.resolve(client);
        var icon = style.icon(HeartDisplayState.FULL);
        float heartWidth = ICON_HEIGHT * icon.width() / icon.height();
        float heartAdvance = HeartDisplayState.iconAdvance(heartWidth, style.sourceWidth());
        if (renderBuffers != null && renderBuffers.layer != style.layer()) {
            renderBuffers.close();
            renderBuffers = null;
        }
        boolean rendered = false;
        try {
            for (EntityState state : TARGETS.selected()) {
                LivingEntity entity = state.entity;
                if ((!showMobs && entity instanceof MobEntity) || !baseEligible(client.player, entity)
                        || !config.shouldDisplayHealth(entity.getHealth(), entity.getMaxHealth(), entity.getAbsorptionAmount())) continue;
                HeartDisplayState display = state.display;
                int rows = display.rows();
                if (rows == 0) continue;
                boolean numeric = !display.label.isEmpty();
                Vec3d position = entity.getLerpedPos(tickDelta);
                double anchorY = position.y + entity.getHeight() + config.heightAboveHead();
                float startX = display.startX(heartWidth, heartAdvance, state.anchorWidth);
                float width = numeric ? state.textWidth + 1 + heartWidth
                        : (Math.min(display.slots, HeartDisplayState.HEARTS_PER_ROW) - 1) * heartAdvance + heartWidth;
                double radius = Math.hypot(Math.max(Math.abs(startX), Math.abs(startX + width)),
                        Math.max(ICON_HEIGHT, (rows - 1) * ICON_HEIGHT)) * scale;
                if (!context.isVisible(new Box(
                        position.x - radius, anchorY - radius, position.z - radius,
                        position.x + radius, anchorY + radius, position.z + radius))) continue;

                if (state.visibilityTick != tick || state.visibilityCamera == null
                        || state.visibilityCamera.squaredDistanceTo(cameraPos) > 1
                        || state.visibilityPosition.squaredDistanceTo(position) > 1
                        || state.visibilityHeight != config.heightOffsetPixels
                        || state.visibilityScale != config.scalePercent) {
                    state.visible = hasClearView(client, entity, cameraPos, position, anchorY, rows, scale);
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
                                    -(slot / HeartDisplayState.HEARTS_PER_ROW) * ICON_HEIGHT);
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

    private static boolean hasClearView(MinecraftClient client, LivingEntity entity, Vec3d camera,
                                        Vec3d position, double anchorY, int rows, float scale) {
        Vec3d eye = position.add(0, entity.getEyeY() - entity.getY(), 0);
        Vec3d top = new Vec3d(position.x,
                Math.max(eye.y + 0.05, anchorY + (rows - 1) * ICON_HEIGHT * scale + 0.05), position.z);
        if (!clearRay(client, entity, eye, top)) return false;
        if (clearRay(client, entity, camera, eye)) return true;
        return clearRay(client, entity, camera, position.add(0, entity.getHeight() * 0.5, 0));
    }

    private static boolean clearRay(MinecraftClient client, LivingEntity entity, Vec3d from, Vec3d to) {
        return client.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, entity)).getType() == HitResult.Type.MISS;
    }

    private static void drawHeart(VertexConsumer vertices, MatrixStack matrices, HeartStyles.Style style,
                                  int sprite, float x, float y) {
        if (style.needsContainer(sprite)) drawIcon(vertices, matrices, style.icon(HeartDisplayState.EMPTY), x, y);
        drawIcon(vertices, matrices, style.icon(sprite), x, y);
    }

    private static void drawIcon(VertexConsumer vertices, MatrixStack matrices, HeartStyles.Icon icon, float x, float y) {
        var matrix = matrices.peek().getPositionMatrix();
        float u0 = icon.u0(), u1 = icon.u1(), v0 = icon.v0(), v1 = icon.v1();
        float heartWidth = ICON_HEIGHT * icon.width() / icon.height();
        vertices.vertex(matrix, x, y, 0).color(0xFFFFFFFF).texture(u0, v0).light(FULL_BRIGHT);
        HeartRenderBuffers.endVertex(vertices);
        vertices.vertex(matrix, x, y + ICON_HEIGHT, 0).color(0xFFFFFFFF).texture(u0, v1).light(FULL_BRIGHT);
        HeartRenderBuffers.endVertex(vertices);
        vertices.vertex(matrix, x + heartWidth, y + ICON_HEIGHT, 0).color(0xFFFFFFFF).texture(u1, v1).light(FULL_BRIGHT);
        HeartRenderBuffers.endVertex(vertices);
        vertices.vertex(matrix, x + heartWidth, y, 0).color(0xFFFFFFFF).texture(u1, v0).light(FULL_BRIGHT);
        HeartRenderBuffers.endVertex(vertices);
    }

    private static final class EntityState extends HeartTargets.State<LivingEntity> {
        long visibilityTick = -1;
        Text text = Text.empty();
        int textWidth, anchorWidth, visibilityScale;
        double visibilityHeight;
        Vec3d visibilityCamera, visibilityPosition;
        boolean visible;

        EntityState(LivingEntity entity) { super(entity); }
    }
}
