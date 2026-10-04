package pl.slogerski.heartistica;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public final class HeartisticaConfigScreen extends HeartConfigScreenBase {
    private final Screen parent;
    private final HeartisticaConfig config = HeartisticaClient.config();
    private static final int CONTENT_HEIGHT = 354;
    private static final int GALLERY_TOP = 180;
    private final List<Placement> contentWidgets = new ArrayList<>();
    private ButtonWidget resetButton, doneButton;
    private int scroll, contentOrigin, viewportBottom;

    public HeartisticaConfigScreen(Screen parent) {
        super(Text.translatable("heartistica.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        contentWidgets.clear();
        int panelWidth = Math.min(310, width - 24);
        int left = (width - panelWidth) / 2;
        int top = 0;
        viewportBottom = height - 36;

        content(toggle(left, top, "heartistica.enabled", config.enabled,
                value -> config.enabled = value));
        content(new IntSlider(left, top + 22, panelWidth, "heartistica.range",
                config.range, 0, 128, true, value -> config.range = value));
        content(new IntSlider(left, top + 44, panelWidth, "heartistica.nearest",
                config.nearestPlayers, 0, 64, true, value -> config.nearestPlayers = value));
        content(new IntSlider(left, top + 66, panelWidth, "heartistica.scale",
                config.scalePercent, 50, 200, false, value -> config.scalePercent = value));
        content(new IntSlider(left, top + 88, panelWidth, "heartistica.height",
                config.heightOffsetPixels, -8, 4, false, value -> config.heightOffsetPixels = value));
        content(ButtonWidget.builder(displayModeText(), button -> {
            config.numericDisplay = !config.numericDisplay;
            button.setMessage(displayModeText());
        }).dimensions(left, top + 110, panelWidth, 20).build());
        content(toggle(left, top + 132, "heartistica.only_absorption",
                config.onlyAbsorption, value -> {
                    config.onlyAbsorption = value;
                    clearAndInit();
                }));
        int selected = HeartStyles.all().indexOf(HeartStyles.selected());
        ButtonWidget previous = content(ButtonWidget.builder(Text.literal("←"), button -> selectStyle(-1))
                .dimensions(width / 2 - 64, GALLERY_TOP + 136, 24, 20).build());
        ButtonWidget next = content(ButtonWidget.builder(Text.literal("→"), button -> selectStyle(1))
                .dimensions(width / 2 + 40, GALLERY_TOP + 136, 24, 20).build());
        previous.active = selected > 0;
        next.active = selected < HeartStyles.all().size() - 1;
        int halfWidth = (panelWidth - 6) / 2;
        resetButton = addDrawableChild(ButtonWidget.builder(Text.translatable("heartistica.reset"), button -> {
            config.reset();
            clearAndInit();
        }).dimensions(left, height - 28, halfWidth, 20).build());
        doneButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(left + halfWidth + 6, height - 28, halfWidth, 20).build());
        positionContent();
    }

    private <T extends ClickableWidget> T content(T widget) {
        contentWidgets.add(new Placement(widget, widget.getY()));
        return addDrawableChild(widget);
    }

    private void selectStyle(int direction) {
        var styles = HeartStyles.all();
        int index = clamp(styles.indexOf(HeartStyles.selected()) + direction, 0, styles.size() - 1);
        config.heartStyle = styles.get(index).id;
        clearAndInit();
    }

    private int maxScroll() { return Math.max(0, CONTENT_HEIGHT - Math.max(1, viewportBottom - 28)); }

    private void positionContent() {
        scroll = clamp(scroll, 0, maxScroll());
        contentOrigin = 28 + Math.max(0, (viewportBottom - 28 - CONTENT_HEIGHT) / 2) - scroll;
        for (Placement placement : contentWidgets) {
            placement.widget.setY(contentOrigin + placement.y);
            placement.widget.visible = placement.widget.getY() + placement.widget.getHeight() > 28
                    && placement.widget.getY() < viewportBottom;
        }
    }

    @Override
    protected boolean onHeartScroll(double vertical) {
        scroll -= (int) Math.round(vertical * 24);
        positionContent();
        return true;
    }

    private record Placement(ClickableWidget widget, int y) { }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ButtonWidget toggle(int x, int y, String key, boolean initial, BoolSetter setter) {
        return ButtonWidget.builder(toggleText(key, initial), button -> {
            boolean next = !button.getMessage().getString().endsWith(ScreenTexts.ON.getString());
            setter.set(next);
            button.setMessage(toggleText(key, next));
        }).dimensions(x, y, Math.min(310, width - 24), 20).build();
    }

    private static Text toggleText(String key, boolean enabled) {
        return Text.translatable(key).append(": ").append(enabled ? ScreenTexts.ON : ScreenTexts.OFF);
    }

    private Text displayModeText() {
        return Text.translatable(config.numericDisplay
                ? "heartistica.display.numeric"
                : "heartistica.display.hearts");
    }

    @Override
    public void close() {
        config.save();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawHeartBackground(context, mouseX, mouseY, delta);
        context.enableScissor(0, 28, width, viewportBottom);
        for (Placement placement : contentWidgets) {
            placement.widget.render(context, mouseY >= 28 && mouseY < viewportBottom ? mouseX : -1,
                    mouseY >= 28 && mouseY < viewportBottom ? mouseY : -1, delta);
        }
        int x = width / 2 - 64, y = contentOrigin + GALLERY_TOP;
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("heartistica.gallery"), width / 2, y - 16, 0xFFDDDDDD);
        context.fill(x, y, x + 128, y + 128, 0xFF777777);
        context.fill(x + 1, y + 1, x + 127, y + 127, 0xFF272727);
        HeartStyles.Style style = HeartStyles.selected();
        style.resolve(client);
        int sprite = config.onlyAbsorption ? HeartDisplayState.GOLD : HeartDisplayState.FULL;
        HeartStyles.Icon icon = style.icon(sprite);
        double fit = 104.0 / Math.max(icon.width(), icon.height());
        int iconWidth = Math.max(1, (int) Math.round(icon.width() * fit));
        int iconHeight = Math.max(1, (int) Math.round(icon.height() * fit));
        int iconX = x + (128 - iconWidth) / 2, iconY = y + (128 - iconHeight) / 2;
        if (style.needsContainer(sprite)) HeartPreviewRenderer.draw(context,
                style.icon(HeartDisplayState.EMPTY), iconX, iconY, iconWidth, iconHeight);
        HeartPreviewRenderer.draw(context, icon, iconX, iconY, iconWidth, iconHeight);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal((HeartStyles.all().indexOf(style) + 1)
                + " / " + HeartStyles.all().size()), width / 2, y + 142, 0xFFDDDDDD);
        context.drawCenteredTextWithShadow(textRenderer, style.name(), width / 2, y + 164, 0xFFFFFFFF);
        context.disableScissor();
        resetButton.render(context, mouseX, mouseY, delta);
        doneButton.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        if (maxScroll() > 0) {
            int track = viewportBottom - 28;
            int thumb = Math.max(16, track * track / CONTENT_HEIGHT);
            int thumbY = 28 + (track - thumb) * scroll / maxScroll();
            context.fill(width - 7, 28, width - 4, viewportBottom, 0xFF303030);
            context.fill(width - 7, thumbY, width - 4, thumbY + thumb, 0xFF999999);
        }
    }

    @FunctionalInterface
    private interface BoolSetter { void set(boolean value); }

    @FunctionalInterface
    private interface IntSetter { void set(int value); }

    private static final class IntSlider extends SliderWidget {
        private final String key;
        private final int min;
        private final int max;
        private final boolean zeroIsOff;
        private final IntSetter setter;

        private IntSlider(int x, int y, int width, String key, int initial,
                          int min, int max, boolean zeroIsOff, IntSetter setter) {
            super(x, y, width, 20, Text.empty(), (initial - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.zeroIsOff = zeroIsOff;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable(key,
                    zeroIsOff && current() == 0 ? ScreenTexts.OFF : signedValue(current())));
        }

        @Override
        protected void applyValue() {
            setter.set(current());
        }

        private Object signedValue(int current) {
            if (!"heartistica.height".equals(key) || current <= 0) return current;
            return "+" + current;
        }
    }
}
