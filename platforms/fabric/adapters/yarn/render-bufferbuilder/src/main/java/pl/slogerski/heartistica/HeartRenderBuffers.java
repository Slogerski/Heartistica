package pl.slogerski.heartistica;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;

import java.util.LinkedHashMap;

final class HeartRenderBuffers implements AutoCloseable {
    static void endVertex(net.minecraft.client.render.VertexConsumer vertices) {
        vertices.next();
    }

    private final BufferBuilder hearts = new BufferBuilder(16_384);
    private final BufferBuilder text = new BufferBuilder(8_192);
    final VertexConsumerProvider.Immediate provider;
    final RenderLayer layer;

    HeartRenderBuffers(RenderLayer layer) {
        this.layer = layer;
        var layers = new LinkedHashMap<RenderLayer, BufferBuilder>();
        layers.put(layer, hearts);
        provider = VertexConsumerProvider.immediate(layers, text);
    }

    @Override public void close() {
        hearts.clear();
        text.clear();
    }
}
