package org.nwh.securejwt.security;

import org.nwh.securejwt.config.SecurityProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de tentativas de login por IP (janela de 1 minuto).
 * Complementa o bloqueio por conta: trava quem testa muitas contas diferentes.
 *
 * Nota: em memoria, vale para uma instancia. Com varias instancias, usar
 * Redis (ex.: Bucket4j) ou o limite do proxy/WAF.
 */
@Component
public class LoginRateLimiter {

    private record Window(long minute, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limitPerMinute;

    public LoginRateLimiter(SecurityProperties props) {
        this.limitPerMinute = props.maxLoginRequestsPerIpPerMinute();
    }

    public boolean tryAcquire(String ip) {
        long minute = currentMinute();
        Window window = windows.compute(ip == null ? "unknown" : ip,
                (key, old) -> (old == null || old.minute() != minute) ? new Window(minute, new AtomicInteger()) : old);
        return window.count().incrementAndGet() <= limitPerMinute;
    }

    @Scheduled(fixedDelay = 60_000)
    void evictOldWindows() {
        long minute = currentMinute();
        windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
    }

    private static long currentMinute() {
        return System.currentTimeMillis() / 60_000;
    }
}
