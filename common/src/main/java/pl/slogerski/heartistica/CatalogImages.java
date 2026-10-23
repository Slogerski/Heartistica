package pl.slogerski.heartistica;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class CatalogImages {
    static final int MAX_BYTES = 524_288;
    static final int MAX_SPRITE_SIZE = 256;
    record Atlas(String id, int width, int height, int atlasWidth, int atlasHeight, HeartSpriteBounds content, byte[] png) {}

    static final class UnsupportedDimensions extends IOException {
        final byte[] image;
        UnsupportedDimensions(byte[] image) {
            super("Heart dimensions are not supported by this version");
            this.image = image;
        }
        Atlas preview(HeartCatalog.Theme theme) throws IOException {
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(image))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return null;
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (w > 4096 || h > 4096 || (long) w * h > 4_194_304) return null;
                    var parameters = reader.getDefaultReadParam();
                    if (theme.layout().equals("strip")) {
                        if (w % 5 != 0) return null;
                        w /= 5;
                        parameters.setSourceRegion(new java.awt.Rectangle(0, 0, w, h));
                    }
                    int scale = Math.max(1, (Math.max(w, h) + 63) / 64);
                    parameters.setSourceSubsampling(scale, scale, 0, 0);
                    BufferedImage thumbnail = reader.read(0, parameters);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(thumbnail, "png", output);
                    return atlas(new HeartCatalog.Theme(theme.id(), theme.name(), "single", Map.of()), Map.of("image", output.toByteArray()));
                } finally { reader.dispose(); }
            }
        }
    }

    static BufferedImage decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid image size");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported catalog image");
            var reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) throw new IOException("Catalog images must be PNG");
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1) throw new IOException("Invalid image dimensions");
                if (w > MAX_SPRITE_SIZE * 5 || h > MAX_SPRITE_SIZE) throw new UnsupportedDimensions(bytes);
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    static Atlas atlas(HeartCatalog.Theme theme, Map<String, byte[]> assets) throws IOException {
        BufferedImage[] sprites = new BufferedImage[5];
        if (theme.layout().equals("sprites")) {
            String[] keys = {"full", "half", "empty", "absorptionFull", "absorptionHalf"};
            for (int i = 0; i < keys.length; i++) if (assets.containsKey(keys[i])) sprites[i] = decode(assets.get(keys[i]));
        } else {
            BufferedImage image = decode(assets.get("image"));
            if (theme.layout().equals("strip")) {
                if (image.getWidth() % 5 != 0) throw new IOException("Heart strip must have five equal columns");
                int w = image.getWidth() / 5;
                for (int i = 0; i < 5; i++) sprites[i] = image.getSubimage(i * w, 0, w, image.getHeight());
            } else sprites[0] = image;
        }
        if (sprites[0] == null) throw new IOException("Missing full heart sprite");
        int w = sprites[0].getWidth(), h = sprites[0].getHeight();
        if (w > MAX_SPRITE_SIZE || h > MAX_SPRITE_SIZE) throw new UnsupportedDimensions(assets.get(theme.layout().equals("sprites") ? "full" : "image"));
        for (BufferedImage sprite : sprites) if (sprite != null && (sprite.getWidth() > MAX_SPRITE_SIZE || sprite.getHeight() > MAX_SPRITE_SIZE))
            throw new UnsupportedDimensions(assets.get(theme.layout().equals("sprites") ? "full" : "image"));
        for (BufferedImage sprite : sprites) if (sprite != null && (sprite.getWidth() != w || sprite.getHeight() != h))
            throw new IOException("Mismatched catalog sprites");
        if (sprites[2] == null) sprites[2] = tint(sprites[0], false);
        if (sprites[1] == null) sprites[1] = half(sprites[0], sprites[2]);
        if (sprites[3] == null) sprites[3] = tint(sprites[0], true);
        if (sprites[4] == null) sprites[4] = half(sprites[3], sprites[2]);
        HeartSpriteBounds content = HeartSpriteBounds.of(sprites);
        int aw = Integer.highestOneBit((w + 2) * 5 - 1) << 1, ah = Integer.highestOneBit(h + 1) << 1;
        BufferedImage atlas = new BufferedImage(aw, ah, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < sprites.length; i++) atlas.setRGB(i * (w + 2) + 1, 1, w, h, sprites[i].getRGB(0, 0, w, h, null, 0, w), 0, w);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(atlas, "png", output);
        return new Atlas(theme.id(), w, h, aw, ah, content, output.toByteArray());
    }

    private static BufferedImage tint(BufferedImage source, boolean gold) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int pixel = source.getRGB(x, y);
            int light = Math.max(pixel >>> 16 & 255, Math.max(pixel >>> 8 & 255, pixel & 255));
            int r = gold ? 105 + 150 * light / 255 : light / 4;
            int g = gold ? 55 + 165 * light / 255 : light / 4;
            int b = gold ? 5 + 55 * light / 255 : light / 4;
            result.setRGB(x, y, pixel & 0xFF000000 | r << 16 | g << 8 | b);
        }
        return result;
    }

    private static BufferedImage half(BufferedImage full, BufferedImage empty) {
        BufferedImage result = new BufferedImage(full.getWidth(), full.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < full.getHeight(); y++) for (int x = 0; x < full.getWidth(); x++) {
            result.setRGB(x, y, (x < (full.getWidth() + 1) / 2 ? full : empty).getRGB(x, y));
        }
        return result;
    }

    static String sha(byte[] bytes, boolean gitBlob) {
        try {
            MessageDigest digest = MessageDigest.getInstance(gitBlob ? "SHA-1" : "SHA-256");
            if (gitBlob) digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    static boolean matches(byte[] bytes, String hash) { return hash.isEmpty() || sha(bytes, hash.length() == 40).equals(hash); }
}
