import be.yildizgames.module.http.HttpClientBuilder;
import be.yildizgames.module.http.java.JavaHttpClientBuilder;

module be.yildizgames.module.http.java {

    requires be.yildizgames.module.http;
    requires com.fasterxml.jackson.databind;
    requires java.net.http;

    provides HttpClientBuilder with JavaHttpClientBuilder;
}