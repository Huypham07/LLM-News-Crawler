package com.vdt.crawler.frontier_service.service.robotstxt;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import com.vdt.crawler.frontier_service.exception.PageBiggerThanMaxSizeException;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.conn.DnsResolver;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageFetcher {
    private static final Logger logger = LoggerFactory.getLogger(PageFetcher.class);
    protected final Object mutex = new Object();

    protected PoolingHttpClientConnectionManager connectionManager;
    protected CloseableHttpClient httpClient;
    private long lastFetchTime = 0;
    private final int politenessDelay;
    private final int maxDownloadSize = 500 * 1024; // 500KB
    protected IdleConnectionMonitorThread connectionMonitorThread = null;

    public PageFetcher(int timeoutMillis, int politenessDelayMillis, DnsResolver dnsResolver) {
        this.politenessDelay = politenessDelayMillis;
        RequestConfig requestConfig = RequestConfig.custom()
                .setExpectContinueEnabled(false)
                .setRedirectsEnabled(false)
                .setSocketTimeout(timeoutMillis)
                .setConnectTimeout(timeoutMillis)
                .build();

        RegistryBuilder<ConnectionSocketFactory> connRegistryBuilder = RegistryBuilder.create();
        connRegistryBuilder.register("http", PlainConnectionSocketFactory.INSTANCE);

        try {
            SSLConnectionSocketFactory sslFactory =
                    new SSLConnectionSocketFactory(SSLContexts.custom().loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true).build(), NoopHostnameVerifier.INSTANCE);
            connRegistryBuilder.register("https", sslFactory);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | RuntimeException e) {
            logger.warn("Exception thrown while trying to register https");
            logger.debug("Stacktrace", e);
        }

        Registry<ConnectionSocketFactory> connRegistry = connRegistryBuilder.build();
        connectionManager =
                new PoolingHttpClientConnectionManager(connRegistry, dnsResolver);
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(5);

        this.httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .setUserAgent("SimpleRobotsFetcher/1.0")
                .build();

        if (connectionMonitorThread == null) {
            connectionMonitorThread = new IdleConnectionMonitorThread(connectionManager);
        }
        connectionMonitorThread.start();
    }

    public PageFetchResult fetchPage(String robotsTxtUrl)
            throws InterruptedException, IOException, PageBiggerThanMaxSizeException {
        PageFetchResult fetchResult = new PageFetchResult();
        synchronized (mutex) {
            long now = new Date().getTime();
            if ((now - lastFetchTime) < politenessDelay) {
                Thread.sleep(politenessDelay - (now - lastFetchTime));
            }
            lastFetchTime = new Date().getTime();
        }
        HttpGet request = new HttpGet(robotsTxtUrl);
        try {
            CloseableHttpResponse response = httpClient.execute(request);
            fetchResult.setEntity(response.getEntity());
            fetchResult.setResponseHeaders(response.getAllHeaders());

            // Setting HttpStatus
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200 ) {
                fetchResult.setFetchedUrl(robotsTxtUrl);

                // Checking maximum size
                if (fetchResult.getEntity() != null) {
                    long size = fetchResult.getEntity().getContentLength();
                    if (size == -1) {
                        Header length = response.getLastHeader(HttpHeaders.CONTENT_LENGTH);
                        if (length == null) {
                            length = response.getLastHeader("Content-length");
                        }
                        if (length != null) {
                            size = Integer.parseInt(length.getValue());
                        }
                    }
                    if (size > maxDownloadSize) {
                        response.close();
                        throw new PageBiggerThanMaxSizeException(size);
                    }
                }
            }

            fetchResult.setStatusCode(statusCode);
            return fetchResult;

        } finally { // occurs also with thrown exceptions
            if (fetchResult.getEntity() == null) {
                request.abort();
            }
        }
    }

    public synchronized void shutDown() {
        if (connectionMonitorThread != null) {
            connectionManager.shutdown();
            connectionMonitorThread.shutdown();
        }
    }
}