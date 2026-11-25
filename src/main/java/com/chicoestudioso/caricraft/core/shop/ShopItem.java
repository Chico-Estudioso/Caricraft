package com.chicoestudioso.caricraft.core.shop;

import java.util.List;

/**
 * Artículo del catálogo del CariPhone.
 *
 * @param builtin    viene instalada de serie (no se compra)
 * @param comingSoon visible pero no comprable
 * @param icon       id de ítem de Minecraft que el cliente dibuja como icono
 * @param color      color ARGB del fondo del icono
 * @param maxLevel   niveles comprables (1 = compra única; &gt;1 = mejora que se compra varias veces)
 */
public record ShopItem(String id, Kind kind, Ownership ownership, long price, boolean builtin, boolean comingSoon,
                       List<String> requires, String icon, int color, int maxLevel) {

    public enum Kind { APP, COSMETIC, UPGRADE }

    public enum Ownership { PARTY, PERSONAL }

    public ShopItem {
        requires = List.copyOf(requires);
        maxLevel = Math.max(1, maxLevel);
    }

    public ShopItem(String id, Kind kind, Ownership ownership, long price, boolean builtin, boolean comingSoon,
                    List<String> requires, String icon, int color) {
        this(id, kind, ownership, price, builtin, comingSoon, requires, icon, color, 1);
    }

    public ShopItem withPrice(long newPrice) {
        return new ShopItem(id, kind, ownership, newPrice, builtin, comingSoon, requires, icon, color, maxLevel);
    }

    public boolean isUpgrade() {
        return maxLevel > 1;
    }

    public boolean isTheme() {
        return kind == Kind.COSMETIC && id.startsWith("theme_");
    }
}
