package pl.slogerski.heartistica;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public final class HeartisticaConfigScreen extends Screen {
    private final Screen parent;
    private final HeartisticaConfig config = HeartisticaClient.config();
    private final CatalogView catalog = new CatalogView(HeartisticaClient.catalog(), config);
    private static final int CONTENT_HEIGHT = 328, GALLERY_TOP = 248;
    private final List<Placement> contentWidgets = new ArrayList<>();
    private Button resetButton, doneButton, eventButton, catalogButton;
    private int scroll, contentOrigin, viewportBottom;
    private boolean catalogMode;
    private String displayedLanguage;

    public HeartisticaConfigScreen(Screen parent) { super(HeartText.text("heartistica.title")); this.parent = parent; }

    @Override protected void init() {
        displayedLanguage = config.language;
        contentWidgets.clear();
        viewportBottom = Math.max(29, height - (catalogMode ? 36 : 60));
        catalog.release();
        int panelWidth = Math.min(310, width - 24), left = (width - panelWidth) / 2, half = (panelWidth - 6) / 2;
        if (catalogMode) {
            resetButton = addRenderableWidget(Button.builder(HeartText.text("heartistica.catalog.restore"), button -> {
                CatalogService.restore(config); rebuildWidgets();
            }).bounds(left, 30, half, 20).build());
            eventButton = addRenderableWidget(Button.builder(HeartText.events(), button -> {
                HeartisticaClient.catalog().toggleEvents(config); rebuildWidgets();
            }).bounds(left + half + 6, 30, half, 20).build());
            doneButton = addRenderableWidget(Button.builder(HeartText.text("heartistica.back"), button -> {
                catalogMode = false; rebuildWidgets();
            }).bounds(width / 2 - 75, height - 28, 150, 20).build());
            return;
        }
        content(toggle(left, 0, "heartistica.enabled", config.enabled, value -> config.enabled = value));
        eventButton = content(Button.builder(HeartText.events(), button -> {
            HeartisticaClient.catalog().toggleEvents(config); rebuildWidgets();
        }).bounds(left, 22, panelWidth, 20).build());
        content(new IntSlider(left, 44, panelWidth, "heartistica.range", config.range, 0, 128, true, value -> config.range = value));
        content(new IntSlider(left, 66, panelWidth, "heartistica.nearest", config.nearestPlayers, 0, 64, true, value -> config.nearestPlayers = value));
        content(new IntSlider(left, 88, panelWidth, "heartistica.scale", config.scalePercent, 50, 200, false, value -> config.scalePercent = value));
        content(new IntSlider(left, 110, panelWidth, "heartistica.height", (int) Math.round(config.heightOffsetPixels * 2), -16, 16, false,
                value -> config.heightOffsetPixels = value / 2.0));
        content(Button.builder(HeartText.text(config.numericDisplay ? "heartistica.display.numeric" : "heartistica.display.hearts"), button -> {
            config.numericDisplay = !config.numericDisplay; rebuildWidgets();
        }).bounds(left, 132, panelWidth, 20).build());
        content(toggle(left, 154, "heartistica.only_absorption", config.onlyAbsorption, value -> config.onlyAbsorption = value));
        content(toggle(left, 176, "heartistica.only_when_damaged", config.onlyWhenDamaged, value -> config.onlyWhenDamaged = value));
        Button save = content(Button.builder(HeartText.text("heartistica.save_server"), button -> {
            if (HeartisticaClient.profiles().saveForServer()) rebuildWidgets();
            else button.setMessage(HeartText.text("heartistica.save_failed"));
        }).bounds(left, 198, half, 20).build());
        Button restore = content(Button.builder(HeartText.text("heartistica.back_default"), button -> {
            if (HeartisticaClient.profiles().backToDefault()) rebuildWidgets();
            else button.setMessage(HeartText.text("heartistica.save_failed"));
        }).bounds(left + half + 6, 198, half, 20).build());
        save.active = HeartisticaClient.profiles().canSave();
        restore.active = HeartisticaClient.profiles().canRestore();
        content(Button.builder(HeartText.language(), button -> {
            HeartisticaClient.changeLanguage(); rebuildWidgets();
        }).bounds(left, 220, panelWidth, 20).build());
        catalogButton = addRenderableWidget(Button.builder(HeartText.text("heartistica.catalog.open"), button -> {
            catalogMode = true; rebuildWidgets();
        }).bounds(left, height - 52, panelWidth, 20).build());
        resetButton = addRenderableWidget(Button.builder(HeartText.text("heartistica.reset"), button -> {
            HeartisticaClient.resetConfig(); rebuildWidgets();
        }).bounds(left, height - 28, half, 20).build());
        doneButton = addRenderableWidget(Button.builder(HeartText.text("heartistica.done"), button -> onClose())
                .bounds(left + half + 6, height - 28, half, 20).build());
        positionContent();
    }

    private <T extends AbstractWidget> T content(T widget) {
        contentWidgets.add(new Placement(widget, widget.getY()));
        return addRenderableWidget(widget);
    }
    private void positionContent() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, CONTENT_HEIGHT - (viewportBottom - 28))));
        contentOrigin = 28 + Math.max(0, (viewportBottom - 28 - CONTENT_HEIGHT) / 2) - scroll;
        for (Placement placement : contentWidgets) {
            placement.widget.setY(contentOrigin + placement.y);
            placement.widget.visible = placement.widget.getY() >= 28 && placement.widget.getY() + 20 <= viewportBottom;
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (catalog.scrolling(x, y, horizontal, vertical)) return true;
        if (!catalogMode) { scroll -= (int) Math.round(vertical * 24); positionContent(); }
        return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        int button = event.button();
        String before = config.heartStyle;
        boolean events = HeartisticaClient.catalog().eventsEnabled(config);
        boolean handled = catalog.click(x, y, button, InputConstants.MOUSE_BUTTON_LEFT);
        if (handled && (!before.equals(config.heartStyle) || events != HeartisticaClient.catalog().eventsEnabled(config))) rebuildWidgets();
        return handled || super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return catalog.drag(event.x()) || super.mouseDragged(event, dx, dy); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return catalog.release() || super.mouseReleased(event); }
    private record Placement(AbstractWidget widget, int y) {}
    private Button toggle(int x, int y, String key, boolean initial, BoolSetter setter) {
        return Button.builder(HeartText.toggle(key, initial), button -> { setter.set(!initial); rebuildWidgets(); })
                .bounds(x, y, Math.min(310, width - 24), 20).build();
    }
    @Override public void onClose() { HeartisticaClient.profiles().saveCurrent(); if (minecraft != null) minecraft.setScreenAndShow(parent); }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!config.language.equals(displayedLanguage)) rebuildWidgets();
        eventButton.setMessage(HeartText.events());
        context.fill(0, 0, width, height, 0xE00B0E14);
        if (catalogMode) {
            int top = CatalogView.top(height);
            catalog.render(new HeartCatalogCanvas(context, minecraft), width, top + 2, top, viewportBottom, true);
            eventButton.extractRenderState(context, mouseX, mouseY, delta);
        } else {
            context.enableScissor(0, 28, width, viewportBottom);
            for (Placement placement : contentWidgets) placement.widget.extractRenderState(context, mouseX, mouseY, delta);
            catalog.render(new HeartCatalogCanvas(context, minecraft), width, contentOrigin + GALLERY_TOP, 28, viewportBottom, false);
            context.disableScissor();
            catalogButton.extractRenderState(context, mouseX, mouseY, delta);
        }
        resetButton.extractRenderState(context, mouseX, mouseY, delta);
        doneButton.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, HeartText.text(catalogMode ? "heartistica.catalog" : "heartistica.title"), width / 2, 12, 0xFFFFFFFF);
    }
    @FunctionalInterface private interface BoolSetter { void set(boolean value); }
    @FunctionalInterface private interface IntSetter { void set(int value); }
    private static final class IntSlider extends AbstractSliderButton {
        private final String key;
        private final int min, max;
        private final boolean zeroIsOff;
        private final IntSetter setter;
        IntSlider(int x, int y, int width, String key, int initial, int min, int max, boolean zeroIsOff, IntSetter setter) {
            super(x, y, width, 20, Component.empty(), (initial - min) / (double) (max - min));
            this.key = key; this.min = min; this.max = max; this.zeroIsOff = zeroIsOff; this.setter = setter; updateMessage();
        }
        private int current() { return min + (int) Math.round(value * (max - min)); }
        @Override protected void updateMessage() {
            int current = current();
            Object shown = zeroIsOff && current == 0 ? UiText.get(HeartisticaClient.config().language, "heartistica.off")
                    : "heartistica.height".equals(key) ? HeartisticaConfig.heightText(current / 2.0) : current;
            setMessage(HeartText.text(key, shown));
        }
        @Override protected void applyValue() { setter.set(current()); }
    }
}
