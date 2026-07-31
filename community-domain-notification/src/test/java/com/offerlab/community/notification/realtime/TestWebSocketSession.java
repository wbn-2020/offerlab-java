package com.offerlab.community.notification.realtime;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TestWebSocketSession implements InvocationHandler {

    private final String id;
    private final WebSocketSession session;
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<WebSocketMessage<?>> sentMessages = new ArrayList<>();
    private boolean open = true;
    private int binaryMessageSizeLimit;
    private CloseStatus closeStatus;

    TestWebSocketSession(String id) {
        this.id = id;
        this.session = (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(),
                new Class<?>[]{WebSocketSession.class},
                this);
    }

    WebSocketSession session() {
        return session;
    }

    List<WebSocketMessage<?>> sentMessages() {
        return sentMessages;
    }

    CloseStatus closeStatus() {
        return closeStatus;
    }

    int binaryMessageSizeLimit() {
        return binaryMessageSizeLimit;
    }

    void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "getId" -> id;
            case "isOpen" -> open;
            case "getAttributes" -> attributes;
            case "setBinaryMessageSizeLimit" -> {
                binaryMessageSizeLimit = (int) args[0];
                yield null;
            }
            case "getBinaryMessageSizeLimit" -> binaryMessageSizeLimit;
            case "sendMessage" -> {
                sentMessages.add((WebSocketMessage<?>) args[0]);
                yield null;
            }
            case "close" -> {
                closeStatus = args == null || args.length == 0 ? CloseStatus.NORMAL : (CloseStatus) args[0];
                open = false;
                yield null;
            }
            case "toString" -> "TestWebSocketSession[" + id + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultValue(method.getReturnType());
        };
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return null;
    }
}
