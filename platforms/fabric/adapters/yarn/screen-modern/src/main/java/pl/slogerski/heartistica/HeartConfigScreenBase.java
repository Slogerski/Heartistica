package pl.slogerski.heartistica;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

abstract class HeartConfigScreenBase extends Screen {
    protected HeartConfigScreenBase(Text title) { super(title); }
    protected abstract boolean onHeartScroll(double vertical);
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return onHeartScroll(vertical);
    }
    protected final void drawHeartBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE00B0E14);
    }
}
