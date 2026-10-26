package org.nwh.securejwt.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registo de auditoria: quem, o que, de onde.
 * Nunca regista senhas nem tokens. Remove quebras de linha para impedir
 * que um atacante injete linhas falsas no log.
 */
@Component
public class AuditLog {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    public void event(String event, String user, String ip, String detail) {
        audit.info("event={} user={} ip={} detail={}", clean(event), clean(user), clean(ip), clean(detail));
    }

    public void warn(String event, String user, String ip, String detail) {
        audit.warn("event={} user={} ip={} detail={}", clean(event), clean(user), clean(ip), clean(detail));
    }

    private static String clean(String value) {
        if (value == null) {
            return "-";
        }
        String s = value.replaceAll("[\\r\\n\\t]", "_");
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}
