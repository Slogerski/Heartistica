package pl.slogerski.heartistica;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;

import java.util.LinkedHashMap;

final class HeartRenderBuffers implements AutoCloseable {
    static void endVertex(net.minecraft.client.render.VertexConsumer vertices) {
    }

    private final BufferAllocator hearts = new BufferAllocator(16_384);
    private final BufferAllocator text = new BufferAllocator(8_192);
    final VertexConsumerProvider.Immediate provider;
    final RenderLayer layer;

    HeartRenderBuffers(RenderLayer layer) {
        this.layer = layer;
        var layers = new LinkedHashMap<RenderLayer, BufferAllocator>();
        layers.put(layer, hearts);
        provider = VertexConsumerProvider.immediate(layers, text);
    }

    @Override public void close() {
        hearts.close();
        text.close();
    }
}
