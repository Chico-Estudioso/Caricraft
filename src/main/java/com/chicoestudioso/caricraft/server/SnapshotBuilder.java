package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.CaricraftConstants;
import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.challenge.ChallengeState;
import com.chicoestudioso.caricraft.server.camp.TravelManager;
import com.chicoestudioso.caricraft.server.challenge.ChallengeManager;
import com.chicoestudioso.caricraft.core.camp.CampService;
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
import com.chicoestudioso.caricraft.network.PhoneSnapshot.ChallengeView;
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
                    appState(core, party, profile, item), item.requires(), item.icon(), item.color(),
                    item.isUpgrade() ? party.upgradeLevel(item.id()) : 0, item.maxLevel()));
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
                core.parties().leavePending(state, viewer), FabricLoader.getInstance().isModLoaded("cobblemon"),
                profile.tipsEnabled(),
                challengeView(mod, party), marketView(core, party),
                com.chicoestudioso.caricraft.server.cobbleverse.CobbleverseManager.view(mod, player), campView(mod, party, player),
                duoTravelView(mod, party, player), lensView(core, party),
                new PhoneSnapshot.BoxView(core.box().hasApp(party), core.box().rows(party),
                        com.chicoestudioso.caricraft.server.box.CariBoxManager.usedSlots(mod, party)));
    }

    private static PhoneSnapshot.LensView lensView(CaricraftCore core, Party party) {
        if (!core.lens().hasApp(party)) {
            return new PhoneSnapshot.LensView(false, 0, List.of());
        }
        List<PhoneSnapshot.PhotoView> photos = party.photos().stream()
                .map(p -> new PhoneSnapshot.PhotoView(p.id(), p.authorName(), p.time(), p.caption(), p.width(), p.height()))
                .toList();
        return new PhoneSnapshot.LensView(true, core.lens().capacity(party), photos);
    }

    private static PhoneSnapshot.CampView campView(CaricraftServer mod, Party party, ServerPlayer viewer) {
        CaricraftCore core = mod.core();
        PhoneSnapshot.PositionView camp = null;
        if (party.camp() != null && core.camp().hasApp(party)) {
            var c = party.camp();
            camp = new PhoneSnapshot.PositionView("", "", c.dimension(), "", c.x() + 0.5, c.y(), c.z() + 0.5);
        }
        var config = core.config().camp;
        boolean toCamp = TravelManager.warmupDestination(viewer.getUUID()) == CampService.Destination.CAMP;
        return new PhoneSnapshot.CampView(camp, core.camp().cooldownEndsAt(party, viewer.getUUID()),
                config.campTravelCost, config.blockDuringChallenge && ChallengeManager.challengeTonight(viewer),
                toCamp ? TravelManager.warmupSecondsLeft(viewer.getUUID()) : 0);
    }

    private static PhoneSnapshot.DuoTravelView duoTravelView(CaricraftServer mod, Party party, ServerPlayer viewer) {
        CaricraftCore core = mod.core();
        List<PhoneSnapshot.PositionView> partners = new ArrayList<>();
        if (core.camp().hasTravelApp(party)) {
            for (UUID member : party.members()) {
                if (member.equals(viewer.getUUID())) {
                    continue;
                }
                ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
                if (online != null) {
                    String biome = online.serverLevel().getBiome(online.blockPosition()).unwrapKey()
                            .map(key -> key.location().toString()).orElse("");
                    partners.add(new PhoneSnapshot.PositionView(member.toString(), online.getGameProfile().getName(),
                            online.level().dimension().location().toString(), biome,
                            online.getX(), online.getY(), online.getZ()));
                }
            }
        }
        var config = core.config().camp;
        var state = core.state();
        PhoneSnapshot.TravelView incoming = core.camp().incoming(state, viewer.getUUID())
                .map(r -> new PhoneSnapshot.TravelView(state.nameOf(r.from()), r.kind().name(), r.expiresAt())).orElse(null);
        PhoneSnapshot.TravelView outgoing = core.camp().outgoing(state, viewer.getUUID())
                .map(r -> new PhoneSnapshot.TravelView(state.nameOf(r.to()), r.kind().name(), r.expiresAt())).orElse(null);
        boolean toPartner = TravelManager.warmupDestination(viewer.getUUID()) == CampService.Destination.PARTNER;
        return new PhoneSnapshot.DuoTravelView(partners, core.camp().cooldownEndsAt(party, viewer.getUUID()),
                config.partnerTravelCost, config.blockDuringChallenge && ChallengeManager.challengeTonight(viewer),
                incoming, outgoing, toPartner ? TravelManager.warmupSecondsLeft(viewer.getUUID()) : 0,
                config.requestSeconds, config.warmupSeconds);
    }

    private static PhoneSnapshot.MarketView marketView(CaricraftCore core, Party party) {
        var market = core.market();
        if (!market.enabled()) {
            return new PhoneSnapshot.MarketView(false, 0, List.of());
        }
        long day = market.day();
        List<PhoneSnapshot.OfferView> offers = market.offers(day).stream()
                .map(o -> new PhoneSnapshot.OfferView(o.id(), o.item(), o.count(), o.price(), market.stockLeft(party, day, o),
                        o.dailyStock(), o.source()))
                .toList();
        return new PhoneSnapshot.MarketView(true, market.nextRefresh(), offers);
    }

    private static ChallengeView challengeView(CaricraftServer mod, Party party) {
        CaricraftCore core = mod.core();
        ChallengeService service = core.challenge();
        ChallengeState state = party.challenge();
        CaricraftConfig.ChallengeConfig config = core.config().challenge;
        long day = ChallengeManager.day(mod.server());
        boolean night = ChallengeManager.night(mod.server());
        return new ChallengeView(config.enabled, state.active(), !state.active() && state.scheduledDay() == day,
                state.forced(), night, service.nightsUntilForced(party, day), config.intervalNights, service.level(party),
                config.maxLevel, state.nightsCompleted(), state.totalKills(), state.totalCarins(), state.nightKills(),
                state.nightCarins(), config.killReward, config.nightReward, config.blockSleep,
                ChallengeManager.blockedByModpack(), ChallengeService.wave(mod.server().overworld().getDayTime()),
                service.effectiveLevel(party, ChallengeService.wave(mod.server().overworld().getDayTime())), config.waves,
                com.chicoestudioso.caricraft.server.challenge.ChallengeSquads.views(party), service.xp(party),
                service.xpForLevel(service.level(party)),
                service.level(party) >= config.maxLevel ? -1 : service.xpForLevel(service.level(party) + 1),
                com.chicoestudioso.caricraft.server.challenge.ChallengeChests.views(party));
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
        long expiresAt = def.category().repeatable()
                ? def.category().cycleEnd(now, com.chicoestudioso.caricraft.core.GameCalendar.of(core.config().timeZone)) : 0;
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
