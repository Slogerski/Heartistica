package pl.slogerski.heartistica;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

final class HeartSpriteBoundsTest {
    private static int checks;

    static void verify() throws Exception {
        checks = 0;
        BufferedImage full = rectangle(16, 16, 1, 3, 13, 11);
        BufferedImage half = rectangle(16, 16, 1, 3, 7, 11);
        var bounds = new HeartSpriteBounds(1, 3, 13, 11);
        check(HeartSpriteBounds.of(full).equals(bounds), "transparent margins are excluded");
        check(HeartSpriteBounds.of(full, half).equals(bounds), "half hearts share the full heart coordinate window");
        BufferedImage gold = rectangle(16, 16, 0, 2, 15, 12);
        check(HeartSpriteBounds.of(full, half, gold).equals(new HeartSpriteBounds(0, 2, 15, 12)),
                "shared bounds preserve absorption artwork outside the normal heart");
        BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        check(HeartSpriteBounds.of(blank).equals(new HeartSpriteBounds(0, 0, 16, 16)), "transparent images retain valid dimensions");
        BufferedImage translucent = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        translucent.setRGB(4, 5, 0x01FF0000);
        check(HeartSpriteBounds.of(translucent).equals(new HeartSpriteBounds(4, 5, 1, 1)), "nonzero alpha is retained");
        reject(() -> HeartSpriteBounds.of(), "missing sprite is rejected");
        reject(() -> HeartSpriteBounds.of(full, new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)),
                "mismatched canvases are rejected");
        check(HeartSpriteBounds.read(new JsonObject(), 16, 16).equals(new HeartSpriteBounds(0, 0, 16, 16)),
                "older manifests retain their original coordinate window");
        check(HeartSpriteBounds.read(data(1, 3, 13, 11), 16, 16).equals(bounds), "manifest content bounds round trip");
        reject(() -> HeartSpriteBounds.read(data(-1, 0, 13, 11), 16, 16), "negative origin is rejected");
        reject(() -> HeartSpriteBounds.read(data(0, 0, 0, 11), 16, 16), "zero content width is rejected");
        reject(() -> HeartSpriteBounds.read(data(0, 0, 13, 17), 16, 16), "oversized content is rejected");
        reject(() -> HeartSpriteBounds.read(data(4, 3, 13, 11), 16, 16), "content cannot cross the next atlas slot");
        reject(() -> HeartSpriteBounds.read(data(1, Integer.MAX_VALUE, 13, 11), 16, 16), "origin checks cannot overflow");

        var theme = new HeartCatalog.Theme("test", "Test", "single", Map.of());
        var atlas = CatalogImages.atlas(theme, Map.of("image", png(full)));
        check(atlas.width() == 16 && atlas.height() == 16, "atlas retains the original sprite dimensions");
        check(atlas.content().equals(bounds), "generated variants keep one shared crop");
        BufferedImage packed = ImageIO.read(new ByteArrayInputStream(atlas.png()));
        for (int i = 0; i < 5; i++) {
            BufferedImage sprite = packed.getSubimage(i * 18 + 1, 1, 16, 16);
            check(HeartSpriteBounds.of(sprite).equals(bounds), "variant " + i + " has matching visible bounds");
        }
        float height = 8;
        float width = height * bounds.width() / bounds.height();
        check(Math.abs(height / bounds.height() * HeartSpriteBounds.of(full).height() - height) < 0.0001F,
                "visible custom height matches the renderer height");
        check(Math.abs(width - 104F / 11) < 0.0001F, "visible artwork keeps its aspect ratio");
        check(Math.abs(HeartDisplayState.iconAdvance(width, atlas.width()) - (width - 1) - 0.5F) < 0.0001F,
                "cropped 16-pixel artwork gains exactly half a pixel between consecutive hearts");

        try (var files = Files.list(Path.of("catalog/Event"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".png")).sorted().toList()) {
                byte[] bytes = Files.readAllBytes(file);
                var actual = CatalogImages.atlas(theme, Map.of("image", bytes));
                check(actual.content().equals(HeartSpriteBounds.of(ImageIO.read(new ByteArrayInputStream(bytes)))),
                        file.getFileName() + " is normalized to its visible artwork");
            }
        }
        verifyBuiltIn();
        System.out.println("Heart sprite bounds regression checks passed: " + checks);
    }

    private static void verifyBuiltIn() throws Exception {
        Path assets = Path.of("build/generated/heart-assets/assets/heartistica");
        var manifest = JsonParser.parseString(Files.readString(assets.resolve("heart_styles.json"))).getAsJsonArray();
        check(!manifest.isEmpty(), "built-in styles have generated metadata");
        for (var entry : manifest) {
            JsonObject style = entry.getAsJsonObject();
            int width = style.get("width").getAsInt(), height = style.get("height").getAsInt();
            HeartSpriteBounds content = HeartSpriteBounds.read(style, width, height);
            String texture = style.get("texture").getAsString().substring("heartistica:".length());
            BufferedImage image = ImageIO.read(assets.resolve(texture).toFile());
            BufferedImage[] sprites = new BufferedImage[5];
            for (int i = 0; i < sprites.length; i++) sprites[i] = image.getSubimage(i * (width + 2) + 1, 1, width, height);
            check(content.equals(HeartSpriteBounds.of(sprites)), "build-time and runtime crop calculations agree");
        }
    }

    private static JsonObject data(int x, int y, int width, int height) {
        JsonObject result = new JsonObject(), content = new JsonObject();
        content.addProperty("x", x);
        content.addProperty("y", y);
        content.addProperty("width", width);
        content.addProperty("height", height);
        result.add("content", content);
        return result;
    }

    private static BufferedImage rectangle(int width, int height, int x, int y, int w, int h) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int row = y; row < y + h; row++) for (int column = x; column < x + w; column++) image.setRGB(column, row, 0xFFFF0000);
        return image;
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static void reject(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
