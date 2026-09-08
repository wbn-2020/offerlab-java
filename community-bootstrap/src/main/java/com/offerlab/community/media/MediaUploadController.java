package com.offerlab.community.media;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 内容图片上传（封面图等）。
 *
 * 安全边界：
 * - 仅登录用户可上传；按用户限流（10 次/小时）防滥用；
 * - 只接受 jpeg/png/webp，单文件 ≤ 5MB；
 * - 文件名由服务端按内容 SHA-256 重新生成（防路径穿越、防同名覆盖语义歧义），
 *   按日期分目录存储在 offerlab.media.upload-dir 下；
 * - 通过 /media/** 静态映射对外只读访问（见 MediaWebConfig）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
public class MediaUploadController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
            MediaType.IMAGE_JPEG_VALUE, "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final Path uploadRoot;

    public MediaUploadController(@Value("${offerlab.media.upload-dir:./data/media}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Data
    public static class UploadResult {
        private String url;
        private long size;
        private String contentType;
    }

    @PostMapping("/upload")
    @RateLimit(key = "'media:upload:' + #uid", rate = 10, per = 3600, failOpen = false)
    public Result<UploadResult> upload(@RequestParam("file") MultipartFile file) {
        Long uid = UserContext.require();
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择要上传的图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "图片不能超过 5MB");
        }
        String extension = resolveAllowedExtension(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("media upload read failed: uid={} error={}", uid, e.getMessage());
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "图片读取失败，请重试");
        }
        String storedName = sha256Hex(bytes) + "." + extension;
        String relative = LocalDate.now() + "/" + storedName;
        Path target = uploadRoot.resolve(relative).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("media upload write failed: uid={} error={}", uid, e.getMessage());
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "图片保存失败，请稍后重试");
        }
        log.info("media uploaded: uid={} path={} size={}", uid, relative, bytes.length);
        UploadResult result = new UploadResult();
        result.setUrl("/media/" + relative);
        result.setSize(bytes.length);
        result.setContentType(file.getContentType());
        return Result.ok(result);
    }

    private String resolveAllowedExtension(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String byContentType = CONTENT_TYPE_TO_EXTENSION.get(contentType);
        if (byContentType != null) {
            return byContentType;
        }
        String original = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (original != null && ALLOWED_EXTENSIONS.contains(original.toLowerCase(Locale.ROOT))) {
            return original.toLowerCase(Locale.ROOT);
        }
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "仅支持 JPG/PNG/WebP 图片");
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                byte high = (byte) ((b >> 4) & 0xF);
                byte low = (byte) (b & 0xF);
                builder.append(hexChar(high)).append(hexChar(low));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static char hexChar(byte value) {
        return value < 10 ? (char) ('0' + value) : (char) ('a' + (value - 10));
    }
}
