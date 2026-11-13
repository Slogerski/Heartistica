package pl.slogerski.heartistica;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Matrix4f;

import static pl.slogerski.heartistica.HeartDisplayState.ICON_HEIGHT;

final class EntityHeartRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int HEART_COLOR = 0xFFFF4057;
    private static final int ABSORPTION_COLOR = 0xFFFFC83D;
    private static final HeartTargets<LivingEntity, EntityState> TARGETS = new HeartTargets<>(EntityState::new);
    private static ClientLevel level;

    private EntityHeartRenderer() {}
    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EntityHeartRenderer::update);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var entry = client.getCurrentServer();
            HeartisticaClient.profiles().useServer(entry == null ? null : entry.ip);
            clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HeartisticaClient.profiles().useServer(null);
            clear();
        });
        OfficialWorldRender.register(EntityHeartRenderer::render);
    }
    private static void clear() { TARGETS.clear(); level = null; }

    private static void update(Minecraft minecraft) {
        HeartisticaConfig config = HeartisticaClient.config();
        if (minecraft.level != level) clear();
        level = minecraft.level;
        if (level == null || minecraft.player == null || !config.enabled) { clear(); return; }
        TARGETS.beginTick();
        long now = Util.getMillis();
        double rangeSquared = config.rangeSquared();
        for (var player : level.players()) updateEntity(minecraft, player, config, rangeSquared, now);
        TARGETS.finishPlayers();
        if (mobsEnabled(minecraft, config)) {
            Iterable<? extends Entity> nearby = config.range == 0 ? level.entitiesForRendering()
                    : level.getEntitiesOfClass(Mob.class, minecraft.player.getBoundingBox().inflate(config.range));
            for (Entity entity : nearby) {
                if (entity instanceof Mob mob) updateEntity(minecraft, mob, config, rangeSquared, now);
            }
        }
        TARGETS.finishTick();
    }

    private static void updateEntity(Minecraft minecraft, LivingEntity entity, HeartisticaConfig config,
                                     double rangeSquared, long now) {
        if (!baseEligible(minecraft.player, entity)) return;
        double distance = minecraft.player.distanceToSqr(entity);
        if (distance > rangeSquared) return;
        EntityState state = TARGETS.update(entity, entity instanceof Mob, distance,
                entity.getHealth(), entity.getMaxHealth(), entity.getAbsorptionAmount(), config, now);
        if (state == null) return;
        state.text = Component.literal(state.display.label);
        state.textWidth = minecraft.font.width(state.text);
        state.anchorWidth = minecraft.font.width(state.display.anchorLabel);
        state.visibilityTick = -1;
    }

    private static boolean mobsEnabled(Minecraft minecraft, HeartisticaConfig config) {
        var server = minecraft.getSingleplayerServer();
        return config.showMobs(server != null && !server.isPublished());
    }

    private static boolean baseEligible(LivingEntity viewer, LivingEntity entity) {
        return entity != viewer && !entity.isRemoved() && entity.isAlive() && !entity.isSpectator()
                && (!entity.isInvisible() || hasVisibleArmor(entity));
    }
    private static boolean hasVisibleArmor(LivingEntity entity) {
        return !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || !entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || !entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static void render(OfficialWorldRender.Context context) {
        Minecraft minecraft = Minecraft.getInstance();
        HeartisticaConfig config = HeartisticaClient.config();
        if (minecraft.player == null || minecraft.level != level || !config.enabled || TARGETS.selected().isEmpty()) return;
        long tick = TARGETS.tick();
        boolean showMobs = mobsEnabled(minecraft, config);
        Camera camera = context.camera();
        Vec3 cameraPos = camera.position();
        float partialTick = context.partialTick();
        float scale = 0.025F * config.scalePercent / 100;
        HeartStyles.Style style = HeartStyles.selected();
        style.resolve(minecraft);
        var icon = style.icon(HeartDisplayState.FULL);
        float heartWidth = ICON_HEIGHT * icon.width() / icon.height();
        float advance = HeartDisplayState.iconAdvance(heartWidth, style.sourceWidth());
        PoseStack pose = context.pose();
        Matrix4fc cameraRotation = new Matrix4f().rotation(camera.rotation());
        for (EntityState state : TARGETS.selected()) {
            LivingEntity entity = state.entity;
            if ((!showMobs && entity instanceof Mob) || !baseEligible(minecraft.player, entity)
                    || !config.shouldDisplayHealth(entity.getHealth(), entity.getMaxHealth(), entity.getAbsorptionAmount())) continue;
            HeartDisplayState display = state.display;
            int rows = display.rows();
            if (rows == 0) continue;
            boolean numeric = !display.label.isEmpty();
            Vec3 position = entity.getPosition(partialTick);
            double anchorY = position.y + entity.getBbHeight() + config.heightAboveHead();
            float startX = display.startX(heartWidth, advance, state.anchorWidth);
            if (!inView(camera, cameraPos, position, anchorY)) continue;
            if (state.visibilityTick != tick || state.visibilityCamera == null
                    || state.visibilityCamera.distanceToSqr(cameraPos) > 1
                    || state.visibilityPosition.distanceToSqr(position) > 1
                    || state.visibilityHeight != config.heightOffsetPixels || state.visibilityScale != config.scalePercent) {
                state.visible = hasClearView(minecraft, entity, cameraPos, position, anchorY, rows, scale);
                state.visibilityTick = tick; state.visibilityCamera = cameraPos; state.visibilityPosition = position;
                state.visibilityHeight = config.heightOffsetPixels; state.visibilityScale = config.scalePercent;
            }
            if (!state.visible) continue;
            pose.pushPose();
            try {
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
                                    -(slot / HeartDisplayState.HEARTS_PER_ROW) * ICON_HEIGHT);
                        }
                    });
                }
            } finally {
                pose.popPose();
            }
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
    private static boolean hasClearView(Minecraft minecraft, LivingEntity entity, Vec3 camera,
                                        Vec3 position, double anchorY, int rows, float scale) {
        Vec3 eye = position.add(0, entity.getEyeY() - entity.getY(), 0);
        Vec3 top = new Vec3(position.x, Math.max(eye.y + 0.05,
                anchorY + (rows - 1) * ICON_HEIGHT * scale + 0.05), position.z);
        if (!clearRay(minecraft, entity, eye, top)) return false;
        return clearRay(minecraft, entity, camera, eye)
                || clearRay(minecraft, entity, camera, position.add(0, entity.getBbHeight() * 0.5, 0));
    }
    private static boolean clearRay(Minecraft minecraft, LivingEntity entity, Vec3 from, Vec3 to) {
        return minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
    }
    private static void drawHeart(VertexConsumer vertices, Matrix4fc matrix, HeartStyles.Style style,
                                  int sprite, float x, float y) {
        if (style.needsContainer(sprite)) drawIcon(vertices, matrix, style.icon(HeartDisplayState.EMPTY), x, y);
        drawIcon(vertices, matrix, style.icon(sprite), x, y);
    }
    private static void drawIcon(VertexConsumer vertices, Matrix4fc matrix, HeartStyles.Icon icon, float x, float y) {
        float width = ICON_HEIGHT * icon.width() / icon.height();
        vertices.addVertex(matrix, x, y, 0).setColor(0xFFFFFFFF).setUv(icon.u0(), icon.v0()).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, x, y + ICON_HEIGHT, 0).setColor(0xFFFFFFFF).setUv(icon.u0(), icon.v1()).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, x + width, y + ICON_HEIGHT, 0).setColor(0xFFFFFFFF).setUv(icon.u1(), icon.v1()).setLight(FULL_BRIGHT);
        vertices.addVertex(matrix, x + width, y, 0).setColor(0xFFFFFFFF).setUv(icon.u1(), icon.v0()).setLight(FULL_BRIGHT);
    }

    private static final class EntityState extends HeartTargets.State<LivingEntity> {
        long visibilityTick = -1;
        Component text = Component.empty(); int textWidth, anchorWidth, visibilityScale;
        double visibilityHeight;
        Vec3 visibilityCamera, visibilityPosition; boolean visible;
        EntityState(LivingEntity entity) { super(entity); }
    }
}
