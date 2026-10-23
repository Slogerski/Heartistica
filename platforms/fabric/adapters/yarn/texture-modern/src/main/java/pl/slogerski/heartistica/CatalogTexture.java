package pl.slogerski.heartistica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

final class CatalogTexture {
    static void install(MinecraftClient client, Identifier id, byte[] png) throws java.io.IOException {
        NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
        NativeImageBackedTexture texture = null;
        try {
            texture = new NativeImageBackedTexture(() -> "Heartistica Catalog", image);
            client.getTextureManager().registerTexture(id, texture);
        } catch (RuntimeException | LinkageError error) {
            try { if (texture == null) image.close(); else texture.close(); }
            catch (RuntimeException | LinkageError cleanupError) { error.addSuppressed(cleanupError); }
            throw error;
        }
    }
}
