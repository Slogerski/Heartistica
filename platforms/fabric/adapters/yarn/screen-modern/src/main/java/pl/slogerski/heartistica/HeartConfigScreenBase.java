package pl.slogerski.heartistica;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

abstract class HeartConfigScreenBase extends Screen {
    protected HeartConfigScreenBase(Text title) { super(title); }
    protected abstract boolean onHeartScroll(double x, double y, double horizontal, double vertical);
    protected abstract boolean onCatalogClick(double x, double y, int button);
    protected abstract boolean onCatalogDrag(double x);
    protected abstract boolean onCatalogRelease();
    @Override public boolean mouseClicked(double x, double y, int button) {
        return onCatalogClick(x, y, button) || super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return onCatalogDrag(x) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return onCatalogRelease() || super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return onHeartScroll(x, y, horizontal, vertical);
    }
    protected final void drawHeartBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE00B0E14);
    }
}
