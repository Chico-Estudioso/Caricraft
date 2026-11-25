package com.chicoestudioso.caricraft.core.box;

import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import com.chicoestudioso.caricraft.core.shop.ShopService;

/** CariBox: reglas del cofre compartido (los objetos los guarda el servidor). */
public final class BoxService {
    public static final int BASE_ROWS = 3;
    public static final int MAX_ROWS = 6;

    private final ShopService shop;

    public BoxService(ShopService shop) {
        this.shop = shop;
    }

    public boolean hasApp(Party party) {
        return party != null && shop.hasApp(party, AppCatalog.CARIBOX);
    }

    /** Filas del cofre: 3 de base y una más por ampliación (máximo 6, como un cofre doble). */
    public int rows(Party party) {
        return Math.min(MAX_ROWS, BASE_ROWS + party.upgradeLevel(AppCatalog.CARIBOX_ROWS));
    }

    public int slots(Party party) {
        return rows(party) * 9;
    }
}
