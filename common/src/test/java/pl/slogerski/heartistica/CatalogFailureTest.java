package pl.slogerski.heartistica;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;

final class CatalogFailureTest {
    private static final String IMAGE = "https://raw.githubusercontent.com/Slogerski/Heartistica/main/catalog/test.png";
    private static final String MANIFEST = """
            {"schemaVersion":1,"categories":{"Event":["test"],"Popular":["resource_pack"],
            "Test":[{"id":"test","image":"%s"}]}}
            """.formatted(IMAGE);
    private static int checks;

    static void verify() throws Exception {
        checks = 0;
        Path directory = Files.createTempDirectory("heartistica-failures-");
        try {
            verifyImages();
            verifyHeaders();
            verifyCache(directory);
            verifyShutdown(directory);
            verifySettings(directory);
            System.out.println("Failure handling regression checks passed: " + checks);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyImages() throws Exception {
        var single = new HeartCatalog.Theme("test", "Test", "single", Map.of());
        expectIo(() -> CatalogImages.decode(null), "missing image data is a recoverable image error");
        expectIo(() -> CatalogImages.atlas(single, Map.of()), "missing single image does not throw a null pointer exception");
        var sprites = new HeartCatalog.Theme("test", "Test", "sprites", Map.of());
        expectIo(() -> CatalogImages.atlas(sprites, Map.of()), "missing full sprite is a recoverable image error");
        byte[] full = image(16, 16), empty = image(8, 8);
        expectIo(() -> CatalogImages.atlas(sprites, Map.of("full", full, "empty", empty)),
                "mismatched empty sprite is rejected before deriving half hearts");
    }

    private static void verifyHeaders() throws Exception {
        byte[] body = new byte[0];
        check(new CatalogHttp.Response(200, null, body).etag().isEmpty(), "missing ETag is normalized");
        check(new CatalogHttp.Response(200, "bad\r\nheader", body).etag().isEmpty(), "ETag cannot inject request headers");
        check(new CatalogHttp.Response(200, "x".repeat(513), body).etag().isEmpty(), "oversized ETag is discarded");
        check(new CatalogHttp.Response(200, "W/\"valid\"", body).etag().equals("W/\"valid\""), "valid weak ETag is preserved");
        URI uri = URI.create(IMAGE);
        expectIo(() -> CatalogHttp.get(uri, "", 0), "zero response limit is rejected before connecting");
        expectIo(() -> CatalogHttp.get(uri, "", CatalogImages.MAX_BYTES + 1), "oversized response limit is rejected before connecting");
        Thread.currentThread().interrupt();
        try {
            expectIo(() -> CatalogHttp.get(uri, "", 1024), "interrupted shutdown does not start another HTTP request");
            check(Thread.currentThread().isInterrupted(), "HTTP cancellation preserves the interrupt flag");
        } finally { Thread.interrupted(); }
    }

    private static void verifyCache(Path directory) throws Exception {
        byte[] pixels = image(16, 16);
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        CountDownLatch installed = new CountDownLatch(1);
        try (var service = new CatalogService(blocked, MANIFEST, atlas -> installed.countDown(),
                (uri, etag, limit) -> new CatalogHttp.Response(200, "", pixels))) {
            service.request("test");
            check(installed.await(5, TimeUnit.SECONDS), "unwritable cache does not prevent installing downloaded hearts");
        }

        Path corrupt = directory.resolve("corrupt");
        Files.createDirectories(corrupt.resolve("images"));
        Path cachedImage = imagePath(corrupt);
        Files.write(cachedImage, new byte[CatalogImages.MAX_BYTES + 1]);
        CountDownLatch recovered = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        try (var service = new CatalogService(corrupt, MANIFEST, atlas -> recovered.countDown(), (uri, etag, limit) -> {
            requests.incrementAndGet();
            return new CatalogHttp.Response(200, "", pixels);
        })) {
            service.request("test");
            check(recovered.await(5, TimeUnit.SECONDS), "oversized cached image is replaced instead of blocking a download");
            check(requests.get() == 1, "corrupt cache recovery makes one image request");
        }

        Path catalogCache = directory.resolve("catalog");
        Files.createDirectories(catalogCache.resolve("catalog.json"));
        Path metadata = catalogCache.resolve("catalog-meta.json");
        String previous = "{\"etag\":\"old\"}";
        Files.writeString(metadata, previous);
        String updated = MANIFEST.replace("\"test\"", "\"updated\"");
        try (var service = new CatalogService(catalogCache, MANIFEST, atlas -> {}, (uri, etag, limit) ->
                new CatalogHttp.Response(200, "new", uri.getPath().endsWith("catalog.json")
                        ? updated.getBytes(StandardCharsets.UTF_8) : pixels))) {
            service.start();
            await(() -> service.catalog().themes.containsKey("updated"));
            CountDownLatch refreshed = new CountDownLatch(1);
            service.checkResourcePack(new Object(), () -> { refreshed.countDown(); return false; });
            check(refreshed.await(5, TimeUnit.SECONDS), "catalog refresh finishes despite a failed cache write");
            check(Files.readString(metadata).equals(previous), "failed catalog write cannot publish an ETag for unsaved data");
        }

        Path imageCache = directory.resolve("image-write");
        Files.createDirectories(imagePath(imageCache));
        Path imageTag = imagePath(imageCache).resolveSibling(imagePath(imageCache).getFileName().toString().replace(".png", ".etag"));
        Files.writeString(imageTag, "old");
        CountDownLatch downloaded = new CountDownLatch(1);
        try (var service = new CatalogService(imageCache, MANIFEST, atlas -> downloaded.countDown(),
                (uri, etag, limit) -> new CatalogHttp.Response(200, "new", pixels))) {
            service.request("test");
            check(downloaded.await(5, TimeUnit.SECONDS), "failed image cache write still installs valid downloaded artwork");
            check(Files.readString(imageTag).equals("old"), "failed image write cannot cache an ETag for another PNG");
        }

        Path unsupported = directory.resolve("unsupported");
        Files.createDirectories(unsupported.resolve("images"));
        Files.write(imagePath(unsupported), image(257, 16));
        AtomicInteger redownloads = new AtomicInteger();
        try (var service = new CatalogService(unsupported, MANIFEST, atlas -> {}, (uri, etag, limit) -> {
            redownloads.incrementAndGet();
            return new CatalogHttp.Response(200, "", pixels);
        })) {
            service.request("test");
            await(() -> service.needsUpdate("test"));
            check(redownloads.get() == 0, "cached unsupported artwork is diagnosed without downloading it again");
        }
    }

    private static void verifyShutdown(Path directory) throws Exception {
        byte[] pixels = image(16, 16);
        AtomicInteger requests = new AtomicInteger(), inspections = new AtomicInteger();
        var closed = new CatalogService(directory.resolve("closed"), MANIFEST, atlas -> {}, (uri, etag, limit) -> {
            requests.incrementAndGet();
            return new CatalogHttp.Response(200, "", pixels);
        });
        closed.close();
        closed.start();
        closed.request("test");
        closed.checkResourcePack(new Object(), () -> { inspections.incrementAndGet(); return false; });
        closed.close();
        check(requests.get() == 0 && inspections.get() == 0, "closed service accepts late calls without rejected-task exceptions or new work");

        for (int attempt = 0; attempt < 8; attempt++) {
            try (var service = new CatalogService(directory.resolve("race-" + attempt), MANIFEST, atlas -> {},
                    (uri, etag, limit) -> new CatalogHttp.Response(200, "", pixels))) {
                CountDownLatch start = new CountDownLatch(1);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread submit = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 30; i++) {
                            service.request("test");
                            service.checkResourcePack(new Object(), () -> false);
                        }
                    } catch (Throwable error) { failure.set(error); }
                });
                submit.start();
                start.countDown();
                service.close();
                submit.join(5000);
                check(!submit.isAlive() && failure.get() == null, "concurrent shutdown safely rejects new catalog work");
            }
        }
    }

    private static void verifySettings(Path directory) throws Exception {
        Path settings = directory.resolve("settings.json");
        String oversized = "{\"range\":12,\"padding\":\"" + "x".repeat(65_536) + "\"}";
        Files.writeString(settings, oversized);
        check(HeartisticaConfig.load(settings).range == 48, "oversized settings fall back safely");
        check(Files.readString(settings).equals(oversized), "reading invalid settings does not overwrite the original file");
    }

    private static Path imagePath(Path directory) {
        String key = CatalogImages.sha((IMAGE + "|").getBytes(StandardCharsets.UTF_8), false);
        return directory.resolve("images").resolve(key + ".png");
    }

    private static byte[] image(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        check(condition.getAsBoolean(), "asynchronous failure handling completes");
    }

    @FunctionalInterface private interface IoAction { void run() throws Exception; }

    private static void expectIo(IoAction action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); }
        catch (IOException expected) { checks++; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
