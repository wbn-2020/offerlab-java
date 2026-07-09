package com.offerlab.community.infra.security;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

public final class ExternalUrlSafety {

    private ExternalUrlSafety() {
    }

    public static String requireSafeHttpUrl(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid(fieldName);
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || !StringUtils.hasText(host)
                    || uri.getUserInfo() != null) {
                throw invalid(fieldName);
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw invalid(fieldName);
            }
            return normalized;
        } catch (BizException e) {
            throw e;
        } catch (URISyntaxException e) {
            throw invalid(fieldName);
        } catch (Exception e) {
            throw invalid(fieldName);
        }
    }

    public static void requireSafeExternalImageUrl(String value, String fieldName) {
        requireSafeHttpUrl(value, fieldName, 512);
    }

    private static BizException invalid(String fieldName) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), fieldName + " is not a safe external URL");
    }
}
