package com.asinking.com.openapi.config;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** JWT Token 黑名单：登出后将 JTI 加入内存黑名单，使 Token 即时失效。 */
@Component
public class TokenBlacklist {

    private final ConcurrentHashMap<String, Long> revokedJtiToExpMillis = new ConcurrentHashMap<>();

    public void revoke(String jti, long expMillis) {
        if (jti == null || jti.isEmpty()) {
            return;
        }
        revokedJtiToExpMillis.put(jti, expMillis);
        cleanup();
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        Long exp = revokedJtiToExpMillis.get(jti);
        if (exp == null) {
            return false;
        }
        if (exp <= System.currentTimeMillis()) {
            revokedJtiToExpMillis.remove(jti);
            return false;
        }
        return true;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = revokedJtiToExpMillis.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() == null || entry.getValue() <= now) {
                it.remove();
            }
        }
    }
}

