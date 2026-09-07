package ru.infereco.demo.barista.pto;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class PtoHttp {

    private PtoHttp() {
    }

    static HttpClient client() {
        try {
            TrustManager[] trust = {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust, new SecureRandom());
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .sslContext(context)
                    .build();
        } catch (Exception ex) {
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        }
    }
}
