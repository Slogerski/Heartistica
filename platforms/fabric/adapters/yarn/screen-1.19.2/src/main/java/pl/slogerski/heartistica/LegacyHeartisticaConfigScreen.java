package pl.slogerski.heartistica;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

final class HeartisticaConfigScreen extends Screen {
    private final Screen parent;
    private final HeartisticaConfig config = HeartisticaClient.config();
    private boolean gallery;

    HeartisticaConfigScreen(Screen parent) {
        super(Text.translatable("heartistica.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        super.init();
        if (gallery) initGallery(); else initSettings();
    }

    private void initSettings() {
        int w = Math.min(310, width - 24), x = (width - w) / 2, y = 28;
        addButton(x, y, w, toggleText("heartistica.enabled", config.enabled), b -> {
            config.enabled = !config.enabled; rebuild();
        });
        addDrawableChild(new IntSlider(x, y + 22, w, "heartistica.range", config.range, 0, 128, true,
                value -> config.range = value));
        addDrawableChild(new IntSlider(x, y + 44, w, "heartistica.nearest", config.nearestPlayers, 0, 64, true,
                value -> config.nearestPlayers = value));
        addDrawableChild(new IntSlider(x, y + 66, w, "heartistica.scale", config.scalePercent, 50, 200, false,
                value -> config.scalePercent = value));
        addDrawableChild(new IntSlider(x, y + 88, w, "heartistica.height", config.heightOffsetPixels, -8, 4, false,
                value -> config.heightOffsetPixels = value));
        addButton(x, y + 110, w, Text.translatable(config.numericDisplay
                ? "heartistica.display.numeric" : "heartistica.display.hearts"), b -> {
            config.numericDisplay = !config.numericDisplay; rebuild();
        });
        addButton(x, y + 132, w, toggleText("heartistica.only_damaged", config.onlyDamagedWithoutAbsorption), b -> {
            config.onlyDamagedWithoutAbsorption = !config.onlyDamagedWithoutAbsorption;
            if (config.onlyDamagedWithoutAbsorption) config.onlyAbsorption = false;
            rebuild();
        });
        addButton(x, y + 154, w, toggleText("heartistica.only_absorption", config.onlyAbsorption), b -> {
            config.onlyAbsorption = !config.onlyAbsorption;
            if (config.onlyAbsorption) config.onlyDamagedWithoutAbsorption = false;
            rebuild();
        });
        addButton(x, y + 176, w, Text.translatable("heartistica.gallery"), b -> { gallery = true; rebuild(); });
        int half = (w - 6) / 2;
        addButton(x, height - 28, half, Text.translatable("heartistica.reset"), b -> { config.reset(); rebuild(); });
        addButton(x + half + 6, height - 28, half, ScreenTexts.DONE, b -> close());
    }

    private void initGallery() {
        int selected = HeartStyles.all().indexOf(HeartStyles.selected());
        ButtonWidget previous = addButton(width / 2 - 64, Math.min(height - 52, 184), 24, Text.literal("←"), b -> select(-1));
        ButtonWidget next = addButton(width / 2 + 40, Math.min(height - 52, 184), 24, Text.literal("→"), b -> select(1));
        previous.active = selected > 0;
        next.active = selected < HeartStyles.all().size() - 1;
        addButton(width / 2 - 64, height - 28, 128, ScreenTexts.BACK, b -> { gallery = false; rebuild(); });
    }

    private ButtonWidget addButton(int x, int y, int w, Text text, ButtonWidget.PressAction action) {
        return addDrawableChild(new ButtonWidget(x, y, w, 20, text, action));
    }

    private void select(int direction) {
        var styles = HeartStyles.all();
        int index = Math.max(0, Math.min(styles.size() - 1, styles.indexOf(HeartStyles.selected()) + direction));
        config.heartStyle = styles.get(index).id;
        rebuild();
    }

    private void rebuild() { clearAndInit(); }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        drawCenteredText(matrices, textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        if (!gallery) return;
        int x = width / 2 - 64, y = Math.max(32, Math.min(48, height - 204));
        DrawableHelper.fill(matrices, x, y, x + 128, y + 128, 0xFF777777);
        DrawableHelper.fill(matrices, x + 1, y + 1, x + 127, y + 127, 0xFF272727);
        HeartStyles.Style style = HeartStyles.selected();
        style.resolve(client);
        int sprite = config.onlyAbsorption ? HeartDisplayState.GOLD : HeartDisplayState.FULL;
        HeartStyles.Icon icon = style.icon(sprite);
        double fit = 104.0 / Math.max(icon.width(), icon.height());
        int iw = Math.max(1, (int) Math.round(icon.width() * fit));
        int ih = Math.max(1, (int) Math.round(icon.height() * fit));
        int ix = x + (128 - iw) / 2, iy = y + (128 - ih) / 2;
        if (style.needsContainer(sprite)) drawIcon(matrices, style.icon(HeartDisplayState.EMPTY), ix, iy, iw, ih);
        drawIcon(matrices, icon, ix, iy, iw, ih);
        drawCenteredText(matrices, textRenderer, style.name(), width / 2, y + 134, 0xFFFFFFFF);
    }

    private static void drawIcon(MatrixStack matrices, HeartStyles.Icon icon, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, icon.texture());
        DrawableHelper.drawTexture(matrices, x, y, w, h, icon.u0() * icon.atlasWidth(),
                icon.v0() * icon.atlasHeight(), icon.width(), icon.height(), icon.atlasWidth(), icon.atlasHeight());
    }

    private static Text toggleText(String key, boolean enabled) {
        return Text.translatable(key).append(": ").append(enabled ? ScreenTexts.ON : ScreenTexts.OFF);
    }

    @Override public void close() {
        config.save();
        if (client != null) client.setScreen(parent);
    }

    @FunctionalInterface private interface IntSetter { void set(int value); }
    private static final class IntSlider extends SliderWidget {
        private final String key;
        private final int min, max;
        private final boolean zeroIsOff;
        private final IntSetter setter;
        IntSlider(int x, int y, int width, String key, int current, int min, int max,
                  boolean zeroIsOff, IntSetter setter) {
            super(x, y, width, 20, Text.literal(""), (current - min) / (double) (max - min));
            this.key = key; this.min = min; this.max = max; this.zeroIsOff = zeroIsOff; this.setter = setter;
            updateMessage();
        }
        private int current() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void updateMessage() {
            int current = current();
            Object shown = zeroIsOff && current == 0 ? ScreenTexts.OFF : current;
            setMessage(Text.translatable(key, shown));
        }
        @Override protected void applyValue() { setter.set(current()); }
    }
}
