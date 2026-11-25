package com.chicoestudioso.caricraft.server.cobbleverse;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Gift;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.Offer;
import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService;
import com.chicoestudioso.caricraft.core.cobbleverse.PokemonSpec;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.integration.cobbledollars.CobbleDollarsBridge;
import com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;

/**
 * Parte de servidor de Cobbleverse: regalo misterioso, tienda en CobbleDollars y venta de Pokémon. Todo se
 * valida aquí (el teléfono solo pide); si algo falla a medias, se deshace (se devuelve el dinero o el Pokémon).
 */
public final class CobbleverseManager {
    private static final Random RANDOM = new Random();
    /** Último intercambio prodigioso de cada jugador (solo para la animación del teléfono). */
    private static final java.util.Map<UUID, PhoneSnapshot.WonderView> LAST_WONDER = new java.util.HashMap<>();

    private CobbleverseManager() {
    }

    /** Activado en la configuración y con Cobblemon instalado. */
    public static boolean active(CaricraftServer mod) {
        return mod.core().cobbleverse().enabled() && CobblemonCompat.backend() != null;
    }

    private static PlayerProfile profile(CaricraftServer mod, ServerPlayer player) {
        return mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
    }

    // ------------------------------------------------------------------ teléfono

    public static PhoneSnapshot.CobbleverseView view(CaricraftServer mod, ServerPlayer player) {
        if (!active(mod)) {
            return null;
        }
        CobbleverseService service = mod.core().cobbleverse();
        var config = mod.core().config().cobbleverse;
        PlayerProfile profile = profile(mod, player);
        long day = service.day();
        OptionalLong balance = CobbleDollarsBridge.balance(player);

        List<PhoneSnapshot.CvOfferView> offers = new ArrayList<>();
        if (config.shop) {
            for (Offer offer : service.offersOfDay(day)) {
                PokemonSpec spec = offer.pokemon();
                offers.add(new PhoneSnapshot.CvOfferView(offer.id(), offer.iconOrDefault(), offer.item(), offer.countOrOne(),
                        spec == null ? List.of() : spec.speciesList(), spec == null ? 0 : spec.levelOrDefault(),
                        spec != null && spec.alwaysShiny(), spec == null ? 0 : spec.perfectIvsOrDefault(),
                        spec == null ? List.of() : spec.movesList(), offer.price(), service.boughtToday(profile, offer.id(), day),
                        offer.dailyLimitOrDefault()));
            }
        }
        List<PhoneSnapshot.SellView> party = new ArrayList<>();
        if (config.sellPokemon) {
            PokemonBackend backend = CobblemonCompat.backend();
            List<PokemonBackend.PartyMon> mons = backend.party(player);
            boolean inBattle = backend.inBattle(player);
            for (PokemonBackend.PartyMon mon : mons) {
                party.add(new PhoneSnapshot.SellView(mon.uuid().toString(), mon.species(), mon.nickname(), mon.level(),
                        mon.info().shiny(), mon.info().ivTotal(), service.sellPrice(mon.info()),
                        service.sellBlocked(mon.info(), mons.size() <= 1, inBattle), mon.aspects()));
            }
        } else if (config.wonderTrade) {
            PokemonBackend backend = CobblemonCompat.backend();
            List<PokemonBackend.PartyMon> mons = backend.party(player);
            boolean inBattle = backend.inBattle(player);
            for (PokemonBackend.PartyMon mon : mons) {
                party.add(new PhoneSnapshot.SellView(mon.uuid().toString(), mon.species(), mon.nickname(), mon.level(),
                        mon.info().shiny(), mon.info().ivTotal(), 0, service.sellBlocked(mon.info(), mons.size() <= 1, inBattle),
                        mon.aspects()));
            }
        }
        return new PhoneSnapshot.CobbleverseView(CobbleDollarsBridge.available(), balance.orElse(-1),
                config.mysteryGift, service.giftAvailable(profile, day), service.nextDayAt(),
                config.shop, offers, config.sellPokemon, party, service.salesToday(profile, day), service.maxSalesPerDay(),
                config.wonderTrade, service.wondersLeft(profile, day), config.wonderTradesPerDay,
                WonderTradeData.get(mod.server()).entries().size(), LAST_WONDER.get(player.getUUID()));
    }

    // ------------------------------------------------------------------ regalo misterioso

    public static ActionResult claimGift(CaricraftServer mod, ServerPlayer player) {
        if (!active(mod)) {
            return ActionResult.fail("cv_disabled");
        }
        CobbleverseService service = mod.core().cobbleverse();
        PlayerProfile profile = profile(mod, player);
        long day = service.day();
        if (!service.giftAvailable(profile, day)) {
            return ActionResult.fail("gift_claimed");
        }
        Gift gift = service.pickGift(RANDOM);
        for (int i = 0; i < 10 && gift.kind() == Gift.Kind.COBBLEDOLLARS && !CobbleDollarsBridge.available(); i++) {
            gift = service.pickGift(RANDOM);
        }
        ActionResult result;
        try {
            result = deliverGift(mod, player, gift);
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("No se pudo entregar el regalo misterioso {} a {}", gift.id(), player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        if (result.success()) {
            service.recordGift(profile, day);
            Party party = mod.core().partyOf(player.getUUID());
            if (party != null) {
                party.incrementStat("mystery_gifts", 1);
            }
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
            mod.markDirty();
        }
        return result;
    }

    private static ActionResult deliverGift(CaricraftServer mod, ServerPlayer player, Gift gift) {
        return switch (gift.kind()) {
            case ITEM -> {
                int count = gift.amount() == null ? 1 : gift.amount().roll(RANDOM);
                com.chicoestudioso.caricraft.server.PhoneActionHandler.giveItemTo(player, gift.item(), count);
                yield ActionResult.ok("gift_item", count, gift.item());
            }
            case POKEMON -> {
                PokemonSpec spec = gift.pokemon();
                boolean shiny = spec.rollShiny(RANDOM);
                String species = CobblemonCompat.backend().give(player, spec.pickSpecies(RANDOM), spec.levelOrDefault(), shiny,
                        spec.perfectIvsOrDefault(), spec.movesList());
                yield ActionResult.ok(shiny ? "gift_pokemon_shiny" : "gift_pokemon", species);
            }
            case CARINS -> {
                int amount = gift.carins().roll(RANDOM);
                Party party = mod.core().partyOf(player.getUUID());
                if (party == null || mod.core().economy().credit(party.wallet(), amount, "mystery_gift", "", null)
                        != EconomyService.Result.OK) {
                    yield ActionResult.fail("internal_error");
                }
                yield ActionResult.ok("gift_carins", amount);
            }
            case COBBLEDOLLARS -> {
                int amount = gift.cobbledollars().roll(RANDOM);
                yield CobbleDollarsBridge.add(player, amount) ? ActionResult.ok("gift_cobbledollars", amount)
                        : ActionResult.fail("cv_no_cobbledollars");
            }
        };
    }

    // ------------------------------------------------------------------ tienda

    public static ActionResult buy(CaricraftServer mod, ServerPlayer player, String offerId) {
        if (!active(mod) || !mod.core().config().cobbleverse.shop) {
            return ActionResult.fail("cv_disabled");
        }
        CobbleverseService service = mod.core().cobbleverse();
        CobbleverseCatalog catalog = service.catalog();
        Offer offer = catalog.offer(offerId);
        OptionalLong balance = CobbleDollarsBridge.balance(player);
        if (offer == null) {
            return ActionResult.fail("cv_offer_gone");
        }
        if (balance.isEmpty()) {
            return ActionResult.fail("cv_no_cobbledollars");
        }
        PlayerProfile profile = profile(mod, player);
        long day = service.day();
        ActionResult check = service.canBuy(profile, offerId, day, balance.getAsLong());
        if (!check.success()) {
            return check;
        }
        if (!CobbleDollarsBridge.remove(player, offer.price())) {
            return ActionResult.fail("cv_insufficient", offer.price() - balance.getAsLong());
        }
        ActionResult delivered;
        try {
            if (offer.isPokemon()) {
                PokemonSpec spec = offer.pokemon();
                String species = CobblemonCompat.backend().give(player, spec.pickSpecies(RANDOM), spec.levelOrDefault(),
                        spec.rollShiny(RANDOM), spec.perfectIvsOrDefault(), spec.movesList());
                delivered = ActionResult.ok("cv_bought_pokemon", species, offer.price());
            } else {
                com.chicoestudioso.caricraft.server.PhoneActionHandler.giveItemTo(player, offer.item(), offer.countOrOne());
                delivered = ActionResult.ok("cv_bought_item", offer.countOrOne(), offer.item(), offer.price());
            }
        } catch (RuntimeException e) {
            CobbleDollarsBridge.add(player, offer.price());
            CaricraftMod.LOGGER.error("No se pudo entregar la oferta {} a {}; se devuelve el dinero.", offerId,
                    player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        service.recordPurchase(profile, offerId, day);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.0f);
        mod.markDirty();
        return delivered;
    }

    // ------------------------------------------------------------------ intercambio prodigioso

    public static ActionResult wonderTrade(CaricraftServer mod, ServerPlayer player, String pokemonId) {
        if (!active(mod) || !mod.core().config().cobbleverse.wonderTrade) {
            return ActionResult.fail("cv_disabled");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(pokemonId);
        } catch (IllegalArgumentException e) {
            return ActionResult.fail("invalid_request");
        }
        CobbleverseService service = mod.core().cobbleverse();
        PokemonBackend backend = CobblemonCompat.backend();
        List<PokemonBackend.PartyMon> mons = backend.party(player);
        PokemonBackend.PartyMon offered = mons.stream().filter(m -> m.uuid().equals(uuid)).findFirst().orElse(null);
        if (offered == null) {
            return ActionResult.fail("cv_pokemon_gone");
        }
        String blocked = service.sellBlocked(offered.info(), mons.size() <= 1, backend.inBattle(player));
        if (blocked != null) {
            return ActionResult.fail("cv_wonder_blocked_" + blocked);
        }
        PlayerProfile profile = profile(mod, player);
        long day = service.day();
        ActionResult check = service.canWonderTrade(profile, day);
        if (!check.success()) {
            return check;
        }
        WonderTradeData pool = WonderTradeData.get(mod.server());
        var registries = mod.server().registryAccess();
        Optional<Object> taken = backend.take(player, uuid);
        if (taken.isEmpty()) {
            return ActionResult.fail("cv_pokemon_gone");
        }
        Object received;
        WonderTradeData.Entry fromPool = null;
        try {
            int index = CobbleverseService.pickFromPool(pool.owners(), player.getUUID(), RANDOM);
            if (index >= 0) {
                fromPool = pool.take(index);
                received = backend.load(fromPool.pokemon(), registries);
            } else {
                received = backend.create(backend.randomSpecies(RANDOM), CobbleverseService.wonderLevel(offered.level(), RANDOM),
                        RANDOM.nextDouble() < service.wonderShinyChance(), 2, List.of());
            }
            pool.add(new WonderTradeData.Entry(player.getUUID(), player.getGameProfile().getName(), System.currentTimeMillis(),
                    backend.save(taken.get(), registries)), mod.core().config().cobbleverse.wonderPoolSize);
            backend.restore(player, received);
        } catch (RuntimeException e) {
            backend.restore(player, taken.get());
            CaricraftMod.LOGGER.error("Falló el intercambio prodigioso de {}; se devuelve su Pokémon.", player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        service.recordWonderTrade(profile, day);
        Party party = mod.core().partyOf(player.getUUID());
        if (party != null) {
            party.incrementStat("wonder_trades", 1);
        }
        PokemonBackend.PartyMon got = backend.describe(received);
        String from = fromPool == null ? "" : fromPool.ownerName();
        LAST_WONDER.put(player.getUUID(), new PhoneSnapshot.WonderView(got.species(), got.aspects(), got.level(), got.info().shiny(),
                from, offered.species(), System.currentTimeMillis()));
        if (fromPool != null) {
            ServerPlayer owner = mod.server().getPlayerList().getPlayer(fromPool.owner());
            if (owner != null) {
                mod.notify(owner, true, "caricraft.notify.wonder_arrived",
                        List.of(got.species(), String.valueOf(got.level()), player.getGameProfile().getName()));
            }
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, got.info().shiny() ? 2.0f : 1.2f);
        mod.markDirty();
        return ActionResult.ok(got.info().shiny() ? "cv_wonder_shiny" : "cv_wonder", got.species(), got.level(),
                from.isEmpty() ? "caricraft.phone.cv.wonder_far" : from);
    }

    public static void forget(UUID player) {
        LAST_WONDER.remove(player);
    }

    // ------------------------------------------------------------------ venta

    public static ActionResult sell(CaricraftServer mod, ServerPlayer player, String pokemonId) {
        if (!active(mod) || !mod.core().config().cobbleverse.sellPokemon) {
            return ActionResult.fail("cv_disabled");
        }
        if (!CobbleDollarsBridge.available()) {
            return ActionResult.fail("cv_no_cobbledollars");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(pokemonId);
        } catch (IllegalArgumentException e) {
            return ActionResult.fail("invalid_request");
        }
        CobbleverseService service = mod.core().cobbleverse();
        PokemonBackend backend = CobblemonCompat.backend();
        List<PokemonBackend.PartyMon> mons = backend.party(player);
        PokemonBackend.PartyMon mon = mons.stream().filter(m -> m.uuid().equals(uuid)).findFirst().orElse(null);
        if (mon == null) {
            return ActionResult.fail("cv_pokemon_gone");
        }
        String blocked = service.sellBlocked(mon.info(), mons.size() <= 1, backend.inBattle(player));
        if (blocked != null) {
            return ActionResult.fail("cv_sell_blocked_" + blocked);
        }
        PlayerProfile profile = profile(mod, player);
        long day = service.day();
        ActionResult check = service.canSell(profile, day);
        if (!check.success()) {
            return check;
        }
        long price = service.sellPrice(mon.info());
        Optional<Object> taken = backend.take(player, uuid);
        if (taken.isEmpty()) {
            return ActionResult.fail("cv_pokemon_gone");
        }
        if (!CobbleDollarsBridge.add(player, price)) {
            backend.restore(player, taken.get());
            return ActionResult.fail("cv_no_cobbledollars");
        }
        service.recordSale(profile, day);
        Party party = mod.core().partyOf(player.getUUID());
        if (party != null) {
            party.incrementStat("pokemon_sold", 1);
        }
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        mod.markDirty();
        return ActionResult.ok("cv_sold", mon.species(), price);
    }
}
