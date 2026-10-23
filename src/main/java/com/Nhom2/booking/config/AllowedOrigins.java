package com.Nhom2.booking.config;

import java.util.List;

/** Front-end origins allowed to call the API and open the chat WebSocket; shared by CORS and WebSocket config. */
public final class AllowedOrigins {

    public static final List<String> PATTERNS = List.of(
            "http://localhost:*",
            // Website trên tên miền của nhóm (duytu205.id.vn, www.duytu205.id.vn)
            "https://duytu205.id.vn",
            "https://*.duytu205.id.vn",
            "https://*.vercel.app",
            "https://*.railway.app"
    );

    private AllowedOrigins() {
    }
}
