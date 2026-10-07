package com.github.catvod.net;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化版 OkDns（对齐 fongmi catvod 的公开 API）：
 * 仅保留 hosts 静态映射（"host=ip"）+ 系统 DNS 兜底，不含 DoH。
 */
public class OkDns implements okhttp3.Dns {

    private final ConcurrentHashMap<String, String> map;

    public OkDns() {
        this.map = new ConcurrentHashMap<>();
    }

    public void clear() {
        map.clear();
    }

    public void addAll(List<String> hosts) {
        for (String host : hosts) {
            if (host == null) continue;
            String[] splits = host.split("=", 2);
            if (splits.length == 2) map.put(splits[0].trim(), splits[1].trim());
        }
    }

    private String get(String hostname) {
        String target = map.get(hostname);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (target == null && hostname.contains(entry.getKey())) target = entry.getValue();
        }
        return target == null ? hostname : target;
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        return SYSTEM.lookup(get(hostname));
    }
}
