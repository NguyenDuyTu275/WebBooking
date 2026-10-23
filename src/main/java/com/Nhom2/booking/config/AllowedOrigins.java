package com.Nhom2.booking.config;

import java.util.List;

/** Front-end origins allowed to call the API and open the chat WebSocket; shared by CORS and WebSocket config. */
public final class AllowedOrigins {

    public static final List<String> PATTERNS = List.of(
            "http://localhost:*",
            "https://*.vercel.app",
            "https://*.railway.app"
    );

    private AllowedOrigins() {
    }
}
