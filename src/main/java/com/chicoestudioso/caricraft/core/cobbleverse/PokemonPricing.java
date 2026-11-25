package com.chicoestudioso.caricraft.core.cobbleverse;

/**
 * Lo que paga la tienda Cobbleverse (en CobbleDollars) por un Pokémon del equipo.
 * <pre>
 * (150 + 30 × nivel) × rareza × IV × variocolor × legendario × multiplicador
 *   rareza      = 1 + 3 × (1 − ratio de captura / 255)   → de ×1 (común) a ×4 (muy raro)
 *   IV          = 0,5 + IV totales / 186                  → de ×0,5 a ×1,5
 *   variocolor  = ×10
 *   legendario  = ×5 (solo si se permite venderlos)
 * </pre>
 * Se redondea a decenas.
 */
public final class PokemonPricing {
    /** Datos del Pokémon que importan para el precio. */
    public record PokemonInfo(String species, int level, int catchRate, int ivTotal, boolean shiny, boolean legendary) {
    }

    public static final int MAX_IV_TOTAL = 186;

    private PokemonPricing() {
    }

    public static long sellPrice(PokemonInfo info, double multiplier) {
        double base = 150 + 30.0 * Math.max(1, Math.min(100, info.level()));
        double rarity = 1 + 3 * (1 - Math.max(1, Math.min(255, info.catchRate())) / 255.0);
        double ivs = 0.5 + Math.max(0, Math.min(MAX_IV_TOTAL, info.ivTotal())) / (double) MAX_IV_TOTAL;
        double price = base * rarity * ivs * (info.shiny() ? 10 : 1) * (info.legendary() ? 5 : 1) * Math.max(0, multiplier);
        return Math.round(price / 10.0) * 10;
    }
}
