package com.chicoestudioso.caricraft.core.shop;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.shop.ShopItem.Kind;
import com.chicoestudioso.caricraft.core.shop.ShopItem.Ownership;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Catálogo extensible de apps y cosméticos. Los precios finales salen de aquí (servidor). */
public final class AppCatalog {
    public static final String MISSIONS = "missions";
    public static final String WALLET = "wallet";
    public static final String DUO = "duo";
    public static final String SHOP = "shop";
    public static final String SETTINGS = "settings";
    public static final String CHRONICLES = "chronicles";
    public static final String EXPLORER = "explorer";
    public static final String STATS = "stats";
    public static final String CARILENS = "carilens";
    public static final String CARICAMP = "caricamp";
    public static final String CHALLENGE = "challenge";
    public static final String MARKET = "market";

    private final Map<String, ShopItem> items = new LinkedHashMap<>();

    /** Catálogo por defecto con precios y desactivaciones de la configuración aplicados. */
    public static AppCatalog createDefault(CaricraftConfig config) {
        AppCatalog catalog = new AppCatalog();
        catalog.register(builtin(MISSIONS, "minecraft:writable_book", 0xFFFF9F1C));
        catalog.register(builtin(WALLET, "minecraft:gold_ingot", 0xFFF4C430));
        catalog.register(builtin(DUO, "minecraft:cake", 0xFFE71D36));
        catalog.register(builtin(SHOP, "minecraft:emerald", 0xFF2A9D8F));
        catalog.register(builtin(SETTINGS, "minecraft:comparator", 0xFF6C757D));
        if (config.market.enabled) {
            catalog.register(builtin(MARKET, "minecraft:chest", 0xFF6A4C93));
        }
        if (config.challenge.enabled) {
            catalog.register(builtin(CHALLENGE, "minecraft:skeleton_skull", 0xFF9D0208));
        }
        catalog.register(app(CHRONICLES, 250, "minecraft:book", 0xFF8E5A3C));
        catalog.register(app(EXPLORER, 400, "minecraft:compass", 0xFF3A86FF));
        catalog.register(app(STATS, 200, "minecraft:clock", 0xFF8338EC));
        catalog.register(comingSoon(CARILENS, 300, "minecraft:spyglass", 0xFF00B4D8));
        catalog.register(app(CARICAMP, 500, "minecraft:campfire", 0xFFFB5607));
        catalog.register(theme("theme_sunset", "minecraft:orange_dye", 0xFFFF7B54));
        catalog.register(theme("theme_ocean", "minecraft:light_blue_dye", 0xFF0077B6));
        catalog.register(theme("theme_forest", "minecraft:lime_dye", 0xFF2D6A4F));
        catalog.applyConfig(config);
        return catalog;
    }

    private static ShopItem builtin(String id, String icon, int color) {
        return new ShopItem(id, Kind.APP, Ownership.PARTY, 0, true, false, List.of(), icon, color);
    }

    private static ShopItem app(String id, long price, String icon, int color) {
        return new ShopItem(id, Kind.APP, Ownership.PARTY, price, false, false, List.of(), icon, color);
    }

    private static ShopItem comingSoon(String id, long price, String icon, int color) {
        return new ShopItem(id, Kind.APP, Ownership.PARTY, price, false, true, List.of(), icon, color);
    }

    private static ShopItem theme(String id, String icon, int color) {
        return new ShopItem(id, Kind.COSMETIC, Ownership.PERSONAL, 150, false, false, List.of(), icon, color);
    }

    public void register(ShopItem item) {
        items.put(item.id(), item);
    }

    private void applyConfig(CaricraftConfig config) {
        config.prices.forEach((id, price) -> {
            ShopItem item = items.get(id);
            if (item != null && !item.builtin()) {
                items.put(id, item.withPrice(price));
            }
        });
        config.disabledItems.forEach(id -> {
            ShopItem item = items.get(id);
            if (item != null && !item.builtin()) {
                items.remove(id);
            }
        });
    }

    public Optional<ShopItem> get(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public Collection<ShopItem> all() {
        return Collections.unmodifiableCollection(items.values());
    }
}
