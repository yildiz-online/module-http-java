package be.yildizgames.module.http.java;

import be.yildizgames.module.http.HttpClient;
import be.yildizgames.module.http.HttpClientBuilder;

public class JavaHttpClientBuilder implements HttpClientBuilder {

    @Override
    public final HttpClient buildHttpClient() {
        return new JavaHttpClient();
    }

    @Override
    public final HttpClient buildHttpClient(int timeout) {
        return new JavaHttpClient(timeout);
    }
}
