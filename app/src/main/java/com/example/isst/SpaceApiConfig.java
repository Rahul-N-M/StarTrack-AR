package com.example.isst;

/**
 * ════════════════════════════════════════════════════
 *  STARTRACK AR — API CONFIGURATION
 *  Paste your keys here. Never commit this file to git.
 * ════════════════════════════════════════════════════
 *
 *  GEMINI CHATBOT
 *    Get free key: https://aistudio.google.com/app/apikey
 *    Free tier: generous daily quota, no credit card
 *
 *  N2YO (ISS passes)
 *    Get free key: https://www.n2yo.com/api/
 *    Free tier: 1000 requests/hour
 *
 *  OPENWEATHERMAP (sky visibility / clouds)
 *    Get free key: https://openweathermap.org/api
 *    Free tier: 1000 calls/day
 *
 *  ASTRONOMY API (Sun / Moon / Planets)
 *    Get free key: https://astronomyapi.com
 *    Free tier: student plan available, sign up with college email
 *    Provides: Application ID + Application Secret (Basic Auth)
 */
public final class SpaceApiConfig {

    private SpaceApiConfig() {}

    // ── Gemini Chatbot ────────────────────────────────────────────────────
    // Paste your key (starts with "AIza...")
    public static final String GEMINI_KEY = "AIzaSyB-UoIvZm9d99w8GXpIFnG25gICLCoipOs";

    // ── N2YO (ISS pass predictions) ───────────────────────────────────────
    // Paste your key from n2yo.com/api
    public static final String N2YO_KEY = "X5WHF5-8SPVFC-7W7KEZ-5RTP";

    // ── OpenWeatherMap ────────────────────────────────────────────────────
    // Paste your key from openweathermap.org/api
    public static final String OPENWEATHER_KEY = "360efaac12c291b308435329f9ae799b";

    // ── AstronomyAPI (planets / sun / moon) ───────────────────────────────
    // Paste BOTH values from astronomyapi.com dashboard
    public static final String ASTRONOMY_APP_ID     = "dd2d0003-dd5b-4eb6-a3c2-5103340b7117";
    public static final String ASTRONOMY_APP_SECRET = "b0776a68793263fa23b511ca14aeaa45287beaab3fedc40789b21b3b44a7aa676706cc54a8118e4030d1c9338974fea1cbc25accfd903430332a9b818b1f081f84ad43326ee9ff3e6407cd54c1ad87eb900f45fd0b96e87b70dc5481e034dd2ee95ab6dd695481466b9730f0f18e7629";

    // ── Derived helpers ───────────────────────────────────────────────────

    /** true if key has been replaced from placeholder */
    public static boolean isN2YOConfigured() {
        return !N2YO_KEY.startsWith("YOUR_");
    }

    public static boolean isWeatherConfigured() {
        return !OPENWEATHER_KEY.startsWith("YOUR_");
    }

    public static boolean isAstronomyConfigured() {
        return !ASTRONOMY_APP_ID.startsWith("YOUR_")
                && !ASTRONOMY_APP_SECRET.startsWith("YOUR_");
    }

    public static boolean isGeminiConfigured() {
        return !GEMINI_KEY.startsWith("YOUR_");
    }
}