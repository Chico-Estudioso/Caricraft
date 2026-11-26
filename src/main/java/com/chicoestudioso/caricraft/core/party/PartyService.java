package com.chicoestudioso.caricraft.core.party;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.mission.MissionState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perfiles, grupos e invitaciones. Política de fusión y reparto: ADR-004.
 */
public final class PartyService {
    /** Tiempo para confirmar la desvinculación tras la primera pulsación. */
    public static final long LEAVE_CONFIRM_WINDOW_MS = 10_000L;
    private static final long HOUR_MS = 3_600_000L;

    private final CaricraftConfig config;
    private final Clock clock;
    private final EconomyService economy;
    private final ChronicleService chronicle;

    public PartyService(CaricraftConfig config, Clock clock, EconomyService economy, ChronicleService chronicle) {
        this.config = config;
        this.clock = clock;
        this.economy = economy;
        this.chronicle = chronicle;
    }

    /**
     * Devuelve el perfil del jugador creándolo (con su grupo individual) si no existe, y actualiza su nombre.
     */
    public PlayerProfile ensureProfile(CaricraftState state, UUID player, String name) {
        PlayerProfile profile = state.profiles().get(player);
        if (profile == null) {
            profile = new PlayerProfile(player, name);
            state.profiles().put(player, profile);
        } else {
            profile.setName(name);
        }
        if (profile.partyId() == null || !state.parties().containsKey(profile.partyId())) {
            Party party = createSoloParty(state, player);
            profile.setPartyId(party.id());
            if (config.startingBalance > 0) {
                economy.credit(party.wallet(), config.startingBalance, "starting_balance", "", "start:" + party.id());
            }
            chronicle.add(party, ChronicleEntry.WELCOME, player, profile.name());
        } else {
            state.parties().get(profile.partyId()).members().add(player);
        }
        return profile;
    }

    private Party createSoloParty(CaricraftState state, UUID player) {
        Party party = new Party(state.newId(), clock.now());
        party.members().add(player);
        state.parties().put(party.id(), party);
        return party;
    }

    /** Elimina las invitaciones caducadas. */
    public void purgeExpiredInvites(CaricraftState state) {
        long now = clock.now();
        state.invites().removeIf(invite -> invite.expired(now));
    }

    public List<Invite> incomingInvites(CaricraftState state, UUID player) {
        purgeExpiredInvites(state);
        return state.invites().stream().filter(i -> i.to().equals(player)).toList();
    }

    public List<Invite> outgoingInvites(CaricraftState state, UUID player) {
        purgeExpiredInvites(state);
        return state.invites().stream().filter(i -> i.from().equals(player)).toList();
    }

    public ActionResult invite(CaricraftState state, UUID from, UUID to) {
        purgeExpiredInvites(state);
        if (from.equals(to)) {
            return ActionResult.fail("invite_self");
        }
        PlayerProfile inviter = state.profiles().get(from);
        PlayerProfile target = state.profiles().get(to);
        if (inviter == null || target == null) {
            return ActionResult.fail("player_unknown");
        }
        Party inviterParty = state.partyOf(from);
        Party targetParty = state.partyOf(to);
        if (inviterParty.id().equals(targetParty.id())) {
            return ActionResult.fail("already_together");
        }
        if (inviterParty.members().size() >= config.maxPartySize) {
            return ActionResult.fail("party_full");
        }
        if (targetParty.members().size() > 1) {
            return ActionResult.fail("target_in_party", target.name());
        }
        long now = clock.now();
        if (inviter.linkCooldownUntil() > now) {
            return ActionResult.fail("cooldown_self", minutesLeft(inviter.linkCooldownUntil(), now));
        }
        if (target.linkCooldownUntil() > now) {
            return ActionResult.fail("cooldown_target", target.name());
        }
        boolean duplicate = state.invites().stream().anyMatch(i -> i.from().equals(from) && i.to().equals(to));
        if (duplicate) {
            return ActionResult.fail("invite_duplicate", target.name());
        }
        long pending = state.invites().stream().filter(i -> i.from().equals(from)).count();
        if (pending >= config.maxPendingInvites) {
            return ActionResult.fail("invite_limit");
        }
        state.invites().add(new Invite(from, to, now + config.inviteExpirySeconds * 1000L));
        return ActionResult.ok("invite_sent", target.name());
    }

    public ActionResult decline(CaricraftState state, UUID to, UUID from) {
        purgeExpiredInvites(state);
        boolean removed = state.invites().removeIf(i -> i.from().equals(from) && i.to().equals(to));
        return removed ? ActionResult.ok("invite_declined", state.nameOf(from)) : ActionResult.fail("invite_missing");
    }

    public ActionResult cancel(CaricraftState state, UUID from, UUID to) {
        purgeExpiredInvites(state);
        boolean removed = state.invites().removeIf(i -> i.from().equals(from) && i.to().equals(to));
        return removed ? ActionResult.ok("invite_cancelled", state.nameOf(to)) : ActionResult.fail("invite_missing");
    }

    /**
     * Acepta la invitación de {@code from}: el grupo individual de {@code to} se fusiona en el de {@code from}.
     */
    public ActionResult accept(CaricraftState state, UUID to, UUID from) {
        purgeExpiredInvites(state);
        boolean exists = state.invites().stream().anyMatch(i -> i.from().equals(from) && i.to().equals(to));
        if (!exists) {
            return ActionResult.fail("invite_missing");
        }
        PlayerProfile joiner = state.profiles().get(to);
        PlayerProfile inviter = state.profiles().get(from);
        Party target = state.partyOf(from);
        Party source = state.partyOf(to);
        if (joiner == null || inviter == null || target == null || source == null) {
            return ActionResult.fail("player_unknown");
        }
        long now = clock.now();
        if (joiner.linkCooldownUntil() > now || inviter.linkCooldownUntil() > now) {
            return ActionResult.fail("cooldown_self", minutesLeft(Math.max(joiner.linkCooldownUntil(), inviter.linkCooldownUntil()), now));
        }
        if (target.id().equals(source.id())) {
            return ActionResult.fail("already_together");
        }
        if (target.members().size() >= config.maxPartySize || source.members().size() > 1) {
            state.invites().removeIf(i -> i.from().equals(from) && i.to().equals(to));
            return ActionResult.fail("party_full");
        }

        merge(source, target, to, joiner.name());
        joiner.setPartyId(target.id());
        state.parties().remove(source.id());
        // Invitaciones que ya no tienen sentido.
        state.invites().removeIf(i -> i.to().equals(to) || i.from().equals(to)
                || (target.members().size() >= config.maxPartySize && (i.from().equals(from) || i.to().equals(from))));
        chronicle.add(target, ChronicleEntry.PARTY_FORMED, to, joiner.name(), inviter.name(), joiner.name());
        return ActionResult.ok("party_joined", inviter.name());
    }

    private void merge(Party source, Party target, UUID joiner, String joinerName) {
        long balance = source.wallet().balance();
        if (balance > 0) {
            economy.transfer(source.wallet(), target.wallet(), balance, "party_merge", joinerName);
        }
        target.members().add(joiner);
        target.ownedApps().addAll(source.ownedApps());
        target.discoveredBiomes().addAll(source.discoveredBiomes());
        source.stats().forEach((key, value) -> target.incrementStat(key, value));
        for (Map.Entry<String, MissionState> entry : source.missions().entrySet()) {
            target.missions().putIfAbsent(entry.getKey(), entry.getValue());
        }
        target.claimedEver().addAll(source.claimedEver());
        if (target.camp() == null) {
            target.setCamp(source.camp());
        }
        source.upgradeLevels().forEach((id, level) -> target.upgradeLevels().merge(id, level, Math::max));
        target.photos().addAll(source.photos());
        target.photos().sort(java.util.Comparator.comparingLong(com.chicoestudioso.caricraft.core.lens.Photo::time).reversed());
        target.pokemon().mergeFrom(source.pokemon());
        chronicle.mergeInto(target, source.chronicle());
    }

    /** Primera pulsación de "desvincular". */
    public ActionResult requestLeave(CaricraftState state, UUID player) {
        Party party = state.partyOf(player);
        PlayerProfile profile = state.profiles().get(player);
        if (party == null || profile == null || !party.isDuo()) {
            return ActionResult.fail("not_in_duo");
        }
        profile.setLeaveRequestedAt(clock.now());
        return ActionResult.ok("leave_confirm");
    }

    public boolean leavePending(CaricraftState state, UUID player) {
        PlayerProfile profile = state.profiles().get(player);
        return profile != null && profile.leaveRequestedAt() > 0
                && clock.now() - profile.leaveRequestedAt() <= LEAVE_CONFIRM_WINDOW_MS;
    }

    /**
     * Confirma la desvinculación: quien se va recibe un grupo nuevo con la mitad del saldo (hacia abajo),
     * una copia de apps, biomas, estadísticas y crónicas, y sus misiones individuales.
     */
    public ActionResult confirmLeave(CaricraftState state, UUID player) {
        Party party = state.partyOf(player);
        PlayerProfile profile = state.profiles().get(player);
        if (party == null || profile == null || !party.isDuo()) {
            return ActionResult.fail("not_in_duo");
        }
        if (!leavePending(state, player)) {
            return ActionResult.fail("leave_not_requested");
        }
        profile.setLeaveRequestedAt(0);

        Party solo = new Party(state.newId(), clock.now());
        solo.members().add(player);
        state.parties().put(solo.id(), solo);

        long half = party.wallet().balance() / 2;
        if (half > 0) {
            economy.transfer(party.wallet(), solo.wallet(), half, "party_split", profile.name());
        }
        solo.ownedApps().addAll(party.ownedApps());
        solo.discoveredBiomes().addAll(party.discoveredBiomes());
        party.stats().forEach(solo::incrementStat);
        solo.claimedEver().addAll(party.claimedEver());
        solo.challenge().copyFrom(party.challenge());
        solo.setCamp(party.camp());
        solo.upgradeLevels().putAll(party.upgradeLevels());
        solo.photos().addAll(party.photos());
        solo.pokemon().copyDexFrom(party.pokemon());
        chronicle.mergeInto(solo, party.chronicle());

        String suffix = "@" + player;
        Iterator<Map.Entry<String, MissionState>> it = party.missions().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MissionState> entry = it.next();
            if (entry.getKey().endsWith(suffix)) {
                solo.missions().put(entry.getKey(), entry.getValue());
                it.remove();
            }
        }

        party.members().remove(player);
        profile.setPartyId(solo.id());

        long cooldownUntil = clock.now() + config.unlinkCooldownHours * HOUR_MS;
        List<UUID> affected = new ArrayList<>(party.members());
        affected.add(player);
        for (UUID member : affected) {
            PlayerProfile p = state.profiles().get(member);
            if (p != null) {
                p.setLinkCooldownUntil(cooldownUntil);
            }
        }
        chronicle.add(party, ChronicleEntry.PARTY_SPLIT, player, profile.name(), profile.name());
        chronicle.add(solo, ChronicleEntry.PARTY_SPLIT, player, profile.name(), profile.name());
        return ActionResult.ok("party_left");
    }

    private static long minutesLeft(long until, long now) {
        return Math.max(1, (until - now + 59_999) / 60_000);
    }
}
