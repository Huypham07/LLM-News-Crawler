package com.vdt.crawler.frontier_service.utils;

import org.apache.http.conn.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DnsResolverWithCache implements DnsResolver {
    public static final DnsResolverWithCache INSTANCE = new DnsResolverWithCache();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis = 900000; // 15 mins

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(host);

        if (cached != null && (now - cached.timestamp) < ttlMillis) {
            return cached.addresses;
        }

        InetAddress[] addresses = InetAddress.getAllByName(host);
        cache.put(host, new CacheEntry(addresses, now));
        return addresses;
    }

    private static class CacheEntry {
        final InetAddress[] addresses;
        final long timestamp;

        CacheEntry(InetAddress[] addresses, long timestamp) {
            this.addresses = addresses;
            this.timestamp = timestamp;
        }
    }
}
