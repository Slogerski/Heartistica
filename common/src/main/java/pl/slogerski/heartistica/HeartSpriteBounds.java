package pl.slogerski.heartistica;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;

record HeartSpriteBounds(int x, int y, int width, int height) {
    HeartSpriteBounds {
        if (x < 0 || y < 0 || width < 1 || height < 1) throw new IllegalArgumentException("Invalid heart content bounds");
    }

    static HeartSpriteBounds of(BufferedImage... images) {
        if (images.length == 0) throw new IllegalArgumentException("Missing heart image");
        int w = images[0].getWidth(), h = images[0].getHeight();
        int left = w, top = h, right = -1, bottom = -1;
        for (BufferedImage image : images) {
            if (image.getWidth() != w || image.getHeight() != h) throw new IllegalArgumentException("Mismatched heart image dimensions");
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return right < 0 ? new HeartSpriteBounds(0, 0, w, h)
                : new HeartSpriteBounds(left, top, right - left + 1, bottom - top + 1);
    }

    static HeartSpriteBounds read(JsonObject data, int canvasWidth, int canvasHeight) {
        if (!data.has("content")) return new HeartSpriteBounds(0, 0, canvasWidth, canvasHeight);
        JsonObject content = data.getAsJsonObject("content");
        var bounds = new HeartSpriteBounds(content.get("x").getAsInt(), content.get("y").getAsInt(),
                content.get("width").getAsInt(), content.get("height").getAsInt());
        if (bounds.width > canvasWidth || bounds.height > canvasHeight
                || bounds.x > canvasWidth - bounds.width || bounds.y > canvasHeight - bounds.height)
            throw new IllegalArgumentException("Heart content outside sprite");
        return bounds;
    }
}
