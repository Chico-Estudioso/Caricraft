package com.chicoestudioso.caricraft.core.shop;

import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;

import java.util.UUID;

/** Compras de la tienda del CariPhone. */
public final class ShopService {
    public static final String STAT_PURCHASES = "purchases";

    private final AppCatalog catalog;
    private final EconomyService economy;
    private final ChronicleService chronicle;

    public ShopService(AppCatalog catalog, EconomyService economy, ChronicleService chronicle) {
        this.catalog = catalog;
        this.economy = economy;
        this.chronicle = chronicle;
    }

    public AppCatalog catalog() {
        return catalog;
    }

    /** ¿Puede el grupo usar la app? (de serie o comprada y no desactivada). */
    public boolean hasApp(Party party, String appId) {
        return catalog.get(appId)
                .map(item -> item.builtin() || (!item.comingSoon() && party.ownedApps().contains(appId)))
                .orElse(false);
    }

    public boolean owns(Party party, PlayerProfile profile, ShopItem item) {
        if (item.builtin()) {
            return true;
        }
        if (item.isUpgrade()) {
            return party.upgradeLevel(item.id()) >= item.maxLevel();
        }
        return item.ownership() == ShopItem.Ownership.PARTY
                ? party.ownedApps().contains(item.id())
                : profile.ownedCosmetics().contains(item.id());
    }

    public boolean requirementsMet(Party party, ShopItem item) {
        return item.requires().stream().allMatch(req -> hasApp(party, req));
    }

    public ActionResult buy(CaricraftState state, UUID player, String itemId) {
        PlayerProfile profile = state.profiles().get(player);
        Party party = state.partyOf(player);
        if (profile == null || party == null) {
            return ActionResult.fail("no_profile");
        }
        ShopItem item = catalog.get(itemId).orElse(null);
        if (item == null) {
            return ActionResult.fail("unknown_item");
        }
        if (item.builtin()) {
            return ActionResult.fail("already_owned");
        }
        if (item.comingSoon()) {
            return ActionResult.fail("coming_soon");
        }
        if (owns(party, profile, item)) {
            return ActionResult.fail("already_owned");
        }
        if (!requirementsMet(party, item)) {
            return ActionResult.fail("requirements_missing");
        }
        if (item.price() > 0) {
            EconomyService.Result result = economy.debit(party.wallet(), item.price(), "purchase", item.id());
            if (result == EconomyService.Result.INSUFFICIENT_FUNDS) {
                return ActionResult.fail("insufficient_funds", item.price() - party.wallet().balance());
            }
            if (result != EconomyService.Result.OK) {
                return ActionResult.fail("invalid_amount");
            }
        }
        if (item.isUpgrade()) {
            party.upgradeLevels().merge(item.id(), 1, Integer::sum);
        } else if (item.ownership() == ShopItem.Ownership.PARTY) {
            party.ownedApps().add(item.id());
            if (item.kind() == ShopItem.Kind.APP) {
                chronicle.add(party, ChronicleEntry.APP_PURCHASED, player, profile.name(), item.id());
            }
        } else {
            profile.ownedCosmetics().add(item.id());
        }
        party.incrementStat(STAT_PURCHASES, 1);
        return ActionResult.ok("purchased", item.id(), item.price());
    }

    /** Selecciona un tema propio o el clásico. */
    public ActionResult selectTheme(CaricraftState state, UUID player, String themeId) {
        PlayerProfile profile = state.profiles().get(player);
        if (profile == null) {
            return ActionResult.fail("no_profile");
        }
        if (PlayerProfile.DEFAULT_THEME.equals(themeId)) {
            profile.setTheme(themeId);
            return ActionResult.ok("theme_selected", themeId);
        }
        ShopItem item = catalog.get(themeId).orElse(null);
        if (item == null || !item.isTheme()) {
            return ActionResult.fail("unknown_item");
        }
        if (!profile.ownedCosmetics().contains(themeId)) {
            return ActionResult.fail("not_owned");
        }
        profile.setTheme(themeId);
        return ActionResult.ok("theme_selected", themeId);
    }
}
