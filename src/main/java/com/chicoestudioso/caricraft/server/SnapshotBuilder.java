package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.CaricraftConstants;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.economy.LedgerEntry;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.mission.MissionScope;
import com.chicoestudioso.caricraft.core.mission.MissionState;
import com.chicoestudioso.caricraft.core.mission.MissionStatus;
import com.chicoestudioso.caricraft.core.mission.Objective;
import com.chicoestudioso.caricraft.core.party.Invite;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import com.chicoestudioso.caricraft.core.shop.ShopItem;
import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.AppView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.ChronicleView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.ExplorationView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.InviteView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.LedgerView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.MemberView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.MissionView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.ObjectiveView;
import com.chicoestudioso.caricraft.network.PhoneSnapshot.PartyView;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Construye el {@link PhoneSnapshot} que ve un jugador concreto. */
public final class SnapshotBuilder {
    static final int MAX_LEDGER = 30;
    static final int MAX_CHRONICLE = 60;

    private SnapshotBuilder() {
    }

    public static PhoneSnapshot build(CaricraftServer mod, ServerPlayer player) {
        CaricraftCore core = mod.core();
        CaricraftState state = core.state();
        UUID viewer = player.getUUID();
        PlayerProfile profile = core.onPlayerJoin(viewer, player.getGameProfile().getName());
        Party party = core.partyOf(viewer);
        long now = core.clock().now();

        List<MemberView> members = new ArrayList<>();
        for (UUID member : party.members()) {
            members.add(new MemberView(member.toString(), state.nameOf(member),
                    mod.server().getPlayerList().getPlayer(member) != null, member.equals(viewer)));
        }
        PartyView partyView = new PartyView(party.id().toString(), party.isDuo(), core.config().maxPartySize, members,
                profile.linkCooldownUntil() > now ? profile.linkCooldownUntil() : 0);

        List<AppView> apps = new ArrayList<>();
        for (ShopItem item : core.shop().catalog().all()) {
            apps.add(new AppView(item.id(), item.kind().name(), item.ownership().name(), item.price(),
                    appState(core, party, profile, item), item.requires(), item.icon(), item.color()));
        }

        List<MissionView> missions = new ArrayList<>();
        for (MissionDefinition def : core.missions().catalog().all()) {
            missions.add(missionView(core, state, party, viewer, def, now));
        }

        List<LedgerView> ledger = party.wallet().ledger().stream().limit(MAX_LEDGER)
                .map(SnapshotBuilder::ledgerView).toList();

        boolean chronicles = core.shop().hasApp(party, AppCatalog.CHRONICLES);
        List<ChronicleView> chronicle = chronicles
                ? party.chronicle().stream().limit(MAX_CHRONICLE).map(SnapshotBuilder::chronicleView).toList()
                : List.of();

        boolean explorer = core.shop().hasApp(party, AppCatalog.EXPLORER);
        int totalBiomes = mod.server().registryAccess().registryOrThrow(Registries.BIOME).keySet().size();
        ExplorationView exploration = new ExplorationView(party.discoveredBiomes().size(), totalBiomes,
                explorer ? party.discoveredBiomes().stream().sorted().toList() : List.of());

        Map<String, Long> stats = core.shop().hasApp(party, AppCatalog.STATS)
                ? new LinkedHashMap<>(party.stats()) : Map.of();

        List<InviteView> incoming = core.parties().incomingInvites(state, viewer).stream()
                .map(invite -> inviteView(state, invite.from(), invite)).toList();
        List<InviteView> outgoing = core.parties().outgoingInvites(state, viewer).stream()
                .map(invite -> inviteView(state, invite.to(), invite)).toList();

        return new PhoneSnapshot(CaricraftConstants.PROTOCOL_VERSION, now, viewer.toString(),
                party.wallet().balance(), profile.theme(), List.copyOf(profile.ownedCosmetics()), partyView, apps,
                missions, ledger, chronicle, exploration, stats, incoming, outgoing,
                core.parties().leavePending(state, viewer), FabricLoader.getInstance().isModLoaded("cobblemon"));
    }

    private static String appState(CaricraftCore core, Party party, PlayerProfile profile, ShopItem item) {
        if (item.builtin()) {
            return "BUILTIN";
        }
        if (item.comingSoon()) {
            return "COMING_SOON";
        }
        if (core.shop().owns(party, profile, item)) {
            return "OWNED";
        }
        return core.shop().requirementsMet(party, item) ? "AVAILABLE" : "LOCKED";
    }

    private static MissionView missionView(CaricraftCore core, CaricraftState state, Party party, UUID viewer,
                                           MissionDefinition def, long now) {
        MissionStatus status = core.missions().statusFor(party, viewer, def);
        MissionState current = core.missions().stateFor(party, viewer, def);
        List<ObjectiveView> objectives = new ArrayList<>();
        for (int i = 0; i < def.objectives().size(); i++) {
            Objective objective = def.objectives().get(i);
            long progress = current == null ? (status == MissionStatus.CLAIMED ? objective.amount() : 0)
                    : current.progress()[Math.min(i, current.progress().length - 1)];
            objectives.add(new ObjectiveView(objective.type().name(), objective.target(), objective.amount(), progress));
        }
        Map<String, Long> contributions = new LinkedHashMap<>();
        if (current != null && def.scope() != MissionScope.INDIVIDUAL) {
            current.contributions().entrySet().stream()
                    .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> contributions.put(state.nameOf(e.getKey()), e.getValue()));
        }
        long expiresAt = def.category().repeatable() ? def.category().cycleEnd(now) : 0;
        return new MissionView(def.id(), def.scope().name(), def.category().name(), status.name(),
                core.missions().rewardOf(def), def.requireAllMembers(), objectives, contributions, expiresAt,
                def.title(), def.description());
    }

    private static LedgerView ledgerView(LedgerEntry entry) {
        return new LedgerView(entry.time(), entry.delta(), entry.balanceAfter(), entry.reason(), entry.detail());
    }

    private static ChronicleView chronicleView(ChronicleEntry entry) {
        return new ChronicleView(entry.time(), entry.type(), entry.actorName(), entry.args());
    }

    private static InviteView inviteView(CaricraftState state, UUID other, Invite invite) {
        return new InviteView(other.toString(), state.nameOf(other), invite.expiresAt());
    }
}
