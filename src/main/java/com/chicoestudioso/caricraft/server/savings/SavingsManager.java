package com.chicoestudioso.caricraft.server.savings;

import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.savings.SavingsService;
import com.chicoestudioso.caricraft.core.savings.SavingsState;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.server.CaricraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/** Hucha del dúo en el servidor: meter, sacar, intereses de cada día y la vista del teléfono. */
public final class SavingsManager {
    private SavingsManager() {
    }

    /** {@code raw}: Carins, o {@code all}. */
    private static long amount(String raw) {
        if (raw == null) {
            return 0;
        }
        if ("all".equalsIgnoreCase(raw.trim())) {
            return -1;
        }
        try {
            return Math.max(0, Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static ActionResult deposit(CaricraftServer mod, ServerPlayer player, String raw) {
        Party party = mod.core().partyOf(player.getUUID());
        if (party == null) {
            return ActionResult.fail("no_profile");
        }
        long amount = amount(raw);
        if (amount == 0) {
            return ActionResult.fail("invalid_amount");
        }
        ActionResult result = mod.core().savings().deposit(party, amount, mod.core().day());
        if (result.success()) {
            player.playNotifySound(SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 1.4f);
            mod.markDirty();
        }
        return result;
    }

    public static ActionResult withdraw(CaricraftServer mod, ServerPlayer player, String raw) {
        Party party = mod.core().partyOf(player.getUUID());
        if (party == null) {
            return ActionResult.fail("no_profile");
        }
        long amount = amount(raw);
        if (amount == 0) {
            return ActionResult.fail("invalid_amount");
        }
        ActionResult result = mod.core().savings().withdraw(party, amount, mod.core().day());
        if (result.success()) {
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.1f);
            mod.markDirty();
        }
        return result;
    }

    /** Desde la comprobación periódica: intereses del día nuevo (si los hay, avisa al dúo). */
    public static void accrue(CaricraftServer mod, Party party, long day) {
        SavingsService service = mod.core().savings();
        if (!service.enabled()) {
            return;
        }
        long before = party.savings().day();
        long gained = service.accrue(party, day);
        if (before != party.savings().day()) {
            mod.markDirty();
        }
        if (gained > 0) {
            java.util.UUID anyone = party.members().iterator().next();
            mod.notifyParty(anyone, "caricraft.notify.savings_interest", List.of(String.valueOf(gained),
                    String.valueOf(party.savings().balance())));
            mod.sendPartySnapshots(anyone);
        }
    }

    public static PhoneSnapshot.SavingsView view(CaricraftServer mod, Party party) {
        SavingsService service = mod.core().savings();
        if (!service.enabled()) {
            return null;
        }
        var config = mod.core().config().savings;
        SavingsState s = party.savings();
        return new PhoneSnapshot.SavingsView(s.balance(), config.dailyRate, config.dailyCap, service.preview(party, mod.core().day()),
                s.totalInterest(), s.lastInterest(), config.maxBalance);
    }

}
