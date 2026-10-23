package pl.slogerski.heartistica;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

final class CatalogService implements AutoCloseable {
    interface Fetcher { CatalogHttp.Response get(URI uri, String etag, int limit) throws IOException; }
    private static final URI ENDPOINT = URI.create("https://raw.githubusercontent.com/Slogerski/Heartistica/main/remote/catalog.json");
    private static final int JSON_LIMIT = 262_144;
    private static final System.Logger LOGGER = System.getLogger("Heartistica");
    private final Path directory;
    private final Consumer<CatalogImages.Atlas> installer;
    private final Fetcher fetcher;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Heartistica Catalog");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> pending = new HashSet<>();
    private final Map<String, Long> retryAt = new HashMap<>();
    private final Set<String> ready = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, Long> unsupported = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> previews = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, Long> loadedAt = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<HeartCatalog.Theme, String> assetKeys = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile HeartCatalog catalog;
    private volatile String status = "heartistica.catalog.loading";
    private String etag = "";
    private int failures;
    private boolean started;
    private boolean cacheWarning;
    private String eventKey = "", eventSelection = "";
    private HeartCatalog selectionCatalog;
    private String selectionManual = "", selection = "";
    private boolean selectionEvents;
    private long selectionSecond;
    private volatile boolean modifiedResourcePack;
    private Object resourcePackToken;

    synchronized void checkResourcePack(Object token, java.util.function.Supplier<Boolean> check) {
        if (worker.isShutdown() || resourcePackToken == token) return;
        resourcePackToken = token;
        modifiedResourcePack = true;
        selectionCatalog = null;
        execute(() -> {
            boolean modified = true;
            try { modified = check.get(); }
            catch (RuntimeException | LinkageError error) { warn("Could not inspect resource pack hearts", error); }
            synchronized (this) {
                if (resourcePackToken == token) {
                    modifiedResourcePack = modified;
                    selectionCatalog = null;
                }
            }
        });
    }

    String eventsLabel(HeartisticaConfig config) {
        String state = UiText.get(config.language, eventsEnabled(config) ? "heartistica.on" : "heartistica.off");
        if (config.eventHeartsMode == HeartisticaConfig.EventHeartsMode.AUTO)
            state = UiText.get(config.language, "heartistica.catalog.auto", state);
        return UiText.get(config.language, "heartistica.catalog.events") + ": " + state;
    }

    boolean eventsEnabled(HeartisticaConfig config) { return config.eventHeartsEnabled(modifiedResourcePack); }
    void toggleEvents(HeartisticaConfig config) { config.setEventHearts(!eventsEnabled(config)); }

    CatalogService(Path directory, String fallback, Consumer<CatalogImages.Atlas> installer) {
        this(directory, fallback, installer, CatalogHttp::get);
    }

    CatalogService(Path directory, String fallback, Consumer<CatalogImages.Atlas> installer, Fetcher fetcher) {
        this.directory = directory.toAbsolutePath().normalize();
        try { this.catalog = HeartCatalog.parse(fallback); }
        catch (RuntimeException error) {
            this.catalog = HeartCatalog.parse("{\"schemaVersion\":1,\"categories\":{\"Event\":[],\"Popular\":[\"resource_pack\",\"heartistica\"]}}");
            warn("Invalid bundled catalog; using built-in hearts", error);
        }
        this.installer = installer;
        this.fetcher = fetcher;
    }

    synchronized void start() {
        if (started || worker.isShutdown()) return;
        started = true;
        execute(() -> {
            try {
                byte[] cached = readCache(directory.resolve("catalog.json"), JSON_LIMIT);
                if (cached != null) catalog = HeartCatalog.parse(new String(cached, StandardCharsets.UTF_8));
                byte[] meta = cached == null ? null : readCache(directory.resolve("catalog-meta.json"), 4096);
                if (meta != null) etag = JsonParser.parseString(new String(meta, StandardCharsets.UTF_8))
                        .getAsJsonObject().get("etag").getAsString();
            } catch (RuntimeException error) { warnCache(error); }
            refresh();
        });
    }

    HeartCatalog catalog() { return catalog; }
    String status() { return status; }
    private String assetKey(HeartCatalog.Theme theme) {
        return assetKeys.computeIfAbsent(theme, value -> value.id() + ":" + value.images() + ":" + value.layout());
    }
    boolean needsUpdate(String id) {
        HeartCatalog.Theme theme = catalog.themes.get(id);
        return theme != null && unsupported.containsKey(assetKey(theme));
    }
    String preview(String id) {
        HeartCatalog.Theme theme = catalog.themes.get(id);
        return theme != null && previews.contains(assetKey(theme)) ? id : "heartistica";
    }
    synchronized void installationFailed(String id) {
        ready.removeIf(key -> key.startsWith(id + ":"));
        HeartCatalog.Theme theme = catalog.themes.get(id);
        if (theme != null) retryAt.put(assetKey(theme), System.currentTimeMillis() + 60_000);
    }

    private void refresh() {
        long delay = 600;
        try {
            var response = fetcher.get(ENDPOINT, etag, JSON_LIMIT);
            if (response.status() == 200) {
                HeartCatalog next = HeartCatalog.parse(new String(response.body(), StandardCharsets.UTF_8));
                catalog = next;
                etag = response.etag();
                JsonObject meta = new JsonObject();
                meta.addProperty("etag", etag);
                if (writeCache(directory.resolve("catalog.json"), response.body()))
                    writeCache(directory.resolve("catalog-meta.json"), meta.toString().getBytes(StandardCharsets.UTF_8));
            } else if (response.status() != 304 || etag.isEmpty()) throw new IOException("Catalog HTTP " + response.status());
            status = "";
            failures = 0;
        } catch (IOException | RuntimeException | LinkageError error) {
            if (worker.isShutdown()) return;
            status = "heartistica.catalog.offline";
            delay = Math.min(600, 60L << Math.min(4, failures++));
            if (failures == 1) warn("Could not refresh heart catalog; using local data", error);
        } finally {
            if (!worker.isShutdown()) scan(catalog.themes.keySet().iterator());
            schedule(this::refresh, delay);
        }
    }

    private void scan(java.util.Iterator<String> themes) {
        if (worker.isShutdown() || !themes.hasNext()) return;
        load(themes.next(), false);
        execute(() -> scan(themes));
    }

    void request(String id) { load(id, true); }

    private synchronized void load(String id, boolean install) {
        if (worker.isShutdown()) return;
        HeartCatalog.Theme theme = catalog.themes.get(id);
        if (theme == null || theme.images().isEmpty()) return;
        String key = assetKey(theme);
        boolean immutable = theme.images().values().stream().allMatch(asset -> !asset.hash().isEmpty());
        if (unsupported.containsKey(key) && (immutable || System.currentTimeMillis() - unsupported.get(key) < 600_000)) return;
        if (!install && loadedAt.containsKey(key) && (immutable || System.currentTimeMillis() - loadedAt.get(key) < 600_000)) return;
        if (ready.contains(key) && (immutable || System.currentTimeMillis() - loadedAt.getOrDefault(key, 0L) < 600_000)
                || pending.contains(key) || System.currentTimeMillis() < retryAt.getOrDefault(key, 0L)) return;
        pending.add(key);
        if (!execute(() -> {
            try {
                Map<String, byte[]> images = new HashMap<>();
                for (var entry : theme.images().entrySet()) images.put(entry.getKey(), image(entry.getValue()));
                CatalogImages.Atlas atlas = CatalogImages.atlas(theme, images);
                if (worker.isShutdown() || !theme.equals(catalog.themes.get(id))) return;
                loadedAt.put(key, System.currentTimeMillis());
                if (install) ready.add(key);
                synchronized (this) { unsupported.remove(key); previews.remove(key); selectionCatalog = null; }
                if (install) installer.accept(atlas);
                synchronized (this) { retryAt.remove(key); }
            } catch (CatalogImages.UnsupportedDimensions error) {
                if (worker.isShutdown() || !theme.equals(catalog.themes.get(id))) return;
                synchronized (this) {
                    unsupported.put(key, System.currentTimeMillis());
                    ready.remove(key);
                    loadedAt.remove(key);
                    selectionCatalog = null;
                }
                try {
                    CatalogImages.Atlas preview = error.preview(theme);
                    if (preview != null) { installer.accept(preview); previews.add(key); }
                } catch (IOException | RuntimeException | LinkageError previewError) {
                    warn("Could not create catalog heart preview " + id, previewError);
                }
            } catch (IOException | RuntimeException | LinkageError error) {
                ready.remove(key);
                loadedAt.remove(key);
                if (worker.isShutdown()) return;
                synchronized (this) { retryAt.put(key, System.currentTimeMillis() + 60_000); }
                warn("Could not load catalog heart " + id, error);
            } finally { synchronized (this) { pending.remove(key); } }
        })) pending.remove(key);
    }

    private boolean execute(Runnable task) {
        return schedule(task, 0);
    }

    private boolean schedule(Runnable task, long delaySeconds) {
        if (worker.isShutdown()) return false;
        try {
            worker.schedule(task, delaySeconds, TimeUnit.SECONDS);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException error) {
            if (!worker.isShutdown()) warn("Could not schedule catalog task", error);
            return false;
        }
    }

    private byte[] image(HeartCatalog.Asset asset) throws IOException {
        String key = CatalogImages.sha((asset.url() + "|" + asset.hash()).getBytes(StandardCharsets.UTF_8), false);
        Path file = directory.resolve("images").resolve(key + ".png");
        Path metadata = directory.resolve("images").resolve(key + ".etag");
        byte[] cached = readCache(file, CatalogImages.MAX_BYTES);
        if (cached != null && CatalogImages.matches(cached, asset.hash())) {
            try {
                CatalogImages.decode(cached);
            } catch (CatalogImages.UnsupportedDimensions error) { throw error; }
            catch (IOException | RuntimeException error) { warnCache(error); cached = null; }
            if (cached != null) {
                if (!asset.hash().isEmpty()) return cached;
                try {
                    if (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < 600_000) return cached;
                } catch (IOException | RuntimeException error) { warnCache(error); }
            }
        } else cached = null;
        try {
            byte[] tag = cached == null ? null : readCache(metadata, 512);
            String etag = tag == null ? "" : new String(tag, StandardCharsets.UTF_8);
            var response = fetcher.get(asset.url(), etag, CatalogImages.MAX_BYTES);
            if (response.status() == 304 && cached != null) {
                try { Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis())); }
                catch (IOException | RuntimeException error) { warnCache(error); }
                return cached;
            }
            if (response.status() != 200 || !CatalogImages.matches(response.body(), asset.hash())) throw new IOException("Invalid catalog image response or SHA");
            CatalogImages.decode(response.body());
            if (writeCache(file, response.body())) writeCache(metadata, response.etag().getBytes(StandardCharsets.UTF_8));
            try { trimImages(file); }
            catch (IOException | RuntimeException error) { warnCache(error); }
            return response.body();
        } catch (CatalogImages.UnsupportedDimensions error) {
            throw error;
        } catch (IOException | RuntimeException | LinkageError error) {
            if (cached != null) return cached;
            throw new IOException("Catalog image unavailable", error);
        }
    }

    synchronized String selected(HeartisticaConfig config) {
        long second = System.currentTimeMillis() / 1000;
        if (selectionCatalog == catalog && selectionManual.equals(config.heartStyle)
                && selectionEvents == eventsEnabled(config) && selectionSecond == second) return selection;
        selectionCatalog = catalog;
        selectionManual = config.heartStyle;
        selectionEvents = eventsEnabled(config);
        selectionSecond = second;
        String manual = needsUpdate(config.heartStyle) ? "resource_pack" : config.heartStyle;
        if (!selectionEvents || !catalog.eventActive(Instant.now())) return selection = manual;
        var candidates = catalog.category("event").stream().filter(id -> !needsUpdate(id)).toList();
        if (candidates.isEmpty()) return selection = manual;
        String key = catalog.eventId + candidates;
        if (!key.equals(eventKey)) {
            eventKey = key;
            eventSelection = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        return selection = eventSelection;
    }

    static void select(HeartisticaConfig config, String id) {
        config.heartStyle = id;
    }
    static void restore(HeartisticaConfig config) { select(config, "resource_pack"); }

    private byte[] readCache(Path path, int limit) {
        try { return read(path, limit); }
        catch (IOException | RuntimeException error) { warnCache(error); return null; }
    }

    private boolean writeCache(Path path, byte[] bytes) {
        try { write(path, bytes); return true; }
        catch (IOException | RuntimeException error) { warnCache(error); return false; }
    }

    private void warnCache(Throwable error) {
        if (cacheWarning) return;
        cacheWarning = true;
        warn("Could not access catalog cache; continuing without persistent cache", error);
    }

    private static byte[] read(Path path, int limit) throws IOException {
        if (!Files.isRegularFile(path)) return null;
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Catalog cache too large");
            return bytes;
        }
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "catalog-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private void trimImages(Path current) throws IOException {
        Path images = directory.resolve("images");
        try (var stream = Files.list(images)) {
            var files = stream.filter(path -> path.getFileName().toString().matches("[0-9a-f]{64}\\.png"))
                    .sorted(java.util.Comparator.comparingLong(path -> {
                        try { return Files.getLastModifiedTime(path).toMillis(); }
                        catch (IOException error) { return 0L; }
                    })).toList();
            long total = 0;
            for (Path path : files) total += Files.size(path);
            int count = files.size();
            for (Path path : files) {
                if (total <= 64L * 1024 * 1024 && count <= 1024) break;
                if (path.equals(current) || !path.toAbsolutePath().normalize().getParent().equals(images)) continue;
                long size = Files.size(path);
                Files.deleteIfExists(path);
                Files.deleteIfExists(path.resolveSibling(path.getFileName().toString().replace(".png", ".etag")));
                total -= size;
                count--;
            }
        }
    }

    private static void warn(String message, Throwable error) { LOGGER.log(System.Logger.Level.WARNING, message, error); }
    @Override public void close() {
        worker.shutdownNow();
        synchronized (this) { pending.clear(); }
        try { worker.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
