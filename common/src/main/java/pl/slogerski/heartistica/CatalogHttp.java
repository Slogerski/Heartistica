package pl.slogerski.heartistica;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import javax.net.ssl.HttpsURLConnection;

final class CatalogHttp {
    record Response(int status, String etag, byte[] body) {
        Response { etag = cleanEtag(etag); }
    }

    private static String cleanEtag(String etag) {
        return etag == null || etag.length() > 512 || etag.chars().anyMatch(c -> c < 32 || c == 127) ? "" : etag;
    }

    static Response get(URI uri, String etag, int limit) throws IOException {
        HeartCatalog.imageUri(uri.toString());
        etag = cleanEtag(etag);
        if (limit < 1 || limit > CatalogImages.MAX_BYTES) throw new IOException("Invalid catalog response limit");
        if (Thread.currentThread().isInterrupted()) throw new IOException("Catalog request interrupted");
        try {
            return Modern.get(uri, etag, limit);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Catalog request interrupted", error);
        } catch (IOException | RuntimeException | LinkageError error) {
            return legacy(uri, etag, limit);
        }
    }

    private static Response legacy(URI uri, String etag, int limit) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "Heartistica/1.0");
        if (!etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);
        try {
            int status = connection.getResponseCode();
            if (status != 200) return new Response(status, "", new byte[0]);
            if (connection.getContentLengthLong() > limit) throw new IOException("Catalog response too large");
            try (var stream = connection.getInputStream()) {
                byte[] bytes = stream.readNBytes(limit + 1);
                if (bytes.length > limit) throw new IOException("Catalog response too large");
                String tag = connection.getHeaderField("ETag");
                return new Response(status, tag == null ? "" : tag, bytes);
            }
        } finally { connection.disconnect(); }
    }

    private static final class Modern {
        private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        static Response get(URI uri, String etag, int limit) throws IOException, InterruptedException {
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("User-Agent", "Heartistica/1.0").GET();
            if (!etag.isEmpty()) builder.header("If-None-Match", etag);
            var response = CLIENT.send(builder.build(), info -> new LimitedBody(limit));
            return new Response(response.statusCode(), response.headers().firstValue("ETag").orElse(""), response.body());
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Catalog response too large"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
