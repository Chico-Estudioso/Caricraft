package com.chicoestudioso.caricraft.client.phone;

/** Paleta de colores (ARGB) del CariPhone. Los temas son cosméticos personales. */
public record PhoneTheme(int bezel, int bezelEdge, int background, int header, int headerText, int accent,
                         int accentText, int text, int textDim, int card, int cardHover, int good, int bad,
                         int warn, int track) {

    public static final PhoneTheme CLASSIC = new PhoneTheme(0xFF23232F, 0xFF101018, 0xFFFDF6EC, 0xFF2EC4B6, 0xFFFFFFFF,
            0xFFFF9F1C, 0xFF1D1D1F, 0xFF1D1D1F, 0xFF6B6B76, 0xFFFFFFFF, 0xFFF3EDE4, 0xFF2A9D58, 0xFFE63946,
            0xFFF4A300, 0xFFE4DED5);
    public static final PhoneTheme SUNSET = new PhoneTheme(0xFF2B1B2E, 0xFF140A16, 0xFFFFF1E6, 0xFFFF7B54, 0xFFFFFFFF,
            0xFF8E44AD, 0xFFFFFFFF, 0xFF2B1B2E, 0xFF7A6270, 0xFFFFFFFF, 0xFFFFE6D5, 0xFF2A9D58, 0xFFD62839,
            0xFFF4A300, 0xFFF3DCCB);
    public static final PhoneTheme OCEAN = new PhoneTheme(0xFF0B2545, 0xFF06142A, 0xFFE8F4FA, 0xFF0077B6, 0xFFFFFFFF,
            0xFF00B4D8, 0xFF06142A, 0xFF0B2545, 0xFF5A7184, 0xFFFFFFFF, 0xFFD6ECF6, 0xFF1B9E77, 0xFFE63946,
            0xFFF4A300, 0xFFCFE3EE);
    public static final PhoneTheme FOREST = new PhoneTheme(0xFF1B2E22, 0xFF0C1710, 0xFFEEF5EA, 0xFF2D6A4F, 0xFFFFFFFF,
            0xFFE9C46A, 0xFF1B2E22, 0xFF1B2E22, 0xFF5E7466, 0xFFFFFFFF, 0xFFDDEBD6, 0xFF2D9D5A, 0xFFD62839,
            0xFFE29A00, 0xFFD4E3CD);

    public static PhoneTheme byId(String id) {
        if (id == null) {
            return CLASSIC;
        }
        return switch (id) {
            case "theme_sunset" -> SUNSET;
            case "theme_ocean" -> OCEAN;
            case "theme_forest" -> FOREST;
            default -> CLASSIC;
        };
    }

    /** Mezcla lineal de dos colores ARGB. */
    public static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
