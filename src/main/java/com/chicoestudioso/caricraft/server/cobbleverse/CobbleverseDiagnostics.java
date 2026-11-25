package com.chicoestudioso.caricraft.server.cobbleverse;

import com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog;
import com.chicoestudioso.caricraft.integration.cobbledollars.CobbleDollarsBridge;
import com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * {@code /caricraft cobbleverse prueba}: comprueba en este servidor, con un jugador ficticio, que la integración
 * con Cobblemon y CobbleDollars funciona (crear un Pokémon especial, quitarlo y devolverlo, cobrar y pagar).
 * Deja todo como estaba. Útil al actualizar el modpack.
 */
public final class CobbleverseDiagnostics {
    private CobbleverseDiagnostics() {
    }

    public static List<String> run(CaricraftServer mod) {
        List<String> out = new ArrayList<>();
        PokemonBackend backend = CobblemonCompat.backend();
        if (backend == null) {
            out.add("FALLO Cobblemon no está instalado o su integración no se pudo activar.");
            return out;
        }
        out.add("OK Cobblemon detectado. Parte Cobbleverse " + (mod.core().cobbleverse().enabled() ? "activada" : "DESACTIVADA en la configuración") + ".");
        CobbleverseCatalog catalog = mod.core().cobbleverse().catalog();
        out.add((catalog.offers().isEmpty() ? "AVISO" : "OK") + " Catálogo: " + catalog.offers().size() + " ofertas y "
                + catalog.gifts().size() + " premios (los que no existen en este servidor se descartan al cargar).");
        for (CobbleverseCatalog.Offer offer : catalog.offers()) {
            if (!offer.isPokemon() && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(offer.item()))) {
                out.add("FALLO Objeto inexistente en la oferta " + offer.id() + ": " + offer.item());
            }
        }

        FakePlayer fake = FakePlayer.get(mod.server().overworld(), new GameProfile(
                UUID.nameUUIDFromBytes("caricraft-cobbleverse-diagnostics".getBytes(StandardCharsets.UTF_8)), "[Caricraft QA]"));
        int before = backend.party(fake).size();
        try {
            String species = backend.give(fake, "eevee", 15, true, 6, List.of("extremespeed", "protect"));
            Optional<PokemonBackend.PartyMon> given = backend.party(fake).stream()
                    .filter(m -> m.species().equals(species)).reduce((a, b) -> b);
            if (given.isEmpty()) {
                out.add("FALLO Se creó " + species + " pero no aparece en el equipo.");
            } else {
                PokemonBackend.PartyMon mon = given.get();
                boolean ok = mon.level() == 15 && mon.info().shiny() && mon.info().ivTotal() == 186;
                out.add((ok ? "OK" : "FALLO") + " Pokémon de tienda: " + species + " nv. " + mon.level() + ", variocolor="
                        + mon.info().shiny() + ", IV=" + mon.info().ivTotal() + "/186, se vendería por ₽"
                        + mod.core().cobbleverse().sellPrice(mon.info()) + ".");
                Optional<Object> taken = backend.take(fake, mon.uuid());
                int afterTake = backend.party(fake).size();
                backend.restore(fake, taken.orElse(null));
                int afterRestore = backend.party(fake).size();
                out.add((taken.isPresent() && afterTake == afterRestore - 1 ? "OK" : "FALLO")
                        + " Quitar y devolver al equipo (venta deshecha): " + afterTake + " → " + afterRestore + ".");
            }
        } catch (RuntimeException e) {
            out.add("FALLO Crear el Pokémon: " + e);
        } finally {
            // Se deja el equipo del jugador ficticio vacío.
            for (PokemonBackend.PartyMon mon : backend.party(fake)) {
                backend.take(fake, mon.uuid());
            }
        }
        out.add("OK Equipo de prueba vaciado (antes tenía " + before + ").");

        if (!CobbleDollarsBridge.available()) {
            out.add("AVISO CobbleDollars no está disponible: la tienda y la venta no funcionarán (el regalo misterioso sí).");
        } else {
            OptionalLong start = CobbleDollarsBridge.balance(fake);
            boolean added = CobbleDollarsBridge.add(fake, 1000);
            long afterAdd = CobbleDollarsBridge.balance(fake).orElse(-1);
            boolean removed = CobbleDollarsBridge.remove(fake, 1000);
            boolean refused = !CobbleDollarsBridge.remove(fake, Long.MAX_VALUE / 2);
            long end = CobbleDollarsBridge.balance(fake).orElse(-1);
            boolean ok = start.isPresent() && added && afterAdd == start.getAsLong() + 1000 && removed && refused
                    && end == start.getAsLong();
            out.add((ok ? "OK" : "FALLO") + " CobbleDollars: " + start.orElse(-1) + " → +1000 = " + afterAdd
                    + " → −1000 = " + end + "; sin saldo se rechaza: " + refused + ".");
        }
        return out;
    }
}
