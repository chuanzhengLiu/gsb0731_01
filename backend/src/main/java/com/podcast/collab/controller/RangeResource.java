package com.podcast.collab.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.core.io.InputStreamResource;

import java.io.IOException;
import java.io.InputStream;

/** 支持 Range 的文件资源 */
public class RangeResource extends InputStreamResource {
    private final long length;

    public RangeResource(FileSystemResource resource, long start, long length) throws IOException {
        super(skip(resource.getInputStream(), start));
        this.length = length;
    }

    private static InputStream skip(InputStream in, long start) throws IOException {
        long skipped = 0;
        while (skipped < start) {
            long s = in.skip(start - skipped);
            if (s <= 0) {
                break;
            }
            skipped += s;
        }
        return in;
    }

    @Override
    public long contentLength() {
        return length;
    }
}
