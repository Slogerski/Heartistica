package com.ogulniega;

public final class OgulniegaModApi {
    public static String language;
    public static int calls;
    public static RuntimeException failure;
    public static String getLanguage() {
        calls++;
        if (failure != null) throw failure;
        return language;
    }
}
