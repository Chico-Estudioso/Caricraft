package com.chicoestudioso.caricraft.server.cobbleverse;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.cobbleverse.ExpeditionService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.integration.cobbledollars.CobbleDollarsBridge;
import com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import com.chicoestudioso.caricraft.server.PhoneActionHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Expediciones: un Pokémon del equipo se va unas horas reales a un destino y vuelve con algo de dinero y objetos.
 * Es un ingreso pasivo pequeño a propósito (menos de lo que se saca jugando una hora) y el Pokémon no se puede usar
 * mientras está fuera.
 */
public final class ExpeditionManager {
    private static final Random RANDOM = new Random();
    private static final long HOUR_MS = 3_600_000L;
    private static long ticks;

    private ExpeditionManager() {
    }

    private static boolean enabled(CaricraftServer mod) {
        return CobbleverseManager.active(mod) && mod.core().expeditions().enabled();
    }

    /** {@code spec}: "destino:horas" (p. ej. {@code cave:4}). */
    public static ActionResult start(CaricraftServer mod, ServerPlayer player, String spec, String pokemonId) {
        if (!enabled(mod)) {
            return ActionResult.fail("cv_disabled");
        }
        String destinationId;
        int hours;
        UUID uuid;
        try {
            int sep = spec.lastIndexOf(':');
            destinationId = spec.substring(0, sep);
            hours = Integer.parseInt(spec.substring(sep + 1));
            uuid = UUID.fromString(pokemonId);
        } catch (RuntimeException e) {
            return ActionResult.fail("invalid_request");
        }
        ExpeditionService service = mod.core().expeditions();
        ExpeditionData data = ExpeditionData.get(mod.server());
        PlayerProfile profile = mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        long day = mod.core().cobbleverse().day();
        ActionResult check = service.canStart(profile, day, data.get(player.getUUID()) != null, destinationId, hours);
        if (!check.success()) {
            return check;
        }
        PokemonBackend backend = CobblemonCompat.backend();
        List<PokemonBackend.PartyMon> mons = backend.party(player);
        PokemonBackend.PartyMon mon = mons.stream().filter(m -> m.uuid().equals(uuid)).findFirst().orElse(null);
        if (mon == null) {
            return ActionResult.fail("cv_pokemon_gone");
        }
        if (backend.inBattle(player)) {
            return ActionResult.fail("cv_exp_blocked_in_battle");
        }
        if (mons.stream().filter(m -> !m.fainted()).count() <= 1 && !mon.fainted()) {
            return ActionResult.fail("cv_exp_blocked_last");
        }
        if (mon.fainted()) {
            return ActionResult.fail("cv_exp_blocked_fainted");
        }
        Optional<Object> taken = backend.take(player, uuid);
        if (taken.isEmpty()) {
            return ActionResult.fail("cv_pokemon_gone");
        }
        long now = System.currentTimeMillis();
        try {
            data.put(player.getUUID(), new ExpeditionData.Away(destinationId, hours, now, now + hours * HOUR_MS,
                    backend.save(taken.get(), mod.server().registryAccess()), mon.species(), mon.level(), mon.info().shiny(),
                    mon.aspects(), mon.types(), false));
            saveNow(mod.server());
        } catch (RuntimeException e) {
            data.remove(player.getUUID());
            backend.restore(player, taken.get());
            CaricraftMod.LOGGER.error("No se pudo mandar de expedición al Pokémon de {}; se devuelve.", player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        service.recordStart(profile, day);
        player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        mod.markDirty();
        return ActionResult.ok("cv_exp_started", mon.species(), "caricraft.phone.cv.exp." + destinationId, hours);
    }

    public static ActionResult claim(CaricraftServer mod, ServerPlayer player) {
        return finish(mod, player, false);
    }

    public static ActionResult cancel(CaricraftServer mod, ServerPlayer player) {
        return finish(mod, player, true);
    }

    private static ActionResult finish(CaricraftServer mod, ServerPlayer player, boolean cancel) {
        if (!CobbleverseManager.active(mod)) {
            return ActionResult.fail("cv_disabled");
        }
        ExpeditionData data = ExpeditionData.get(mod.server());
        ExpeditionData.Away away = data.get(player.getUUID());
        if (away == null) {
            return ActionResult.fail("cv_exp_none");
        }
        long now = System.currentTimeMillis();
        if (!cancel && now < away.end) {
            return ActionResult.fail("cv_exp_not_ready", Math.max(1, (away.end - now + 59_999) / 60_000));
        }
        PokemonBackend backend = CobblemonCompat.backend();
        Object pokemon;
        try {
            pokemon = backend.load(away.pokemon, mod.server().registryAccess());
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.error("No se pudo cargar el Pokémon de expedición de {}; se conserva en los datos.",
                    player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        data.remove(player.getUUID());
        try {
            backend.restore(player, pokemon);
        } catch (RuntimeException e) {
            data.put(player.getUUID(), away);
            CaricraftMod.LOGGER.error("No se pudo devolver el Pokémon de expedición a {}", player.getGameProfile().getName(), e);
            return ActionResult.fail("internal_error");
        }
        saveNow(mod.server());
        mod.markDirty();
        if (cancel) {
            return ActionResult.ok("cv_exp_cancelled", away.species);
        }
        ExpeditionService.Destination destination = ExpeditionService.destination(away.destination);
        ExpeditionService.Reward reward = destination == null ? new ExpeditionService.Reward(0, Map.of())
                : mod.core().expeditions().roll(destination, away.hours, away.level,
                ExpeditionService.typeMatch(destination, away.types), RANDOM);
        long dollars = reward.dollars();
        if (dollars > 0 && !CobbleDollarsBridge.add(player, dollars)) {
            PlayerProfile profile = mod.core().state().profiles().get(player.getUUID());
            if (profile != null) {
                profile.setPendingDollars(profile.pendingDollars() + dollars);
            }
        }
        int given = 0;
        for (Map.Entry<String, Integer> item : reward.items().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(item.getKey());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                PhoneActionHandler.giveItemTo(player, item.getKey(), item.getValue());
                given += item.getValue();
            }
        }
        Party party = mod.core().partyOf(player.getUUID());
        if (party != null) {
            party.incrementStat("expeditions", 1);
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.5f);
        return ActionResult.ok("cv_exp_back", away.species, dollars, given);
    }

    /** Cada 30 s avisa a quien tenga una expedición recién terminada. */
    public static void tick(MinecraftServer server, CaricraftServer mod) {
        if (++ticks % 600 != 0 || !enabled(mod)) {
            return;
        }
        ExpeditionData data = ExpeditionData.get(server);
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ExpeditionData.Away> entry : data.all().entrySet()) {
            ExpeditionData.Away away = entry.getValue();
            if (away.notified || now < away.end) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                away.notified = true;
                data.setDirty();
                mod.notify(player, true, "caricraft.notify.expedition_back", List.of("cobblemon:" + plain(away.species)));
                mod.sendSnapshot(player);
            }
        }
    }

    /** Administración/QA: termina ya todas las expediciones. */
    public static int finishAll(MinecraftServer server) {
        ExpeditionData data = ExpeditionData.get(server);
        long now = System.currentTimeMillis();
        int n = 0;
        for (Map.Entry<UUID, ExpeditionData.Away> entry : data.all().entrySet()) {
            entry.setValue(entry.getValue().withEnd(now - 1));
            n++;
        }
        data.setDirty();
        return n;
    }

    private static String plain(String species) {
        int i = species.indexOf(':');
        return i < 0 ? species : species.substring(i + 1);
    }

    private static void saveNow(MinecraftServer server) {
        try {
            server.overworld().getDataStorage().save();
        } catch (RuntimeException e) {
            CaricraftMod.LOGGER.warn("No se pudieron guardar ya los datos de expediciones (se guardarán con el mundo)", e);
        }
    }

    static PhoneSnapshot.ExpeditionView view(CaricraftServer mod, ServerPlayer player, PlayerProfile profile) {
        if (!enabled(mod)) {
            return null;
        }
        ExpeditionService service = mod.core().expeditions();
        List<PhoneSnapshot.DestinationView> destinations = new ArrayList<>();
        for (ExpeditionService.Destination d : ExpeditionService.DESTINATIONS) {
            destinations.add(new PhoneSnapshot.DestinationView(d.id(), d.icon(), d.types(),
                    d.loot().stream().map(ExpeditionService.Loot::item).toList()));
        }
        ExpeditionData.Away away = ExpeditionData.get(mod.server()).get(player.getUUID());
        PhoneSnapshot.AwayView awayView = away == null ? null : new PhoneSnapshot.AwayView(away.species, away.aspects, away.shiny,
                away.level, away.destination, away.hours, away.end, System.currentTimeMillis() >= away.end);
        return new PhoneSnapshot.ExpeditionView(destinations, ExpeditionService.HOURS, service.perDay(),
                service.startedToday(profile, mod.core().cobbleverse().day()), awayView);
    }
}
