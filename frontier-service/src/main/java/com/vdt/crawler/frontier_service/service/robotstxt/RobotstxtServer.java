/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vdt.crawler.frontier_service.service.robotstxt;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.vdt.crawler.frontier_service.exception.PageBiggerThanMaxSizeException;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vdt.crawler.frontier_service.utils.Util;

/**
 * @author Yasser Ganjisaffar
 */
public class RobotstxtServer {

    private static final Logger logger = LoggerFactory.getLogger(RobotstxtServer.class);

    protected RobotstxtConfig config;

    protected final Map<String, HostDirectives> host2directivesCache = new HashMap<>();

    protected PageFetcher pageFetcher;

    private final int maxBytes;

    public RobotstxtServer(RobotstxtConfig config, PageFetcher pageFetcher) {
        this(config, pageFetcher, 16384);
    }

    public RobotstxtServer(RobotstxtConfig config, PageFetcher pageFetcher, int maxBytes) {
        this.config = config;
        this.pageFetcher = pageFetcher;
        this.maxBytes = maxBytes;
    }

    private static String getHost(URL url) {
        return url.getHost().toLowerCase();
    }

    public Double getCrawlDelay(String webURL) throws IOException, InterruptedException {
        if (!config.isEnabled()) {
            return null;
        }
        try {
            URL url = new URL(webURL);
            String host = getHost(url);
            String path = url.getPath();

            HostDirectives directives = host2directivesCache.get(host);

            if (directives != null && directives.needsRefetch()) {
                synchronized (host2directivesCache) {
                    host2directivesCache.remove(host);
                    directives = null;
                }
            }
            if (directives == null) {
                directives = fetchDirectives(url);
            }
            return directives.getCrawlDelay();
        } catch (MalformedURLException e) {
            logger.error("Bad URL in Robots.txt: " + webURL, e);
        }

        logger.warn("RobotstxtServer: default: allow - " + webURL);
        return null;
    }

    public boolean allows(String webURL) throws IOException, InterruptedException {
        if (!config.isEnabled()) {
            return true;
        }
        try {
            URL url = new URL(webURL);
            String host = getHost(url);
            String path = url.getPath();

            HostDirectives directives = host2directivesCache.get(host);

            if (directives != null && directives.needsRefetch()) {
                synchronized (host2directivesCache) {
                    host2directivesCache.remove(host);
                    directives = null;
                }
            }
            if (directives == null) {
                directives = fetchDirectives(url);
            }
            return directives.allows(path);
        } catch (MalformedURLException e) {
            logger.error("Bad URL in Robots.txt: " + webURL, e);
        }

        logger.warn("RobotstxtServer: default: allow - " + webURL);
        return true;
    }

    private HostDirectives fetchDirectives(URL url) {
        String host = getHost(url);
        String port = ((url.getPort() == url.getDefaultPort()) || (url.getPort() == -1)) ? "" :
                      (":" + url.getPort());
        String proto = url.getProtocol();
        String robotsTxtUrl = proto + "://" + host + port + "/robots.txt";
        HostDirectives directives = null;
        PageFetchResult fetchResult = null;
        try {
            for (int redir = 0; redir < 3; ++redir) {
                fetchResult = pageFetcher.fetchPage(robotsTxtUrl);
                int status = fetchResult.getStatusCode();
                // Follow redirects up to 3 levels
                if ((status == HttpStatus.SC_MULTIPLE_CHOICES ||
                     status == HttpStatus.SC_MOVED_PERMANENTLY ||
                     status == HttpStatus.SC_MOVED_TEMPORARILY ||
                     status == HttpStatus.SC_SEE_OTHER ||
                     status == HttpStatus.SC_TEMPORARY_REDIRECT || status == 308) &&
                    // SC_PERMANENT_REDIRECT RFC7538
                    fetchResult.getMovedToUrl() != null) {
                    robotsTxtUrl = fetchResult.getMovedToUrl();
                    fetchResult.discardContentIfNotConsumed();
                } else {
                    // Done on all other occasions
                    break;
                }
            }

            if (fetchResult.getStatusCode() == HttpStatus.SC_OK) {
                // Most recent answer on robots.txt max size is
                // https://developers.google.com/search/reference/robots_txt
                fetchResult.fetchContent(500 * 1024);
                if (Util.hasPlainTextContent(fetchResult.getContentType())) {
                    String content;
                    if (fetchResult.getContentCharset() == null) {
                        content = new String(fetchResult.getContentData());
                    } else {
                        content = new String(fetchResult.getContentData(), fetchResult.getContentCharset());
                    }
                    directives = RobotstxtParser.parse(content, config);
                } else if (fetchResult.getContentType()
                               .contains(
                                   "html")) { // TODO This one should be upgraded to remove all
                    // html tags
                    String content = new String(fetchResult.getContentData());
                    directives = RobotstxtParser.parse(content, config);
                } else {
                    logger.warn(
                        "Can't read this robots.txt: {}  as it is not written in plain text, " +
                        "contentType: {}", robotsTxtUrl, fetchResult.getContentType());
                }
            } else {
                logger.debug("Can't read this robots.txt: {}  as it's status code is {}",
                             robotsTxtUrl, fetchResult.getStatusCode());
            }
        } catch (SocketException | UnknownHostException | SocketTimeoutException |
            NoHttpResponseException se) {
            // No logging here, as it just means that robots.txt doesn't exist on this server
            // which is perfectly ok
            logger.trace("robots.txt probably does not exist.", se);
        } catch (PageBiggerThanMaxSizeException pbtms) {
            logger.error("Error occurred while fetching (robots) url: {}, {}",
                         robotsTxtUrl, pbtms.getMessage());
        } catch (IOException | InterruptedException | RuntimeException e) {
            logger.error("Error occurred while fetching (robots) url: " + robotsTxtUrl, e);
        } finally {
            if (fetchResult != null) {
                fetchResult.discardContentIfNotConsumed();
            }
        }

        if (directives == null) {
            // We still need to have this object to keep track of the time we fetched it
            directives = new HostDirectives(config);
        }
        synchronized (host2directivesCache) {
            if (host2directivesCache.size() == config.getCacheSize()) {
                String minHost = null;
                long minAccessTime = Long.MAX_VALUE;
                for (Map.Entry<String, HostDirectives> entry : host2directivesCache.entrySet()) {
                    long entryAccessTime = entry.getValue().getLastAccessTime();
                    if (entryAccessTime < minAccessTime) {
                        minAccessTime = entryAccessTime;
                        minHost = entry.getKey();
                    }
                }
                host2directivesCache.remove(minHost);
            }
            host2directivesCache.put(host, directives);
        }
        return directives;
    }
}
