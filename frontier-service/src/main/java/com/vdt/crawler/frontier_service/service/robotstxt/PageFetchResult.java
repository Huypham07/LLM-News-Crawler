package com.vdt.crawler.frontier_service.service.robotstxt;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageFetchResult {

    private static final Logger logger = LoggerFactory.getLogger(PageFetchResult.class);

    private int statusCode;
    private HttpEntity entity = null;
    private Header[] responseHeaders = null;
    private String fetchedUrl = null;

    private byte[] contentData;
    private String contentType;
    private String contentEncoding;
    private String contentCharset;
    private boolean truncated = false;

    /**
     * Read contents from an entity, with a specified maximum. This is a replacement of
     * EntityUtils.toByteArray because that function does not impose a maximum size.
     *
     * @param entity The entity from which to read
     * @param maxBytes The maximum number of bytes to read
     * @return A byte array containing maxBytes or fewer bytes read from the entity
     *
     * @throws IOException Thrown when reading fails for any reason
     */
    private byte[] toByteArray(HttpEntity entity, int maxBytes) throws IOException {
        if (entity == null) {
            return new byte[0];
        }
        try (InputStream is = entity.getContent()) {
            int size = (int) entity.getContentLength();
            int readBufferLength = size;

            if (readBufferLength <= 0) {
                readBufferLength = 4096;
            }
            // in case when the maxBytes is less than the actual page size
            readBufferLength = Math.min(readBufferLength, maxBytes);

            // We allocate the buffer with either the actual size of the entity (if available)
            // or with the default 4KiB if the server did not return a value to avoid allocating
            // the full maxBytes (for the cases when the actual size will be smaller than maxBytes).
            ByteArrayBuffer buffer = new ByteArrayBuffer(readBufferLength);

            byte[] tmpBuff = new byte[4096];
            int dataLength;

            while ((dataLength = is.read(tmpBuff)) != -1) {
                if (maxBytes > 0 && (buffer.length() + dataLength) > maxBytes) {
                    truncated = true;
                    dataLength = maxBytes - buffer.length();
                }
                buffer.append(tmpBuff, 0, dataLength);
                if (truncated) {
                    break;
                }
            }
            return buffer.toByteArray();
        }
    }

    private void load(HttpEntity entity, int maxBytes) throws IOException {
        contentType = null;
        Header type = entity.getContentType();
        if (type != null) {
            contentType = type.getValue();
        }

        contentEncoding = null;
        Header encoding = entity.getContentEncoding();
        if (encoding != null) {
            contentEncoding = encoding.getValue();
        }

        Charset charset;
        try {
            charset = ContentType.getOrDefault(entity).getCharset();
        } catch (Exception e) {
            logger.warn("parse charset failed: {}", e.getMessage());
            charset = StandardCharsets.UTF_8;
        }

        if (charset != null) {
            contentCharset = charset.displayName();
        }

        contentData = toByteArray(entity, maxBytes);
    }

    public void fetchContent(int maxBytes) throws SocketTimeoutException, IOException {
        try {
            load(entity, maxBytes);
        } catch (SocketTimeoutException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            logger.info("Exception while fetching content for: {} [{}]", fetchedUrl,
                    e.getMessage());
        }
    }

    public void discardContentIfNotConsumed() {
        try {
            if (entity != null) {
                EntityUtils.consume(entity);
            }
        } catch (IOException ignored) {
            // We can EOFException (extends IOException) exception. It can happen on compressed
            // streams which are not
            // repeatable
            // We can ignore this exception. It can happen if the stream is closed.
        } catch (RuntimeException e) {
            logger.warn("Unexpected error occurred while trying to discard content", e);
        }
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public HttpEntity getEntity() {
        return entity;
    }

    public void setEntity(HttpEntity entity) {
        this.entity = entity;
    }

    public Header[] getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Header[] responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getFetchedUrl() {
        return fetchedUrl;
    }

    public void setFetchedUrl(String fetchedUrl) {
        this.fetchedUrl = fetchedUrl;
    }

    public byte[] getContentData() {
        return contentData;
    }

    public void setContentData(byte[] contentData) {
        this.contentData = contentData;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public String getContentCharset() {
        return contentCharset;
    }

    public void setContentCharset(String contentCharset) {
        this.contentCharset = contentCharset;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
