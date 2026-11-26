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
            if (pokemon != null) {
                result.add(describe(pokemon, slot));
            }
        }
        return result;
    }

    private static PartyMon describe(Pokemon pokemon, int slot) {
        int ivTotal = 0;
        for (Stats stat : IV_STATS) {
            Integer iv = pokemon.getIvs().get(stat);
            ivTotal += iv == null ? 0 : iv;
        }
        boolean legendary = pokemon.isLegendary() || pokemon.isMythical() || pokemon.isUltraBeast();
        String species = pokemon.getSpecies().getResourceIdentifier().toString();
        String nickname = pokemon.getNickname() == null ? "" : pokemon.getNickname().getString();
        return new PartyMon(slot, pokemon.getUuid(), species, nickname, pokemon.getLevel(), pokemon.isFainted(),
                new PokemonPricing.PokemonInfo(species, pokemon.getLevel(), pokemon.getSpecies().getCatchRate(), ivTotal,
                        pokemon.getShiny(), legendary), List.copyOf(pokemon.getAspects()));
    }

    @Override
    public PartyMon describe(Object pokemon) {
        return describe((Pokemon) pokemon, -1);
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
    public Object create(String species, int level, boolean shiny, int perfectIvs, List<String> moves) {
        Pokemon pokemon = PokemonProperties.Companion.parse("species=" + species + " level=" + level + (shiny ? " shiny=true" : ""))
                .create();
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
        return pokemon;
    }

    @Override
    public String give(ServerPlayer player, String species, int level, boolean shiny, int perfectIvs, List<String> moves) {
        Pokemon pokemon = (Pokemon) create(species, level, shiny, perfectIvs, moves);
        store(player).add(pokemon);
        return pokemon.getSpecies().getResourceIdentifier().toString();
    }

    @Override
    public net.minecraft.nbt.CompoundTag save(Object pokemon, net.minecraft.core.RegistryAccess registries) {
        return ((Pokemon) pokemon).saveToNBT(registries, new net.minecraft.nbt.CompoundTag());
    }

    @Override
    public Object load(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.RegistryAccess registries) {
        return Pokemon.Companion.loadFromNBT(registries, tag);
    }

    @Override
    public String randomSpecies(java.util.Random random) {
        List<com.cobblemon.mod.common.pokemon.Species> pool = PokemonSpecies.getImplemented().stream()
                .filter(s -> java.util.Collections.disjoint(s.getLabels(), java.util.Set.of("legendary", "mythical", "ultra_beast", "paradox")))
                .toList();
        return pool.get(random.nextInt(pool.size())).getResourceIdentifier().getPath();
    }

    private static Pokemon find(ServerPlayer player, UUID uuid) {
        PlayerPartyStore party = store(player);
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null && pokemon.getUuid().equals(uuid)) {
                return pokemon;
            }
        }
        return null;
    }

    @Override
    public Optional<int[]> ivs(ServerPlayer player, UUID uuid) {
        Pokemon pokemon = find(player, uuid);
        if (pokemon == null) {
            return Optional.empty();
        }
        int[] ivs = new int[IV_STATS.size()];
        for (int i = 0; i < ivs.length; i++) {
            Integer iv = pokemon.getIvs().get(IV_STATS.get(i));
            ivs[i] = iv == null ? 0 : iv;
        }
        return Optional.of(ivs);
    }

    @Override
    public boolean improve(ServerPlayer player, UUID uuid, String kind, int value) {
        Pokemon pokemon = find(player, uuid);
        if (pokemon == null) {
            return false;
        }
        switch (kind) {
            case "iv" -> pokemon.getIvs().set(IV_STATS.get(value), 31);
            case "level" -> pokemon.setLevel(value);
            case "shiny" -> pokemon.setShiny(true);
            default -> throw new IllegalArgumentException("Mejora desconocida: " + kind);
        }
        return true;
    }

    @Override
    public net.minecraft.world.entity.Entity spawnWild(net.minecraft.server.level.ServerLevel level, String species, int pokemonLevel,
                                                       boolean shiny, double x, double y, double z) {
        com.cobblemon.mod.common.entity.pokemon.PokemonEntity entity = PokemonProperties.Companion
                .parse("species=" + species + " level=" + pokemonLevel + (shiny ? " shiny=true" : "")).createEntity(level);
        entity.getPokemon().setShiny(shiny);
        entity.moveTo(x, y, z, level.getRandom().nextFloat() * 360f, 0f);
        return level.addFreshEntity(entity) ? entity : null;
    }

    @Override
    public boolean isBattling(net.minecraft.world.entity.Entity entity) {
        return entity instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemon && pokemon.isBattling();
    }

    @Override
    public UUID pokemonId(net.minecraft.world.entity.Entity entity) {
        return entity instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemon ? pokemon.getPokemon().getUuid() : null;
    }

    @Override
    public boolean owns(ServerPlayer player, UUID pokemonId) {
        return store(player).get(pokemonId) != null || Cobblemon.INSTANCE.getStorage().getPC(player).get(pokemonId) != null;
    }

    @Override
    public boolean speciesExists(String species) {
        return PokemonSpecies.getByName(species) != null;
    }
}
