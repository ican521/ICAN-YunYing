package com.github.catvod;

/**
 * spider jar 读取本地代理端口的宿主类（fongmi catvod 模块同款）。
 * jar 通过 Proxy.getUrl() 生成 127.0.0.1:port/proxy 地址，
 * DexClassLoader 父委托会优先命中本类，端口由 LocalProxyServer 启动后写入。
 */
public class Proxy {

    private static int port = -1;

    public static void set(int port) {
        Proxy.port = port;
    }

    public static int getPort() {
        return port;
    }

    public static String getUrl(boolean local) {
        return "http://127.0.0.1:" + getPort() + "/proxy";
    }
}
