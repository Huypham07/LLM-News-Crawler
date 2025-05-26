package com.vdt.crawler.frontier_service.exception;

public class PageBiggerThanMaxSizeException extends Exception {
    long pageSize;

    public PageBiggerThanMaxSizeException(long pageSize) {
        super("Aborted fetching of this URL as it's size ( " + pageSize +
              " ) exceeds the maximum size");
        this.pageSize = pageSize;
    }

    public long getPageSize() {
        return pageSize;
    }
}