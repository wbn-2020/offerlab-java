package com.offerlab.community.post.api.dto;

public final class PostContentLimits {
    public static final int MIN_TITLE_LEN = 8;
    public static final int MAX_TITLE_LEN = 200;
    public static final int MAX_CONTENT_LEN = 50000;
    public static final int MAX_EXT_JSON_LEN = 20000;
    public static final int MAX_SUMMARY_LEN = 240;
    public static final int MAX_TAG_COUNT = 5;
    public static final int MAX_TAG_NAME_LEN = 32;
    public static final int MAX_COVER_URL_LEN = 512;

    private PostContentLimits() {
    }
}
