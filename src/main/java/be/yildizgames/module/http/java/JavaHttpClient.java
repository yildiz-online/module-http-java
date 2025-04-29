package be.yildizgames.module.http.java;

import be.yildizgames.module.http.Header;
import be.yildizgames.module.http.Headers;
import be.yildizgames.module.http.HttpCode;
import be.yildizgames.module.http.HttpClient;
import be.yildizgames.module.http.HttpResponse;
import be.yildizgames.module.http.HttpTransferListener;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class JavaHttpClient implements HttpClient {

    /**
     * Logger.
     */
    private static final System.Logger LOGGER = System.getLogger(JavaHttpClient.class.toString());

    public static final String ERROR_HTTP_CONTENT_RETRIEVE = "error.http.content.retrieve";

    /**
     * Buffer size.
     */
    private static final int BUFFER_SIZE = 1024;

    private final List<HttpTransferListener> listeners = new ArrayList<>();

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

    private final int timeout;

    public JavaHttpClient(int timeout) {
        this.timeout = timeout;
    }

    public JavaHttpClient() {
        this(-1);
    }

    public final String getText(final URI uri) {
        return this.getStream(uri, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    public final String getText(final String uri) {
        return this.getText(URI.create(uri));
    }

    public final HttpResponse<String> getTextResponse(final URI uri) {
        return this.getStreamResponse(uri, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    public final HttpResponse<String> getTextResponse(final String uri) {
        return this.getTextResponse(URI.create(uri));
    }

    public final <T> T getObject(URI uri, Class<T> clazz) {
        try {
            var content = this.getText(uri);
            var mapper = new ObjectMapper();
            return mapper.readValue(content,clazz);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public final <T> T getObject(String uri, Class<T> clazz) {
        return this.getObject(URI.create(uri), clazz);
    }

    public final InputStream getInputStream(final URI uri) {
        return this.getStream(uri, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
    }

    public final InputStream getInputStream(final String uri) {
        return this.getInputStream(URI.create(uri));
    }

    public final Reader getReader(final URI uri) {
        return new InputStreamReader(this.getStream(uri, java.net.http.HttpResponse.BodyHandlers.ofInputStream()));
    }

    public final Reader getReader(final String uri) {
        return this.getReader(URI.create(uri));
    }

    public final void sendFile(URI uri, Path origin, String mime) {
        try {
            var request = java.net.http.HttpRequest.newBuilder()
                    .header("Content-Type", mime)
                    .uri(uri)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofFile(origin))
                    .build();
            var response = this.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (HttpCode.isError(response.statusCode())) {
                LOGGER.log(System.Logger.Level.ERROR, "Error sending content: {0} status: {1}", uri, response.statusCode());
                throw new IllegalStateException("error.http.content.send");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("error.http.file.send", e);
        } catch (Exception e) {
            throw new IllegalStateException("error.http.file.send", e);
        }
    }

    public final void receiveFile(URI uri, Path destination) {
        try {
            Files.createDirectories(destination.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("error.file.create", e);
        }
        try (
                var bis = new BufferedInputStream(this.getStream(uri, java.net.http.HttpResponse.BodyHandlers.ofInputStream()));
                var bos = new BufferedOutputStream(Files.newOutputStream(destination))) {
            var buf = new byte[BUFFER_SIZE];
            int len;
            long currentlyTransferred = 0;
            while ((len = bis.read(buf)) > 0) {
                bos.write(buf, 0, len);
                currentlyTransferred += len;
                for(HttpTransferListener l : this.listeners) {
                    l.received(uri, len, currentlyTransferred);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("error.http.file.retrieve", e);
        }
    }

    public final void addTransferListener(HttpTransferListener l) {
        this.listeners.add(l);
    }

    /**
     * Call to an HTTP get method, return the stream generated by the response.
     *
     * @param url Url to request.
     * @return The stream for the request url.
     * @throws IllegalStateException If an exception occurs.
     */
    private <T> T getStream(final URI url, java.net.http.HttpResponse.BodyHandler<T> bodyHandler){
        java.net.http.HttpRequest request;
        if(this.timeout == -1) {
            request = java.net.http.HttpRequest.newBuilder(url).build();
        } else {
            request = java.net.http.HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(timeout)).build();
        }
        try {
            var response = this.client.send(request, bodyHandler);
            if (HttpCode.isError(response.statusCode())) {
                LOGGER.log(System.Logger.Level.ERROR, "Error retrieving content: {0} status: {1}", url, response.statusCode());
                throw new IllegalStateException(ERROR_HTTP_CONTENT_RETRIEVE);
            }
            return response.body();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error retrieving content: {0}", url, e);
            throw new IllegalStateException(ERROR_HTTP_CONTENT_RETRIEVE);
        } catch (InterruptedException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error retrieving content: {0}", url, e);
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
            var headers = new ArrayList<Header>();
            for(var h:  response.headers().map().entrySet()) {
                headers.add(new Header(h.getKey(), h.getValue()));
            }
            return new HttpResponse<>(response.statusCode(), response.body(), new Headers(headers));
        } catch (Throwable e) {
            return new HttpResponse<>(e);
        }
    }
}
