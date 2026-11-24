package com.chicoestudioso.caricraft.core.shop;

import java.util.List;

/**
 * Artículo del catálogo del CariPhone.
 *
 * @param builtin    viene instalada de serie (no se compra)
 * @param comingSoon visible pero no comprable
 * @param icon       id de ítem de Minecraft que el cliente dibuja como icono
 * @param color      color ARGB del fondo del icono
 */
public record ShopItem(String id, Kind kind, Ownership ownership, long price, boolean builtin, boolean comingSoon,
                       List<String> requires, String icon, int color) {

    public enum Kind { APP, COSMETIC }

    public enum Ownership { PARTY, PERSONAL }

    public ShopItem {
        requires = List.copyOf(requires);
    }

    public ShopItem withPrice(long newPrice) {
        return new ShopItem(id, kind, ownership, newPrice, builtin, comingSoon, requires, icon, color);
    }

    public boolean isTheme() {
        return kind == Kind.COSMETIC && id.startsWith("theme_");
    }
}
