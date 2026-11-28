package com.chicoestudioso.caricraft.server.cobbleverse;

import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aviso de variocolor al dúo: cuando aparece un Pokémon variocolor salvaje cerca de uno de los dos, a ambos les
 * llega un aviso y una brújula en la barra de acción (sobre la barra de objetos, sin tapar la flecha de CobbleNav)
 * que señala dónde está hasta que alguien lo captura, desaparece o pasan unos minutos. Solo lee; no toca al Pokémon.
 */
public final class ShinyAlerts {
    /** Distancia máxima a un miembro del dúo para avisar. */
    private static final double NEAR_RANGE = 160;
    private static final double ARRIVED_RANGE = 6;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final class Alert {
        final UUID entity;
        final ServerLevel level;
        final String species;
        final int pokemonLevel;
        final UUID partyId;
        final String near;
        final long expiresAt;
        final Set<UUID> arrived = new HashSet<>();
        double x;
        double y;
        double z;

        Alert(UUID entity, ServerLevel level, String species, int pokemonLevel, UUID partyId, String near, long expiresAt, Entity at) {
            this.entity = entity;
            this.level = level;
            this.species = species;
            this.pokemonLevel = pokemonLevel;
            this.partyId = partyId;
            this.near = near;
            this.expiresAt = expiresAt;
            this.x = at.getX();
            this.y = at.getY();
            this.z = at.getZ();
        }
    }

    private static final Map<UUID, Alert> ALERTS = new LinkedHashMap<>();
    private static long ticks;

    private ShinyAlerts() {
    }

    /** Un variocolor salvaje acaba de aparecer (lo llama el adaptador de Cobblemon o la prueba de administración). */
    public static void onShinySpawn(CaricraftServer mod, ServerLevel level, Entity entity, String species, int pokemonLevel) {
        var config = mod.core().config().cobbleverse;
        if (!config.enabled || !config.shinyAlerts || ALERTS.containsKey(entity.getUUID())) {
            return;
        }
        ServerPlayer nearest = null;
        double best = NEAR_RANGE * NEAR_RANGE * 4; // el mayor alcance posible (radar a tope); luego se mira el del grupo
        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(entity);
            if (d < best && mod.core().partyOf(player.getUUID()) != null) {
                best = d;
                nearest = player;
            }
        }
        if (nearest == null) {
            return;
        }
        Party party = mod.core().partyOf(nearest.getUUID());
        int radar = party.upgradeLevel(com.chicoestudioso.caricraft.core.shop.AppCatalog.CC_RADAR);
        double range = range(radar);
        if (best > range * range) {
            return; // más lejos de lo que alcanza el radar de ese dúo
        }
        Alert alert = new Alert(entity.getUUID(), level, species, pokemonLevel, party.id(), nearest.getGameProfile().getName(),
                System.currentTimeMillis() + minutes(config.shinyAlertMinutes, radar) * 60_000L, entity);
        ALERTS.put(alert.entity, alert);
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player == null) {
                continue;
            }
            if (player.level() == level) {
                double dx = alert.x - player.getX();
                double dz = alert.z - player.getZ();
                int octant = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
                mod.notify(player, true, "caricraft.notify.shiny_nearby", List.of(species, String.valueOf(Math.round(Math.sqrt(dx * dx + dz * dz))),
                        "caricraft.dir." + octant, alert.near));
            } else {
                mod.notify(player, true, "caricraft.notify.shiny_nearby_far", List.of(species, alert.near));
            }
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.6f);
        }
        mod.sendPartySnapshots(nearest.getUUID());
    }

    /** Alcance del aviso con la mejora «Radar variocolor»: 160, 240 o 320 bloques. */
    public static double range(int radar) {
        return NEAR_RANGE + 80 * Math.max(0, Math.min(2, radar));
    }

    /** Minutos que dura el aviso: los de la configuración, +3 o +7 con el radar. */
    public static int minutes(int base, int radar) {
        return base + switch (Math.max(0, Math.min(2, radar))) {
            case 1 -> 3;
            case 2 -> 7;
            default -> 0;
        };
    }

    /** Cada tick: cada medio segundo se actualiza la brújula; las alertas caducan o terminan si el Pokémon ya no está. */
    public static void tick(MinecraftServer server, CaricraftServer mod) {
        if (ALERTS.isEmpty() || ++ticks % 10 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Alert> it = ALERTS.values().iterator(); it.hasNext(); ) {
            Alert alert = it.next();
            Party party = mod.core().state().parties().get(alert.partyId);
            Entity entity = alert.level.getEntity(alert.entity);
            boolean gone = entity == null || !entity.isAlive();
            if (party == null || now > alert.expiresAt || (gone && alert.level.isLoaded(net.minecraft.core.BlockPos.containing(alert.x, alert.y, alert.z)))) {
                it.remove(); // capturado, debilitado, desaparecido o caducado
                if (party != null) {
                    clearActionBar(mod, party, alert);
                    mod.sendPartySnapshots(party.members().iterator().next());
                }
                continue;
            }
            if (!gone) {
                alert.x = entity.getX();
                alert.y = entity.getY();
                alert.z = entity.getZ();
            }
            for (UUID member : party.members()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                PlayerProfile profile = mod.core().state().profiles().get(member);
                if (player == null || player.level() != alert.level || alert.arrived.contains(member)
                        || (profile != null && !profile.shinyCompass())) {
                    continue;
                }
                double dx = alert.x - player.getX();
                double dz = alert.z - player.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz + Math.pow(alert.y - player.getY(), 2));
                Component name = Component.translatable("cobblemon.species." + alert.species.substring(alert.species.indexOf(':') + 1) + ".name");
                if (distance <= ARRIVED_RANGE) {
                    alert.arrived.add(member);
                    player.displayClientMessage(Component.translatable("caricraft.cobbleverse.shiny_here", name)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                    continue;
                }
                // Flecha relativa a hacia dónde mira el jugador (0 = delante).
                float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float relative = Mth.wrapDegrees(target - player.getYRot());
                String arrow = ARROWS[Math.floorMod(Math.round(relative / 45f), 8)];
                player.displayClientMessage(Component.translatable("caricraft.cobbleverse.shiny_compass", name, Math.round(distance), arrow)
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    private static void clearActionBar(CaricraftServer mod, Party party, Alert alert) {
        for (UUID member : party.members()) {
            ServerPlayer player = mod.server().getPlayerList().getPlayer(member);
            if (player != null && player.level() == alert.level && !alert.arrived.contains(member)) {
                player.displayClientMessage(Component.empty(), true);
            }
        }
    }

    public static List<PhoneSnapshot.ShinyAlertView> views(Party party) {
        List<PhoneSnapshot.ShinyAlertView> views = new ArrayList<>();
        for (Alert alert : ALERTS.values()) {
            if (alert.partyId.equals(party.id())) {
                views.add(new PhoneSnapshot.ShinyAlertView(alert.species, alert.pokemonLevel, alert.level.dimension().location().toString(),
                        alert.x, alert.y, alert.z, alert.expiresAt, alert.near));
            }
        }
        return views;
    }

    public static void clear() {
        ALERTS.clear();
    }
}
