package com.offerlab.community.post.domain.model;

public enum PostDomain {
    TECH(1, "技术"),
    CAREER(2, "职场"),
    READING(3, "阅读"),
    LIFESTYLE(4, "生活"),
    INVESTMENT(5, "投资理财");

    private final int code;
    private final String displayName;

    PostDomain(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static PostDomain fromCode(Integer code) {
        if (code == null) return TECH;
        for (PostDomain d : values()) {
            if (d.code == code) return d;
        }
        return TECH;
    }

    public static boolean isValid(Integer code) {
        if (code == null) return false;
        for (PostDomain d : values()) {
            if (d.code == code) return true;
        }
        return false;
    }
}
