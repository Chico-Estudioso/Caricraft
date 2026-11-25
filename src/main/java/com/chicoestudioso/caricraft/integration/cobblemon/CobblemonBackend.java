package com.chicoestudioso.caricraft.integration.cobblemon;

import com.chicoestudioso.caricraft.CaricraftMod;
import com.chicoestudioso.caricraft.core.cobbleverse.PokemonPricing;
import com.chicoestudioso.caricraft.server.cobbleverse.PokemonBackend;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link PokemonBackend} con la API de Cobblemon 1.8.4 (verificada en el JAR). */
final class CobblemonBackend implements PokemonBackend {
    private static final List<Stats> IV_STATS = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE, Stats.SPEED);

    private static PlayerPartyStore store(ServerPlayer player) {
        return Cobblemon.INSTANCE.getStorage().getParty(player);
    }

    @Override
    public List<PartyMon> party(ServerPlayer player) {
        PlayerPartyStore party = store(player);
        List<PartyMon> result = new ArrayList<>();
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon == null) {
                continue;
            }
            int ivTotal = 0;
            for (Stats stat : IV_STATS) {
                Integer iv = pokemon.getIvs().get(stat);
                ivTotal += iv == null ? 0 : iv;
            }
            boolean legendary = pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast();
            String species = pokemon.getSpecies().getResourceIdentifier().toString();
            String nickname = pokemon.getNickname() == null ? "" : pokemon.getNickname().getString();
            result.add(new PartyMon(slot, pokemon.getUuid(), species, nickname, pokemon.getLevel(), pokemon.isFainted(),
                    new PokemonPricing.PokemonInfo(species, pokemon.getLevel(), pokemon.getSpecies().getCatchRate(), ivTotal,
                            pokemon.getShiny(), legendary)));
        }
        return result;
    }

    @Override
    public boolean inBattle(ServerPlayer player) {
        return BattleRegistry.getBattleByParticipatingPlayer(player) != null;
    }

    @Override
    public Optional<Object> take(ServerPlayer player, UUID uuid) {
        PlayerPartyStore party = store(player);
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null && pokemon.getUuid().equals(uuid)) {
                if (pokemon.getEntity() != null) {
                    pokemon.getEntity().discard();
                }
                return party.remove(pokemon) ? Optional.of(pokemon) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public void restore(ServerPlayer player, Object taken) {
        if (taken instanceof Pokemon pokemon) {
            store(player).add(pokemon);
        }
    }

    @Override
    public String give(ServerPlayer player, String species, int level, boolean shiny, int perfectIvs, List<String> moves) {
        Pokemon pokemon = PokemonProperties.Companion.parse("species=" + species + " level=" + level + (shiny ? " shiny=true" : ""))
                .create(player);
        pokemon.setShiny(shiny);
        List<Stats> stats = new ArrayList<>(IV_STATS);
        Collections.shuffle(stats);
        for (int i = 0; i < Math.min(perfectIvs, stats.size()); i++) {
            pokemon.getIvs().set(stats.get(i), 31);
        }
        for (int i = 0; i < Math.min(4, moves.size()); i++) {
            MoveTemplate template = Moves.getByName(moves.get(i));
            if (template == null) {
                CaricraftMod.LOGGER.warn("Movimiento desconocido en el catálogo de Cobbleverse: {}", moves.get(i));
                continue;
            }
            pokemon.getMoveSet().setMove(i, template.create());
        }
        store(player).add(pokemon);
        return pokemon.getSpecies().getResourceIdentifier().toString();
    }

    @Override
    public boolean speciesExists(String species) {
        return PokemonSpecies.getByName(species) != null;
    }
}
