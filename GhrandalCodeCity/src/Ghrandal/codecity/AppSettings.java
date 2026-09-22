package Ghrandal.codecity;

import java.util.prefs.Preferences;

/** Persistent user preferences. Kept deliberately small and backward compatible. */
final class AppSettings {
    static final int DEFAULT_CITY_BACKGROUND_RGB = 0x1A1F26;
    private static final Preferences P = Preferences.userRoot().node("GhrandalCodeCity");
    private AppSettings() {}
    static int windowWidth() { return P.getInt("window.width", 1480); }
    static int windowHeight() { return P.getInt("window.height", 920); }
    static boolean antiAlias() { return P.getBoolean("render.antialias", true); }
    static java.awt.Color cityBackground() {
        return new java.awt.Color(P.getInt("city.background.rgb", DEFAULT_CITY_BACKGROUND_RGB));
    }
    static void setCityBackground(java.awt.Color color) {
        if (color != null) P.putInt("city.background.rgb", color.getRGB() & 0x00FFFFFF);
    }
    static void resetCityBackground() {
        P.putInt("city.background.rgb", DEFAULT_CITY_BACKGROUND_RGB);
    }
    static java.awt.Color defaultCityBackground() {
        return new java.awt.Color(DEFAULT_CITY_BACKGROUND_RGB);
    }
    static void saveWindow(int w, int h) { P.putInt("window.width", Math.max(900,w)); P.putInt("window.height", Math.max(600,h)); }
    static void setAntiAlias(boolean value) { P.putBoolean("render.antialias", value); }
}
