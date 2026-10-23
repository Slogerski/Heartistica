package pl.slogerski.heartistica;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public final class CatalogTest {
    private static int checks;
    private static final String CATALOG = """
            {"schemaVersion":1,"event":{"id":"winter"},"categories":{
              "zimowe":[{"id":"ice","name":"Lód","image":"https://github.com/Slogerski/Heartistica/blob/main/remote/ice.png"}],
              "Event":["ice"],"Popular":["ice","resource_pack"],"limited":["Lód"]}}
            """;
    public static void main(String[] args) throws Exception {
        check(HeartCatalog.parse(Files.readString(Path.of("remote/catalog.json"))).themes.size() >= 2, "project catalog matches the runtime schema");
        HeartCatalog catalog = HeartCatalog.parse(CATALOG);
        check(catalog.categories.stream().map(HeartCatalog.Category::id).toList().equals(List.of("event", "popular", "zimowe", "limited")), "category order");
        check(catalog.category("limited").equals(List.of("ice")), "name references resolve");
        check(catalog.category("event").equals(List.of("ice")), "event references resolve across categories");
        check(catalog.themes.get("ice").images().get("image").url().toString()
                .equals("https://raw.githubusercontent.com/Slogerski/Heartistica/main/remote/ice.png"), "GitHub blob URL is normalized");
        check(catalog.eventActive(Instant.now()), "nonempty event category is active");
        reject(CATALOG.replace("\"schemaVersion\":1", "\"schemaVersion\":99"));
        reject(CATALOG.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5"));
        reject(CATALOG.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""));
        reject(CATALOG.replace("\"Event\":[\"ice\"]", "\"Event\":[\"missing\"]"));
        reject(CATALOG.replace("\"id\":\"ice\"", "\"id\":\"../escape\""));
        reject(CATALOG.replace("https://github.com/Slogerski/Heartistica/blob/main/remote/ice.png", "http://127.0.0.1/private"));
        reject(CATALOG.replace("https://github.com/Slogerski/Heartistica/blob/main/remote/ice.png", "file:///private"));
        reject(CATALOG.replace("https://github.com/Slogerski/Heartistica/blob/main/remote/ice.png", "https://raw.githubusercontent.com:123/private"));
        reject(CATALOG.replace("\"image\":", "\"sha\":\"wrong\",\"image\":"));
        HeartCatalog timed = HeartCatalog.parse(CATALOG.replace("\"id\":\"winter\"", "\"id\":\"winter\",\"startsAt\":\"2030-01-01T00:00:00Z\",\"endsAt\":\"2030-02-01T00:00:00Z\""));
        check(!timed.eventActive(Instant.parse("2029-12-31T23:59:59Z")), "future event is inactive");
        check(timed.eventActive(Instant.parse("2030-01-01T00:00:00Z")), "event starts inclusively");
        check(!timed.eventActive(Instant.parse("2030-02-01T00:00:00Z")), "event ends exclusively");
        check(CatalogImages.sha(new byte[0], true).equals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391"), "Git blob SHA");
        check(CatalogImages.sha(new byte[0], false).equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"), "SHA-256");
        byte[] image = image(9, 8);
        check(CatalogImages.matches(image, CatalogImages.sha(image, false)), "matching image hash");
        check(!CatalogImages.matches(image, "0".repeat(64)), "mismatching image hash");
        var atlas = CatalogImages.atlas(catalog.themes.get("ice"), Map.of("image", image));
        check(atlas.width() == 9 && atlas.height() == 8 && atlas.atlasWidth() >= 55, "single PNG generates five sprites");
        check(ImageIO.read(new java.io.ByteArrayInputStream(atlas.png())).getRGB(1, 1) == 0xFFFF0000, "atlas preserves original color");
        try { CatalogImages.decode(image(1281, 1)); throw new AssertionError("oversized image accepted"); } catch (IOException expected) { checks++; }
        try { CatalogImages.decode(new byte[]{1, 2, 3}); throw new AssertionError("invalid image accepted"); } catch (IOException expected) { checks++; }
        CatalogScroll smooth = new CatalogScroll();
        smooth.limit(100); smooth.to(100);
        double first = smooth.step(1_000_000_000L);
        check(first > 0 && first < 100, "scroll interpolates");
        check(smooth.step(1_100_000_000L) > first, "scroll progresses");
        smooth.limit(10);
        check(smooth.target() == 10 && smooth.step(1_200_000_000L) <= 10, "scroll clamps after resize");
        check(UiText.get("pl_pl", "heartistica.catalog.open").equals("Otwórz Katalog"), "Polish language");
        check(UiText.get("en_us", "heartistica.catalog.open").equals("Open Catalog"), "English language");
        HeartisticaConfig preferences = new HeartisticaConfig();
        check(preferences.eventHeartsEnabled(false) && preferences.language.equals("en_us"), "event hearts default on and UI defaults to English");
        preferences.setEventHearts(false);
        preferences.language = "pl_pl";
        check(preferences.copy().eventHeartsMode == HeartisticaConfig.EventHeartsMode.OFF && preferences.copy().language.equals("pl_pl"), "catalog preferences are included in snapshots");
        preferences.reset();
        check(preferences.eventHeartsMode == HeartisticaConfig.EventHeartsMode.AUTO && preferences.language.equals("en_us"), "reset restores catalog preferences");
        preferences.heartStyle = "heartistica";
        preferences.setEventHearts(false);
        CatalogService.restore(preferences);
        check(preferences.heartStyle.equals("resource_pack") && !preferences.eventHeartsEnabled(false),
                "explicit original-heart choice is not overridden by event hearts");
        check(preferences.eventHeartsMode == HeartisticaConfig.EventHeartsMode.OFF, "choosing original hearts preserves manual event mode");
        verifyService(image);
        verifyDimensions();
        verifyStartupChecks();
        verifyLayout();
        verifyMouseButtons();
        verifyLanguageProfiles();
        LanguageSettingsTest.verify();
        verifyEventPreferences();
        verifyHeartTextures();
        HeartSpriteBoundsTest.verify();
        CatalogFailureTest.verify();
        System.out.println("Catalog regression checks passed: " + checks);
    }

    private static void verifyService(byte[] image) throws Exception {
        Path directory = Files.createTempDirectory("heartistica-catalog-test-");
        try {
            HeartisticaConfig config = new HeartisticaConfig();
            try (CatalogService service = new CatalogService(directory, CATALOG, atlas -> {}, (uri, etag, limit) -> new CatalogHttp.Response(200, "", image))) {
                check(service.selected(config).equals("ice"), "event overrides default theme");
                check(service.selected(config).equals("ice"), "event selection stays stable");
                CatalogService.select(config, "heartistica");
                check(!service.eventsEnabled(config) && service.selected(config).equals("heartistica"), "automatic mode respects a selected catalog theme");
                config.setEventHearts(true);
                check(service.selected(config).equals("ice") && config.heartStyle.equals("heartistica"), "reenabling event overrides but preserves manual theme");
                CatalogService.restore(config);
                check(config.heartStyle.equals("resource_pack") && service.eventsEnabled(config), "restore returns original hearts with remembered preference");
                FakeCanvas canvas = new FakeCanvas();
                CatalogView view = new CatalogView(service, config);
                view.render(canvas, 500, 100, 28, 400, false);
                check(!canvas.names.contains("Popular") && !canvas.names.contains("Event"), "embedded Popular strip omits category headers");
                check(canvas.fills.stream().anyMatch(rect -> rect[1] == 100 && rect[3] == 164), "embedded cards use the space freed by their header");
                check(canvas.clips.stream().anyMatch(rect -> rect[0] == 100 && rect[2] == 400), "strip width is sixty percent and centered");
                canvas.names.clear();
                view.render(canvas, 500, 146, 144, 500, true);
                check(canvas.names.indexOf("Event") < canvas.names.indexOf("Popular"), "full catalog shows event before popular");
                check(!view.click(0, 0, 0), "catalog does not intercept controls outside viewport");
            }
            AtomicInteger requests = new AtomicInteger();
            CountDownLatch installed = new CountDownLatch(1);
            String hashed = CATALOG.replace("\"image\":", "\"sha\":\"" + CatalogImages.sha(image, true) + "\",\"image\":");
            try (CatalogService service = new CatalogService(directory, hashed, atlas -> installed.countDown(), (uri, etag, limit) -> {
                requests.incrementAndGet(); return new CatalogHttp.Response(200, "", image);
            })) {
                service.request("ice"); service.request("ice");
                check(installed.await(5, TimeUnit.SECONDS), "asset installs asynchronously");
                check(requests.get() == 1, "in-flight downloads are shared");
            }
            CountDownLatch cached = new CountDownLatch(1);
            try (CatalogService service = new CatalogService(directory, hashed, atlas -> cached.countDown(), (uri, etag, limit) -> {
                throw new AssertionError("hashed image should not redownload");
            })) {
                service.request("ice");
                check(cached.await(5, TimeUnit.SECONDS), "hashed cache is reused offline");
            }
            Files.writeString(directory.resolve("catalog.json"), hashed);
            CountDownLatch attempted = new CountDownLatch(1);
            try (CatalogService service = new CatalogService(directory, "{\"schemaVersion\":1,\"categories\":{}}", atlas -> {}, (uri, etag, limit) -> {
                attempted.countDown(); throw new NoClassDefFoundError("HTTP unavailable");
            })) {
                service.start();
                check(attempted.await(5, TimeUnit.SECONDS), "catalog refresh runs outside rendering");
                check(service.catalog().themes.containsKey("ice"), "missing HTTP provider preserves cached catalog");
            }
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static void verifyDimensions() throws Exception {
        var theme = HeartCatalog.parse(CATALOG).themes.get("ice");
        check(CatalogImages.atlas(theme, Map.of("image", image(256, 256))).width() == 256, "maximum supported sprite is accepted");
        try {
            CatalogImages.atlas(theme, Map.of("image", image(257, 16)));
            throw new AssertionError("unsupported sprite width accepted");
        } catch (CatalogImages.UnsupportedDimensions expected) {
            var preview = expected.preview(theme);
            check(preview != null && preview.width() <= 64 && preview.height() <= 64, "unsupported sprite gets bounded preview");
        }
        try {
            CatalogImages.decode(image(16, 257));
            throw new AssertionError("unsupported sprite height accepted");
        } catch (CatalogImages.UnsupportedDimensions expected) { checks++; }
        try {
            CatalogImages.decode(new byte[]{1, 2, 3});
            throw new AssertionError("invalid PNG accepted");
        } catch (CatalogImages.UnsupportedDimensions wrong) {
            throw new AssertionError("invalid PNG incorrectly requests update", wrong);
        } catch (IOException expected) { checks++; }
        Path directory = Files.createTempDirectory("heartistica-dimensions-test-");
        byte[] oversized = image(257, 16);
        AtomicInteger downloads = new AtomicInteger();
        try {
            try (CatalogService service = new CatalogService(directory, CATALOG, atlas -> {}, (uri, etag, limit) -> {
                downloads.incrementAndGet(); return new CatalogHttp.Response(200, "", oversized);
            })) {
                HeartisticaConfig config = new HeartisticaConfig();
                service.request("ice");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!service.needsUpdate("ice") && System.nanoTime() < deadline) Thread.sleep(2);
                check(service.needsUpdate("ice"), "unsupported dimensions are tracked per theme");
                check(service.selected(config).equals("resource_pack"), "unsupported event theme falls back to original hearts");
                config.heartStyle = "ice";
                config.setEventHearts(false);
                check(service.selected(config).equals("resource_pack"), "saved unsupported theme falls back without changing preference");
                check(config.heartStyle.equals("ice"), "unsupported selection is preserved for a future update");
                service.request("ice");
                check(downloads.get() == 1, "unsupported theme is not downloaded every frame");
                CatalogView view = new CatalogView(service, config);
                FakeCanvas canvas = new FakeCanvas();
                view.render(canvas, 500, 100, 28, 400, false);
                check(canvas.names.contains("Need Update"), "unsupported tile displays update label");
                check(canvas.fills.stream().anyMatch(rect -> rect[4] == 0x99000000), "unsupported tile preview is dimmed");
                config.heartStyle = "resource_pack";
                check(view.click(120, 140, 0) && config.heartStyle.equals("resource_pack"), "unsupported tile cannot be selected");
                config.language = "pl_pl";
                canvas.names.clear();
                view.render(canvas, 500, 100, 28, 400, false);
                check(canvas.names.contains("Aktualizuj"), "Polish update label is translated");
            }
            CountDownLatch failed = new CountDownLatch(1);
            try (CatalogService service = new CatalogService(directory.resolve("offline"), CATALOG, atlas -> {}, (uri, etag, limit) -> {
                failed.countDown(); throw new IOException("Offline");
            })) {
                service.request("ice");
                check(failed.await(5, TimeUnit.SECONDS), "offline asset request completes");
                check(!service.needsUpdate("ice"), "network failure does not request a mod update");
            }
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verifyStartupChecks() throws Exception {
        var data = com.google.gson.JsonParser.parseString(CATALOG).getAsJsonObject();
        var themes = data.getAsJsonObject("categories").getAsJsonArray("zimowe");
        var bad = themes.get(0).getAsJsonObject().deepCopy();
        bad.addProperty("id", "bad");
        bad.addProperty("name", "Oversized");
        bad.addProperty("image", "https://raw.githubusercontent.com/Slogerski/Heartistica/main/remote/bad.png");
        themes.add(bad);
        byte[] manifest = data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] valid = image(9, 9), oversized = image(257, 16);
        Path directory = Files.createTempDirectory("heartistica-startup-test-");
        AtomicInteger manifests = new AtomicInteger(), images = new AtomicInteger(), uploads = new AtomicInteger();
        CountDownLatch installed = new CountDownLatch(1);
        try {
            try (CatalogService service = new CatalogService(directory, CATALOG, atlas -> {
                uploads.incrementAndGet();
                if (atlas.id().equals("ice")) installed.countDown();
            }, (uri, etag, limit) -> {
                if (uri.getPath().endsWith("catalog.json")) {
                    manifests.incrementAndGet(); return new CatalogHttp.Response(200, "catalog-v1", manifest);
                }
                images.incrementAndGet();
                return new CatalogHttp.Response(200, "", uri.getPath().endsWith("bad.png") ? oversized : valid);
            })) {
                check(images.get() == 0 && manifests.get() == 0, "catalog construction does not start network checks during game loading");
                service.start();
                service.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!service.needsUpdate("bad") && System.nanoTime() < deadline) Thread.sleep(2);
                check(service.needsUpdate("bad"), "startup checks unsupported hearts without opening a screen");
                check(manifests.get() == 1 && images.get() == 2, "startup scans every remote theme and starts only once");
                check(installed.getCount() == 1, "startup validation does not upload unused full textures");
                service.request("ice");
                check(installed.await(5, TimeUnit.SECONDS), "validated heart installs on demand");
                check(images.get() == 2, "on-demand installation reuses the startup image cache");
            }
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static final class FakeCanvas implements CatalogView.Canvas {
        final List<String> names = new ArrayList<>();
        final List<int[]> clips = new ArrayList<>();
        final List<int[]> fills = new ArrayList<>();
        public void fill(int x, int y, int right, int bottom, int color) { fills.add(new int[]{x, y, right, bottom, color}); }
        public void text(String text, int x, int y, int color) { names.add(text); }
        public void heart(String id, int x, int y, int size) {}
        public void clip(int x, int y, int right, int bottom) { clips.add(new int[]{x, y, right, bottom}); }
        public void unclip() {}
    }
    private static void verifyLayout() throws Exception {
        var data = com.google.gson.JsonParser.parseString(CATALOG).getAsJsonObject();
        var categories = data.getAsJsonObject("categories");
        var winter = categories.getAsJsonArray("zimowe");
        var popular = categories.getAsJsonArray("Popular");
        for (int i = 1; i <= 16; i++) {
            var theme = winter.get(0).getAsJsonObject().deepCopy();
            theme.addProperty("id", "ice_" + i);
            theme.addProperty("name", "Ice " + i);
            winter.add(theme);
            popular.add("ice_" + i);
        }
        byte[] pixels = image(9, 9);
        try (CatalogService service = new CatalogService(Path.of("build", "unused-catalog-test-cache"), data.toString(), atlas -> {},
                (uri, etag, limit) -> new CatalogHttp.Response(200, "", pixels))) {
            HeartisticaConfig config = new HeartisticaConfig();
            CatalogView view = new CatalogView(service, config);
            FakeCanvas first = new FakeCanvas();
            view.render(first, 600, 100, 28, 400, false, 1_000_000_000L);
            check(first.clips.stream().anyMatch(rect -> rect[0] == 120 && rect[2] == 480), "resized strip remains sixty percent wide");
            check(first.fills.stream().anyMatch(rect -> rect[3] - rect[1] == 2 && rect[4] == 0xFF121318), "catalog scrollbar track stays two pixels tall with the darker background");
            check(first.fills.stream().anyMatch(rect -> rect[3] - rect[1] == 2 && rect[4] == 0xFF454851), "scrollable rows use the darker visible thumb");
            check(view.click(470, 172, 0), "horizontal scrollbar can be grabbed");
            check(view.drag(480), "horizontal scrollbar can be dragged");
            check(view.release(), "scrollbar releases capture");
            FakeCanvas scrolled = new FakeCanvas();
            view.render(scrolled, 600, 100, 28, 400, false, 2_000_000_000L);
            check(!scrolled.names.contains("Active Res…"), "dragging advances visible cards");
            check(!view.click(20, 150, 0), "clipped heart cannot be selected outside the strip");
            check(view.scrolling(200, 150, 0, -1), "embedded row supports wheel scrolling");
            FakeCanvas fullCanvas = new FakeCanvas();
            view.render(fullCanvas, 600, 146, 144, 400, true, 3_000_000_000L);
            check(fullCanvas.fills.stream().anyMatch(rect -> rect[3] - rect[1] == 2 && rect[4] == 0xFF24262C),
                    "short catalog categories use the darker inactive separator");
            check(view.scrolling(300, 170, 0, -1), "full catalog supports vertical wheel scrolling over cards");
            Path reports = Path.of("build", "reports");
            Files.createDirectories(reports);
            for (int[] size : List.of(new int[]{854, 480}, new int[]{426, 240})) {
                try (RenderCanvas canvas = new RenderCanvas(size[0], size[1])) {
                    canvas.fill(0, 0, size[0], size[1], 0xFF0B0E14);
                    canvas.text("Heart Catalog", size[0] / 2, 12, 0xFFFFFFFF);
                    canvas.fill(size[0] / 2 - 155, 30, size[0] / 2 - 3, 50, 0xFF444852);
                    canvas.text("Restore", size[0] / 2 - 79, 36, 0xFFFFFFFF);
                    canvas.fill(size[0] / 2 + 3, 30, size[0] / 2 + 155, 50, 0xFF444852);
                    canvas.text("Event Hearts: ON", size[0] / 2 + 79, 36, 0xFFFFFFFF);
                    int top = CatalogView.top(size[1]);
                    new CatalogView(service, config).render(canvas, size[0], top + 2, top, size[1] - 36, true);
                    canvas.fill(size[0] / 2 - 75, size[1] - 28, size[0] / 2 + 75, size[1] - 8, 0xFF444852);
                    canvas.text("Back", size[0] / 2, size[1] - 22, 0xFFFFFFFF);
                    ImageIO.write(canvas.image, "png", reports.resolve("catalog-" + size[0] + ".png").toFile());
                }
            }
        }
    }
    private static void verifyMouseButtons() throws Exception {
        String manifest = """
                {"schemaVersion":1,"categories":{"Event":["resource_pack","heartistica"],
                "Popular":["resource_pack","heartistica"]}}
                """;
        try (var service = new CatalogService(Path.of("build", "unused-input-test-cache"), manifest, atlas -> {},
                (uri, etag, limit) -> { throw new AssertionError("Built-in hearts must not need HTTP"); })) {
            HeartisticaConfig config = new HeartisticaConfig();
            CatalogView view = new CatalogView(service, config);
            for (boolean full : new boolean[]{false, true}) {
                int y = full ? 146 : 100;
                int tileY = y + (full ? 15 : 0) + 20;
                view.render(new FakeCanvas(), 500, y, full ? 144 : 28, 400, full);
                config.heartStyle = "resource_pack";
                check(!view.click(190, tileY, 0, 1) && config.heartStyle.equals("resource_pack"),
                        "26.3 does not mistake button zero for a primary click");
                check(!view.click(190, tileY, 3, 1) && config.heartStyle.equals("resource_pack"),
                        "26.3 right click does not select a heart");
                check(view.click(190, tileY, 1, 1) && config.heartStyle.equals("heartistica"),
                        "26.3 primary click selects a heart in both catalog layouts");
                check(view.click(120, tileY, 1, 1) && config.heartStyle.equals("resource_pack"),
                        "26.3 primary click can restore resource-pack hearts");
                check(view.click(190, tileY, 0, 0) && config.heartStyle.equals("heartistica"),
                        "26.1 and 26.2 keep working with their native primary button");
                check(view.click(120, tileY, 0) && config.heartStyle.equals("resource_pack"),
                        "older adapters retain the existing zero-based click handler");
            }
            view.render(new FakeCanvas(), 200, 146, 144, 400, true);
            check(view.click(145, 231, 1, 1), "26.3 primary click grabs the horizontal scrollbar");
            check(view.drag(150) && view.release(), "26.3 scrollbar drag can be completed and released");
        }
    }

    private static final class RenderCanvas implements CatalogView.Canvas, AutoCloseable {
        final BufferedImage image;
        final java.awt.Graphics2D graphics;
        final BufferedImage heart;
        final List<java.awt.Shape> clips = new ArrayList<>();
        RenderCanvas(int width, int height) throws IOException {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            graphics = image.createGraphics();
            graphics.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 9));
            heart = ImageIO.read(Path.of("common/src/main/resources/assets/heartistica/textures/gui/heartistica/heart.png").toFile());
        }
        public void fill(int x, int y, int right, int bottom, int color) {
            graphics.setColor(new java.awt.Color(color, true)); graphics.fillRect(x, y, right - x, bottom - y);
        }
        public void text(String text, int x, int y, int color) {
            graphics.setColor(new java.awt.Color(color, true)); graphics.drawString(text, x - graphics.getFontMetrics().stringWidth(text) / 2, y + 8);
        }
        public void heart(String id, int x, int y, int size) { graphics.drawImage(heart, x, y, size, size, null); }
        public void clip(int x, int y, int right, int bottom) { clips.add(graphics.getClip()); graphics.clipRect(x, y, right - x, bottom - y); }
        public void unclip() { graphics.setClip(clips.remove(clips.size() - 1)); }
        public void close() { graphics.dispose(); }
    }
    private static void verifyLanguageProfiles() throws Exception {
        Path directory = Files.createTempDirectory("heartistica-language-test-");
        Path defaults = directory.resolve("defaults.json"), overrides = directory.resolve("servers.json");
        try {
            HeartisticaConfig config = new HeartisticaConfig();
            config.save(defaults);
            ServerProfiles profiles = new ServerProfiles(config, defaults, overrides);
            profiles.useServer("first.example");
            check(profiles.saveForServer(), "profile can store initial catalog preferences");
            config.language = "pl_pl";
            config.setEventHearts(false);
            profiles.saveCurrent();
            check(HeartisticaConfig.load(defaults).language.equals("pl_pl"), "UI language is saved globally even with a server override");
            profiles.useServer("second.example");
            check(config.language.equals("pl_pl") && config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.AUTO, "server changes retain UI language but restore event preference");
            profiles.useServer("first.example");
            check(config.language.equals("pl_pl") && config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.OFF, "server event preference survives reconnect");
            check(profiles.backToDefault() && config.language.equals("pl_pl"), "removing override preserves chosen UI language");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static byte[] image(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        ByteArrayOutputStream stream = new ByteArrayOutputStream(); ImageIO.write(image, "png", stream); return stream.toByteArray();
    }
    private static void verifyEventPreferences() throws Exception {
        HeartisticaConfig config = new HeartisticaConfig();
        check(config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.AUTO, "events default to automatic");
        check(config.eventHeartsEnabled(false) && !config.eventHeartsEnabled(true), "automatic mode follows actual resource pack heart changes");
        CatalogService.select(config, "heartistica");
        check(!config.eventHeartsEnabled(false) && config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.AUTO,
                "selected catalog theme disables events without leaving automatic mode");
        CatalogService.restore(config);
        check(config.eventHeartsEnabled(false), "restoring unchanged original hearts reenables automatic events");
        config.setEventHearts(true);
        CatalogService.select(config, "heartistica");
        CatalogService.select(config, "another_theme");
        check(config.eventHeartsEnabled(true) && config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.ON,
                "manual ON survives theme changes and modified pack hearts");
        config.setEventHearts(false);
        CatalogService.restore(config);
        check(!config.eventHeartsEnabled(false) && config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.OFF,
                "manual OFF survives restoring original hearts");
        config.reset();
        check(config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.AUTO && config.eventHeartsEnabled(false),
                "Reset Settings restores automatic mode");

        check(eventMode("{\"configVersion\":4,\"eventHearts\":true}") == HeartisticaConfig.EventHeartsMode.AUTO,
                "old default becomes automatic");
        check(eventMode("{\"configVersion\":4,\"eventHearts\":false}") == HeartisticaConfig.EventHeartsMode.OFF,
                "old explicit OFF is retained");
        check(eventMode("{\"configVersion\":4,\"eventHearts\":true,\"resourcePackEventHearts\":true}") == HeartisticaConfig.EventHeartsMode.ON,
                "old resource pack permission becomes manual ON");
        check(eventMode("{\"configVersion\":4,\"heartStyle\":\"heartistica\",\"catalogEventHearts\":true}") == HeartisticaConfig.EventHeartsMode.ON,
                "old enabled catalog events become manual ON");
        check(eventMode("{\"configVersion\":4,\"heartStyle\":\"heartistica\",\"catalogEventHearts\":false}") == HeartisticaConfig.EventHeartsMode.AUTO,
                "old catalog default becomes automatic");
        check(eventMode("{\"configVersion\":3,\"heartStyle\":\"heartistica\",\"eventHearts\":true}") == HeartisticaConfig.EventHeartsMode.ON,
                "older enabled catalog preference migrates");
        check(eventMode("{\"configVersion\":3,\"heartStyle\":\"heartistica\",\"eventHearts\":false}") == HeartisticaConfig.EventHeartsMode.OFF,
                "older disabled catalog preference migrates");
        check(eventMode("{\"configVersion\":5,\"eventHeartsMode\":\"OFF\",\"resourcePackEventHearts\":true}") == HeartisticaConfig.EventHeartsMode.OFF,
                "new event mode takes precedence over old fields");
        check(eventMode("{\"configVersion\":5,\"eventHeartsMode\":\"unknown\"}") == HeartisticaConfig.EventHeartsMode.AUTO,
                "unknown event mode safely falls back to automatic");

        config.reset();
        try (CatalogService service = new CatalogService(Path.of("build", "unused-events-cache"), CATALOG, atlas -> {})) {
            setResourcePack(service, false);
            check(service.eventsEnabled(config) && service.selected(config).equals("ice"), "automatic unchanged hearts use the active event");
            check(service.eventsLabel(config).endsWith("Auto (ON)"), "label identifies automatic ON");
            setResourcePack(service, true);
            check(!service.eventsEnabled(config) && service.selected(config).equals("resource_pack"), "automatic modified hearts keep resource pack appearance");
            check(service.eventsLabel(config).endsWith("Auto (OFF)"), "label identifies automatic OFF");
            check(config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.AUTO, "automatic changes never write a manual preference");
            service.toggleEvents(config);
            check(config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.ON && service.selected(config).equals("ice"),
                    "clicking automatic OFF selects manual ON and bypasses texture protection");
            check(service.eventsLabel(config).endsWith(": ON") && !service.eventsLabel(config).contains("Auto"), "manual label no longer claims automatic");
            setResourcePack(service, false);
            setResourcePack(service, true);
            check(service.eventsEnabled(config) && config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.ON,
                    "manual ON stays static across resource pack changes");
            service.toggleEvents(config);
            check(config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.OFF, "second click selects manual OFF");
            setResourcePack(service, false);
            check(!service.eventsEnabled(config) && service.selected(config).equals("resource_pack"), "manual OFF does not reenable when pack is removed");
            config.reset();
            check(service.eventsEnabled(config) && service.eventsLabel(config).endsWith("Auto (ON)"), "reset reenters automatic mode using current textures");
            service.toggleEvents(config);
            check(config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.OFF, "clicking automatic ON selects manual OFF");
            setResourcePack(service, true);
            config.reset();
            check(!service.eventsEnabled(config) && service.eventsLabel(config).endsWith("Auto (OFF)"), "reset with modified hearts restores automatic OFF");
            setResourcePack(service, false);
            check(service.eventsEnabled(config), "automatic mode resumes reacting after reset");
        }

        Path directory = Files.createTempDirectory("heartistica-events-test-");
        try {
            Path settings = directory.resolve("settings.json");
            for (var mode : HeartisticaConfig.EventHeartsMode.values()) {
                config.eventHeartsMode = mode;
                config.save(settings);
                var loaded = HeartisticaConfig.load(settings);
                check(loaded.eventHeartsMode == mode && loaded.copy().eventHeartsMode == mode, "all event modes survive restart and snapshots");
                var json = com.google.gson.JsonParser.parseString(Files.readString(settings)).getAsJsonObject();
                check(!json.has("eventHearts") && !json.has("catalogEventHearts") && !json.has("resourcePackEventHearts"),
                        "saved configuration contains no obsolete event flags");
                loaded.reset();
                check(loaded.eventHeartsMode == HeartisticaConfig.EventHeartsMode.AUTO, "reset removes each manual event override");
            }
            Files.delete(settings);
        } finally { Files.delete(directory); }
        String withoutResourcePackCard = "{\"schemaVersion\":1,\"categories\":{\"Popular\":[\"heartistica\"]}}";
        try (CatalogService service = new CatalogService(Path.of("build", "unused-restore-cache"), withoutResourcePackCard, atlas -> {})) {
            config.heartStyle = "heartistica";
            CatalogService.restore(config);
            check(service.selected(config).equals("resource_pack") && !service.catalog().category("popular").contains("resource_pack"),
                    "restore does not depend on a resource pack card in catalog categories");
        }
    }

    private static HeartisticaConfig.EventHeartsMode eventMode(String json) {
        return HeartisticaConfig.fromJson(com.google.gson.JsonParser.parseString(json).getAsJsonObject()).eventHeartsMode;
    }

    private static void setResourcePack(CatalogService service, boolean modified) throws Exception {
        CountDownLatch checked = new CountDownLatch(1);
        service.checkResourcePack(new Object(), () -> { checked.countDown(); return modified; });
        check(checked.await(2, TimeUnit.SECONDS), "resource pack check runs asynchronously");
        HeartisticaConfig probe = new HeartisticaConfig();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (service.eventsEnabled(probe) == modified && System.nanoTime() < deadline) Thread.sleep(2);
        check(service.eventsEnabled(probe) != modified, "resource pack check updates automatic event state");
    }

    private static void verifyHeartTextures() throws IOException {
        BufferedImage base = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        BufferedImage custom = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        custom.setRGB(0, 200, 0xFFFF0000);
        check(!different(base, custom, true), "legacy hotbar changes alone do not count as modified hearts");
        custom.setRGB(52, 0, 0xFFFF0000);
        check(different(base, custom, true), "legacy heart pixels identify a modified resource pack");
        custom.setRGB(52, 0, 0);
        custom.setRGB(169, 45, 0xFFFF0000);
        check(different(base, custom, true), "legacy hardcore absorption pixels are checked");
        BufferedImage original = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        BufferedImage rescaled = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        original.setRGB(0, 0, 0xFFFF0000);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) rescaled.setRGB(x, y, 0xFFFF0000);
        check(!different(original, rescaled, false), "identical upscaled hearts are not treated as custom designs");
        rescaled.setRGB(1, 1, 0xFF00FF00);
        check(different(original, rescaled, false), "modern custom heart pixels are detected");
    }
    private static boolean different(BufferedImage first, BufferedImage second, boolean atlas) throws IOException {
        ByteArrayOutputStream a = new ByteArrayOutputStream(), b = new ByteArrayOutputStream();
        ImageIO.write(first, "png", a); ImageIO.write(second, "png", b);
        return HeartTextureCheck.differs(new java.io.ByteArrayInputStream(a.toByteArray()), new java.io.ByteArrayInputStream(b.toByteArray()), atlas);
    }
    private static void reject(String json) {
        try { HeartCatalog.parse(json); throw new AssertionError("invalid catalog accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
