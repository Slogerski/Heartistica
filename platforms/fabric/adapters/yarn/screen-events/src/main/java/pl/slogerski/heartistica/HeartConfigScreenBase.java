package pl.slogerski.heartistica;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

abstract class HeartConfigScreenBase extends Screen {
    protected HeartConfigScreenBase(Text title) { super(title); }
    protected abstract boolean onHeartScroll(double x, double y, double horizontal, double vertical);
    protected abstract boolean onCatalogClick(double x, double y, int button);
    protected abstract boolean onCatalogDrag(double x);
    protected abstract boolean onCatalogRelease();
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return onHeartScroll(x, y, horizontal, vertical);
    }
    @Override public boolean mouseClicked(Click click, boolean doubleClick) {
        return onCatalogClick(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubleClick);
    }
    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        return onCatalogDrag(click.x()) || super.mouseDragged(click, dx, dy);
    }
    @Override public boolean mouseReleased(Click click) {
        return onCatalogRelease() || super.mouseReleased(click);
    }
    protected final void drawHeartBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE00B0E14);
    }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE00B0E14);
    }
}
