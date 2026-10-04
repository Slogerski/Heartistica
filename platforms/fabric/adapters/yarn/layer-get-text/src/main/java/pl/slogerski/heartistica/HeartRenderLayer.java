package pl.slogerski.heartistica;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
final class HeartRenderLayer {
    private HeartRenderLayer() {}
    static RenderLayer text(Identifier texture) { return RenderLayer.getText(texture); }
}
