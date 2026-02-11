package be.yildizgames.module.http.java;

import be.yildizgames.module.http.Headers;
import be.yildizgames.module.http.HttpClient;
import be.yildizgames.module.http.HttpCode;
import be.yildizgames.module.http.HttpResponse;
import be.yildizgames.module.http.HttpTransferListener;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;

public class JavaHttpClient implements HttpClient {

    public static final String ERROR_HTTP_CONTENT_RETRIEVE = "error.http.content.retrieve";

    /**
     * Buffer size.
     */
    private static final int BUFFER_SIZE = 1024;

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

    private final int timeout;

    public JavaHttpClient(int timeout) {
        this.timeout = timeout;
    }

    public JavaHttpClient() {
        this(-1);
    }

    @Override
    public final HttpResponse<String> getText(final String uri) {
        return this.getStreamResponse(URI.create(uri), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public final HttpResponse<String> postText(final String uri, final String content, final String mime) {
        try {
            var request = java.net.http.HttpRequest.newBuilder()
                    .header("Content-Type", mime)
                    .uri(URI.create(uri))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(content))
                    .build();
            var response = this.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return new HttpResponse<>(response.statusCode(), response.body(), Headers.fromMap(response.headers().map()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HttpResponse<>(e);
        } catch (Exception e) {
            return new HttpResponse<>(e);
        }
    }

    @Override
    public final HttpResponse<InputStream> getInputStream(final String uri) {
        return this.getStreamResponse(URI.create(uri), java.net.http.HttpResponse.BodyHandlers.ofInputStream());
    }

    @Override
    public final HttpResponse<Path> getFile(final String uri, final Path destination, final HttpTransferListener listener) {
        try {
            Files.createDirectories(destination.getParent());
        } catch (IOException e) {
            return new HttpResponse<>(e);
        }
        try (
                var content = this.getStream(URI.create(uri), java.net.http.HttpResponse.BodyHandlers.ofInputStream());
                var bis = new BufferedInputStream(content.content);
                var bos = new BufferedOutputStream(Files.newOutputStream(destination))) {
            var buf = new byte[BUFFER_SIZE];
            int len;
            long currentlyTransferred = 0;
            while ((len = bis.read(buf)) > 0) {
                bos.write(buf, 0, len);
                currentlyTransferred += len;
                listener.received(uri, len, currentlyTransferred);
            }
            return new HttpResponse<>(content.code, destination, content.headers());
        } catch (Exception e) {
            return new HttpResponse<>(e);
        }
    }

    @Override
    public final HttpResponse<String> postFile(final String uri, final Path file, final String mime) {
        try {
            var request = java.net.http.HttpRequest.newBuilder()
                    .header("Content-Type", mime)
                    .uri(URI.create(uri))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofFile(file))
                    .build();
            var response = this.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return new HttpResponse<>(response.statusCode(), response.body(), Headers.fromMap(response.headers().map()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HttpResponse<>(e);
        } catch (Exception e) {
            return new HttpResponse<>(e);
        }
    }

    @Override
    public final HttpResponse<String> postInputStream(final String to, final Supplier<InputStream> content, final String mime) {
        try {
            var request = java.net.http.HttpRequest.newBuilder()
                    .header("Content-Type", mime)
                    .uri(URI.create(to))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofInputStream(content))
                    .build();
            var response = this.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return new HttpResponse<>(response.statusCode(), response.body(), Headers.fromMap(response.headers().map()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HttpResponse<>(e);
        } catch (Exception e) {
            return new HttpResponse<>(e);
        }
    }

    /**
     * Call to an HTTP get method, return the stream generated by the response.
     *
     * @param url Url to request.
     * @return The stream for the request url.
     * @throws IllegalStateException If an exception occurs.
     */
    private <T extends AutoCloseable> HttpContent<T> getStream(final URI url, java.net.http.HttpResponse.BodyHandler<T> bodyHandler){
        java.net.http.HttpRequest request;
        if(this.timeout == -1) {
            request = java.net.http.HttpRequest.newBuilder(url).build();
        } else {
            request = java.net.http.HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(timeout)).build();
        }
        try {
            var response = this.client.send(request, bodyHandler);
            if (HttpCode.isError(response.statusCode())) {
                throw new IllegalStateException(ERROR_HTTP_CONTENT_RETRIEVE);
            }
            return new HttpContent<>(response.body(), response.statusCode(), Headers.fromMap(response.headers().map()));
        } catch (IOException e) {
            throw new IllegalStateException(ERROR_HTTP_CONTENT_RETRIEVE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ERROR_HTTP_CONTENT_RETRIEVE);
        }
    }

    private <T> be.yildizgames.module.http.HttpResponse<T> getStreamResponse(final URI url, java.net.http.HttpResponse.BodyHandler<T> bodyHandler){
        java.net.http.HttpRequest request;
        if(this.timeout == -1) {
            request = java.net.http.HttpRequest.newBuilder(url).build();
        } else {
            request = java.net.http.HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(timeout)).build();
        }
        try {
            var response = this.client.send(request, bodyHandler);
            return new HttpResponse<>(response.statusCode(), response.body(), Headers.fromMap(response.headers().map()));
        } catch (Throwable e) {
            return new HttpResponse<>(e);
        }
    }

    private record HttpContent<T extends AutoCloseable>(T content, int code, Headers headers) implements AutoCloseable {

        @Override
        public void close() throws Exception {
            this.content.close();
        }
    }
}
