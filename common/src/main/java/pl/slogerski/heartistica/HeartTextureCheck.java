package pl.slogerski.heartistica;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class HeartTextureCheck {
    static boolean differs(InputStream original, InputStream selected, boolean legacyAtlas) throws IOException {
        BufferedImage base = read(original), current = read(selected);
        if (legacyAtlas) {
            for (int y : new int[]{0, 45}) for (int x : new int[]{52, 61, 16, 160, 169}) {
                if (regionDiffers(base, current, x, y, 9, 9, 256, 256)) return true;
            }
            return false;
        }
        if ((long) base.getWidth() * current.getHeight() != (long) current.getWidth() * base.getHeight()) return true;
        int width = Math.min(256, Math.max(base.getWidth(), current.getWidth()));
        int height = Math.min(256, Math.max(base.getHeight(), current.getHeight()));
        return regionDiffers(base, current, 0, 0, width, height, width, height);
    }

    private static boolean regionDiffers(BufferedImage base, BufferedImage current, int x, int y, int width, int height, int atlasWidth, int atlasHeight) {
        for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++) {
            double u = (x + dx + 0.5) / atlasWidth, v = (y + dy + 0.5) / atlasHeight;
            int first = base.getRGB(Math.min(base.getWidth() - 1, (int) (u * base.getWidth())), Math.min(base.getHeight() - 1, (int) (v * base.getHeight())));
            int second = current.getRGB(Math.min(current.getWidth() - 1, (int) (u * current.getWidth())), Math.min(current.getHeight() - 1, (int) (v * current.getHeight())));
            if (first >>> 24 == 0 && second >>> 24 == 0) continue;
            if (first != second) return true;
        }
        return false;
    }

    private static BufferedImage read(InputStream stream) throws IOException {
        try (var input = new MemoryCacheImageInputStream(stream)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported heart texture");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096) throw new IOException("Heart texture too large");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}
