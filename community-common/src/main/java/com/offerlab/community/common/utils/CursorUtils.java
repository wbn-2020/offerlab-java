package com.offerlab.community.common.utils;

import java.util.Base64;

/**
 * 游标工具类 - 用于分页
 */
public class CursorUtils {

    /**
     * 编码游标：将 score 和 id 组合编码为 Base64 字符串
     */
    public static String encode(long score, long id) {
        String raw = score + ":" + id;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    /**
     * 解码游标：从 Base64 字符串解码出 score 和 id
     */
    public static CursorData decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            String raw = new String(Base64.getDecoder().decode(cursor));
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                return null;
            }
            return new CursorData(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }

    public static class CursorData {
        public final long score;
        public final long id;

        public CursorData(long score, long id) {
            this.score = score;
            this.id = id;
        }
    }
}
