package com.github.catvod.net;

import android.annotation.SuppressLint;

import androidx.collection.ArrayMap;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 简化版 OkHttp（对齐 fongmi catvod 的公开 API 签名，供 spider jar 调用）：
 * 去掉拦截器/代理/DoH 依赖，保留 trust-all SSL 与 hosts 映射 DNS。
 */
public class OkHttp {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private OkHttpClient client;
    private OkHttpClient player;
    private OkDns dns;

    public static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static OkDns dns() {
        if (get().dns != null) return get().dns;
        return get().dns = new OkDns();
    }

    public static synchronized OkHttpClient client() {
        if (get().client != null) return get().client;
        return get().client = getBuilder().build();
    }

    public static synchronized OkHttpClient player() {
        if (get().player != null) return get().player;
        return get().player = getBuilder().build();
    }

    public static OkHttpClient client(long timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).build();
    }

    public static OkHttpClient noRedirect() {
        return noRedirect(TIMEOUT);
    }

    public static OkHttpClient noRedirect(long timeout) {
        return client().newBuilder().connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build();
    }

    public static OkHttpClient client(boolean redirect, long timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    public static String string(String url) {
        if (!url.startsWith("http")) return "";
        try (Response res = newCall(url).execute()) {
            return res.body().string();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String string(String url, Map<String, String> headers) {
        if (!url.startsWith("http")) return "";
        try (Response res = newCall(url, headers).execute()) {
            return res.body().string();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static Call newCall(String url) {
        return client().newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(String url, String tag) {
        return client().newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    public static Call newCall(OkHttpClient client, String url, String tag) {
        return client.newCall(new Request.Builder().url(url).tag(tag).build());
    }

    public static Call newCall(String url, Map<String, String> headers) {
        return client().newCall(new Request.Builder().url(url).headers(Headers.of(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, ArrayMap<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(Headers.of(headers)).build());
    }

    public static Call newCall(String url, Map<String, String> headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(Headers.of(headers)).post(body).build());
    }

    public static Call newCall(String url, RequestBody body, String tag) {
        return client().newCall(new Request.Builder().url(url).post(body).tag(tag).build());
    }

    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    public static void cancel(String tag) {
        cancel(client(), tag);
    }

    public static void cancel(OkHttpClient client, String tag) {
        for (Call call : client.dispatcher().queuedCalls()) if (tag.equals(call.request().tag())) call.cancel();
        for (Call call : client.dispatcher().runningCalls()) if (tag.equals(call.request().tag())) call.cancel();
    }

    public static void cancelAll() {
        cancelAll(client());
    }

    public static void cancelAll(OkHttpClient client) {
        client.dispatcher().cancelAll();
    }

    public static FormBody toBody(ArrayMap<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) body.add(entry.getKey(), entry.getValue());
        return body.build();
    }

    private static HttpUrl buildUrl(String url, ArrayMap<String, String> params) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) builder.addQueryParameter(entry.getKey(), entry.getValue());
        return builder.build();
    }

    private static OkHttpClient.Builder getBuilder() {
        // 跳转必须跟随：fongmi 的 client 虽然是 followRedirects(false)，但它挂了 ProxyRedirectInterceptor
        // 自己逐跳重发 30x（最多 20 跳）。我们没移植那个拦截器，所以直接交给 OkHttp 跟随；
        // 否则蜘蛛拿 301/302 的空响应体去解析，就会出现「jar 调通了但列表永远空」。
        return new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
            .dns(dns())
            .hostnameVerifier((hostname, session) -> true)
            .sslSocketFactory(getSSLContext().getSocketFactory(), trustAllCertificates())
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor(new ResponseFixInterceptor());
    }

    /**
     * 对齐 fongmi ResponseInterceptor 的两件事：
     * ① 服务器回 Content-Encoding: deflate（raw deflate，OkHttp 只认 gzip）时自己解压，否则蜘蛛拿到乱码正文；
     * ② DEBUG 下打印蜘蛛发出的每个请求与响应体积，定位「请求失败还是解析失败」。
     */
    private static class ResponseFixInterceptor implements okhttp3.Interceptor {
        @androidx.annotation.NonNull
        @Override
        public Response intercept(@androidx.annotation.NonNull Chain chain) throws java.io.IOException {
            Request request = chain.request();
            Response response = chain.proceed(request);
            String encoding = response.header("Content-Encoding");
            if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
                java.io.InputStream is = new java.util.zip.InflaterInputStream(
                    response.body().byteStream(), new java.util.zip.Inflater(true));
                byte[] bytes = readAll(is);
                response = response.newBuilder()
                    .removeHeader("Content-Encoding")
                    .removeHeader("Content-Length")
                    .body(okhttp3.ResponseBody.create(bytes, response.body().contentType()))
                    .build();
            }
            if (com.ican.tvplay.BuildConfig.DEBUG) {
                int code = response.code();
                android.util.Log.d("CatvodNet", "req " + request.method() + " " + request.url() + " -> " + code +
                    " len=" + response.body().contentLength() + " enc=" + encoding);
            }
            return response;
        }

        private static byte[] readAll(java.io.InputStream is) throws java.io.IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static SSLContext getSSLContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustAllCertificates()}, new SecureRandom());
            return context;
        } catch (Throwable e) {
            return null;
        }
    }

    @SuppressLint({"TrustAllX509TrustManager", "CustomX509TrustManager"})
    private static X509TrustManager trustAllCertificates() {
        return new X509TrustManager() {
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
        };
    }

    public void clear() {
        cancelAll();
        dns().clear();
    }

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }
}
