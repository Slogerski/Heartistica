package pl.slogerski.heartistica;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

final class HeartisticaConfigScreen extends Screen {
    private final Screen parent;
    private final HeartisticaConfig config = HeartisticaClient.config();
    private static final int GALLERY_TOP = 180, CONTENT_HEIGHT = 354, VIEWPORT_TOP = 28;
    private final List<Placement> content = new ArrayList<>();
    private int scroll, origin, viewportBottom;
    private Button resetButton, doneButton;
    private record Placement(AbstractWidget widget, int y) {}

    HeartisticaConfigScreen(Screen parent) { super(Component.translatable("heartistica.title")); this.parent = parent; }

    @Override protected void init() {
        super.init();
        content.clear();
        viewportBottom = Math.max(VIEWPORT_TOP + 1, height - 36);
        initSettings();
        positionContent();
    }

    private void initSettings() {
        int w = Math.min(310, width - 24), x = (width - w) / 2, y = 0;
        add(x, y, w, toggle("heartistica.enabled", config.enabled), b -> { config.enabled = !config.enabled; rebuild(); });
        content(new IntSlider(x, y + 22, w, "heartistica.range", config.range, 0, 128, true, v -> config.range = v));
        content(new IntSlider(x, y + 44, w, "heartistica.nearest", config.nearestPlayers, 0, 64, true, v -> config.nearestPlayers = v));
        content(new IntSlider(x, y + 66, w, "heartistica.scale", config.scalePercent, 50, 200, false, v -> config.scalePercent = v));
        content(new IntSlider(x, y + 88, w, "heartistica.height", config.heightOffsetPixels, -8, 4, false, v -> config.heightOffsetPixels = v));
        add(x, y + 110, w, Component.translatable(config.numericDisplay
                ? "heartistica.display.numeric" : "heartistica.display.hearts"), b -> { config.numericDisplay = !config.numericDisplay; rebuild(); });
        add(x, y + 132, w, toggle("heartistica.only_absorption", config.onlyAbsorption), b -> {
            config.onlyAbsorption = !config.onlyAbsorption;
            rebuild();
        });
        initGallery();
        int half = (w - 6) / 2;
        resetButton = addRenderableWidget(Button.builder(Component.translatable("heartistica.reset"), b -> { config.reset(); rebuild(); })
                .pos(x, height - 28).size(half, 20).build());
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .pos(x + half + 6, height - 28).size(half, 20).build());
    }

    private void initGallery() {
        int selected = HeartStyles.all().indexOf(HeartStyles.selected());
        Button previous = add(width / 2 - 64, GALLERY_TOP + 136, 24, Component.literal("←"), b -> select(-1));
        Button next = add(width / 2 + 40, GALLERY_TOP + 136, 24, Component.literal("→"), b -> select(1));
        previous.active = selected > 0;
        next.active = selected < HeartStyles.all().size() - 1;
    }

    private Button add(int x, int y, int w, Component message, Button.OnPress press) {
        return content(Button.builder(message, press).pos(x, y).size(w, 20).build());
    }
    private <T extends AbstractWidget> T content(T widget) {
        content.add(new Placement(widget, widget.getY()));
        return addRenderableWidget(widget);
    }
    private int maxScroll() { return Math.max(0, CONTENT_HEIGHT - (viewportBottom - VIEWPORT_TOP)); }
    private void positionContent() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        origin = VIEWPORT_TOP + Math.max(0, (viewportBottom - VIEWPORT_TOP - CONTENT_HEIGHT) / 2) - scroll;
        for (Placement placement : content) {
            placement.widget.setY(origin + placement.y);
            placement.widget.visible = placement.widget.getY() + placement.widget.getHeight() > VIEWPORT_TOP
                    && placement.widget.getY() < viewportBottom;
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll -= (int)Math.round(vertical * 24);
        positionContent();
        return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.y() < VIEWPORT_TOP) return false;
        if (event.y() >= viewportBottom) {
            for (Button button : new Button[]{resetButton, doneButton}) {
                if (button.mouseClicked(event, doubleClick)) { setFocused(button); return true; }
            }
            return false;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        boolean handled = super.keyPressed(event);
        for (Placement placement : content) {
            if (placement.widget == getFocused()) {
                int y = placement.widget.getY();
                if (y < VIEWPORT_TOP) scroll -= VIEWPORT_TOP - y;
                else if (y + 20 > viewportBottom) scroll += y + 20 - viewportBottom;
                positionContent();
                break;
            }
        }
        return handled;
    }
    private void rebuild() { rebuildWidgets(); }
    private void select(int direction) {
        var styles = HeartStyles.all();
        int index = Math.max(0, Math.min(styles.size() - 1, styles.indexOf(HeartStyles.selected()) + direction));
        config.heartStyle = styles.get(index).id; rebuild();
    }
    private static Component toggle(String key, boolean enabled) {
        return Component.translatable(key).copy().append(": ").append(CommonComponents.optionStatus(enabled));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xE00B0E14);
        graphics.enableScissor(0, VIEWPORT_TOP, width, viewportBottom);
        int contentMouseY = mouseY >= VIEWPORT_TOP && mouseY < viewportBottom ? mouseY : Integer.MIN_VALUE;
        for (Placement placement : content) placement.widget.extractRenderState(graphics, mouseX, contentMouseY, delta);
        int x = width / 2 - 64, y = origin + GALLERY_TOP;
        graphics.centeredText(font, Component.translatable("heartistica.gallery"), width / 2, y - 16, 0xFFFFFFFF);
        graphics.fill(x, y, x + 128, y + 128, 0xFF777777);
        graphics.fill(x + 1, y + 1, x + 127, y + 127, 0xFF272727);
        HeartStyles.Style style = HeartStyles.selected();
        style.resolve(minecraft);
        int spriteIndex = config.onlyAbsorption ? HeartDisplayState.GOLD : HeartDisplayState.FULL;
        HeartStyles.Icon icon = style.icon(spriteIndex);
        double fit = 104.0 / Math.max(icon.width(), icon.height());
        int iw = Math.max(1, (int)Math.round(icon.width() * fit)), ih = Math.max(1, (int)Math.round(icon.height() * fit));
        int ix = x + (128 - iw) / 2, iy = y + (128 - ih) / 2;
        if (style.needsContainer(spriteIndex)) drawIcon(graphics, style.icon(HeartDisplayState.EMPTY), ix, iy, iw, ih);
        drawIcon(graphics, icon, ix, iy, iw, ih);
        graphics.centeredText(font, style.name(), width / 2, y + 162, 0xFFFFFFFF);
        graphics.disableScissor();
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        resetButton.extractRenderState(graphics, mouseX, mouseY, delta);
        doneButton.extractRenderState(graphics, mouseX, mouseY, delta);
        if (maxScroll() > 0) {
            int barX = (width + Math.min(310, width - 24)) / 2 + 4;
            int track = viewportBottom - VIEWPORT_TOP;
            int thumb = Math.max(12, track * track / CONTENT_HEIGHT);
            int barY = VIEWPORT_TOP + scroll * (track - thumb) / maxScroll();
            graphics.fill(barX, VIEWPORT_TOP, barX + 2, viewportBottom, 0xFF333333);
            graphics.fill(barX, barY, barX + 2, barY + thumb, 0xFFAAAAAA);
        }
    }

    private static void drawIcon(GuiGraphicsExtractor graphics, HeartStyles.Icon icon, int x, int y, int w, int h) {
        if (icon.sprite() != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite(), x, y, w, h);
        else graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), x, y,
                icon.u0() * icon.atlasWidth(), icon.v0() * icon.atlasHeight(), w, h,
                icon.width(), icon.height(), icon.atlasWidth(), icon.atlasHeight());
    }

    @Override public void onClose() { config.save(); minecraft.setScreenAndShow(parent); }

    @FunctionalInterface private interface IntSetter { void set(int value); }
    private static final class IntSlider extends AbstractSliderButton {
        private final String key; private final int min, max; private final boolean zeroOff; private final IntSetter setter;
        IntSlider(int x, int y, int width, String key, int current, int min, int max, boolean zeroOff, IntSetter setter) {
            super(x, y, width, 20, Component.empty(), (current - min) / (double)(max - min));
            this.key = key; this.min = min; this.max = max; this.zeroOff = zeroOff; this.setter = setter; updateMessage();
        }
        private int current() { return min + (int)Math.round(value * (max - min)); }
        @Override protected void updateMessage() {
            int value = current();
            setMessage(Component.translatable(key, zeroOff && value == 0 ? CommonComponents.OPTION_OFF : value));
        }
        @Override protected void applyValue() { setter.set(current()); }
    }
}
