package com.chicoestudioso.caricraft.core.mission;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftState;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.shop.ShopService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Máquina de estados de las misiones y aplicación del progreso. */
public final class MissionService {
    public static final String STAT_MISSIONS = "missions_claimed";

    private final MissionCatalog catalog;
    private final ShopService shop;
    private final EconomyService economy;
    private final ChronicleService chronicle;
    private final CaricraftConfig config;
    private final Clock clock;

    /** Calendario de la zona horaria configurada (se lee cada vez: la configuración se puede recargar). */
    private com.chicoestudioso.caricraft.core.GameCalendar calendar() {
        return com.chicoestudioso.caricraft.core.GameCalendar.of(config.timeZone);
    }

    public MissionService(MissionCatalog catalog, ShopService shop, EconomyService economy, ChronicleService chronicle,
                          CaricraftConfig config, Clock clock) {
        this.catalog = catalog;
        this.shop = shop;
        this.economy = economy;
        this.chronicle = chronicle;
        this.config = config;
        this.clock = clock;
    }

    public MissionCatalog catalog() {
        return catalog;
    }

    /** Estado actual tras aplicar la caducidad de diarias/semanales. Puede ser {@code null}. */
    public MissionState stateFor(Party party, UUID player, MissionDefinition def) {
        String key = def.stateKey(player);
        MissionState state = party.missions().get(key);
        if (state != null && def.category().repeatable() && state.cycle() != def.category().cycle(clock.now(), calendar())
                && state.status() != MissionStatus.COMPLETED) {
            // Ciclo terminado: lo no completado caduca y vuelve a estar disponible.
            party.missions().remove(key);
            return null;
        }
        return state;
    }

    public MissionStatus statusFor(Party party, UUID player, MissionDefinition def) {
        MissionState state = stateFor(party, player, def);
        if (state != null) {
            return state.status();
        }
        if (!def.category().repeatable() && party.claimedEver().contains(def.stateKey(player))) {
            return MissionStatus.CLAIMED;
        }
        return requirementsMet(party, player, def) ? MissionStatus.AVAILABLE : MissionStatus.LOCKED;
    }

    public boolean requirementsMet(Party party, UUID player, MissionDefinition def) {
        if (def.scope() == MissionScope.COOP && !party.isDuo()) {
            return false;
        }
        if (def.requiresApp() != null && !def.requiresApp().isBlank() && !shop.hasApp(party, def.requiresApp())) {
            return false;
        }
        for (String required : def.requires()) {
            if (!party.claimedEver().contains(required) && !party.claimedEver().contains(required + "@" + player)) {
                return false;
            }
        }
        return true;
    }

    public ActionResult accept(CaricraftState state, UUID player, String missionId) {
        Party party = state.partyOf(player);
        MissionDefinition def = catalog.get(missionId).orElse(null);
        if (party == null || def == null) {
            return ActionResult.fail("unknown_mission");
        }
        if (def.category() == MissionCategory.MILESTONE) {
            return ActionResult.fail("milestone_auto");
        }
        MissionStatus status = statusFor(party, player, def);
        switch (status) {
            case LOCKED -> {
                return ActionResult.fail(def.scope() == MissionScope.COOP && !party.isDuo() ? "mission_needs_duo" : "mission_locked");
            }
            case ACCEPTED, COMPLETED -> {
                return ActionResult.fail("mission_already_active");
            }
            case CLAIMED -> {
                return ActionResult.fail("mission_already_claimed");
            }
            default -> {
            }
        }
        party.missions().put(def.stateKey(player), newState(def, player));
        return ActionResult.ok("mission_accepted", def.id());
    }

    private MissionState newState(MissionDefinition def, UUID player) {
        long now = clock.now();
        return new MissionState(def.id(), def.scope().shared() ? null : player, MissionStatus.ACCEPTED,
                new long[def.objectives().size()], def.category().cycle(now, calendar()), now);
    }

    public ActionResult abandon(CaricraftState state, UUID player, String missionId) {
        Party party = state.partyOf(player);
        MissionDefinition def = catalog.get(missionId).orElse(null);
        if (party == null || def == null) {
            return ActionResult.fail("unknown_mission");
        }
        if (def.category() == MissionCategory.MILESTONE) {
            return ActionResult.fail("milestone_auto");
        }
        MissionState current = stateFor(party, player, def);
        if (current == null || current.status() != MissionStatus.ACCEPTED) {
            return ActionResult.fail("mission_not_active");
        }
        party.missions().remove(def.stateKey(player));
        return ActionResult.ok("mission_abandoned", def.id());
    }

    public ActionResult claim(CaricraftState state, UUID player, String missionId) {
        Party party = state.partyOf(player);
        MissionDefinition def = catalog.get(missionId).orElse(null);
        if (party == null || def == null) {
            return ActionResult.fail("unknown_mission");
        }
        MissionState current = stateFor(party, player, def);
        if (current == null) {
            return statusFor(party, player, def) == MissionStatus.CLAIMED
                    ? ActionResult.fail("mission_already_claimed") : ActionResult.fail("mission_not_active");
        }
        if (current.status() == MissionStatus.CLAIMED) {
            return ActionResult.fail("mission_already_claimed");
        }
        if (current.status() != MissionStatus.COMPLETED) {
            return ActionResult.fail("mission_not_completed");
        }
        long reward = rewardOf(def);
        String key = def.stateKey(player);
        if (reward > 0) {
            String idempotencyKey = "mission:" + party.id() + ":" + key + ":" + current.cycle();
            EconomyService.Result result = economy.credit(party.wallet(), reward, "mission", def.id(), idempotencyKey);
            if (result == EconomyService.Result.INVALID_AMOUNT) {
                return ActionResult.fail("invalid_amount");
            }
            // DUPLICATE: ya se pagó (p. ej. tras un fallo a mitad); solo se marca como reclamada.
        }
        current.setStatus(MissionStatus.CLAIMED);
        current.setClaimedAt(clock.now());
        party.claimedEver().add(key);
        party.incrementStat(STAT_MISSIONS, 1);
        return ActionResult.ok("mission_claimed", def.id(), reward);
    }

    public long rewardOf(MissionDefinition def) {
        return Math.max(0, Math.round(def.reward() * config.missionRewardMultiplier));
    }

    /**
     * Aplica un evento de progreso del jugador a sus misiones y a las del grupo, y actualiza estadísticas.
     *
     * @return misiones que se acaban de completar
     */
    public List<MissionDefinition> onProgress(CaricraftState state, UUID player, ProgressEvent event) {
        Party party = state.partyOf(player);
        if (party == null || event.amount() <= 0) {
            return List.of();
        }
        if (event.type().statKey() != null) {
            party.incrementStat(event.type().statKey(), event.amount());
        }
        List<MissionDefinition> completed = new ArrayList<>();
        for (MissionDefinition def : catalog.all()) {
            if (def.objectives().stream().noneMatch(o -> o.matches(event))) {
                continue;
            }
            if (def.scope() == MissionScope.COOP && !party.isDuo()) {
                continue;
            }
            MissionState current = stateFor(party, player, def);
            if (current == null && def.category() == MissionCategory.MILESTONE
                    && !party.claimedEver().contains(def.stateKey(player)) && requirementsMet(party, player, def)) {
                current = newState(def, player);
                party.missions().put(def.stateKey(player), current);
            }
            if (current == null || current.status() != MissionStatus.ACCEPTED) {
                continue;
            }
            long[] progress = current.progress();
            for (int i = 0; i < def.objectives().size(); i++) {
                Objective objective = def.objectives().get(i);
                if (objective.matches(event) && progress[i] < objective.amount()) {
                    progress[i] = Math.min(objective.amount(), progress[i] + event.amount());
                }
            }
            current.contributions().merge(player, event.amount(), Long::sum);
            if (tryComplete(party, player, state.nameOf(player), def, current)) {
                completed.add(def);
            }
        }
        return completed;
    }

    /** Entrega ítems del inventario para los objetivos {@code DELIVER_ITEM} de una misión aceptada. */
    public ActionResult deliver(CaricraftState state, UUID player, String missionId, InventoryAccess inventory) {
        Party party = state.partyOf(player);
        MissionDefinition def = catalog.get(missionId).orElse(null);
        if (party == null || def == null) {
            return ActionResult.fail("unknown_mission");
        }
        MissionState current = stateFor(party, player, def);
        if (current == null || current.status() != MissionStatus.ACCEPTED) {
            return ActionResult.fail("mission_not_active");
        }
        long delivered = 0;
        long[] progress = current.progress();
        for (int i = 0; i < def.objectives().size(); i++) {
            Objective objective = def.objectives().get(i);
            if (objective.type() != ObjectiveType.DELIVER_ITEM) {
                continue;
            }
            long needed = objective.amount() - progress[i];
            if (needed > 0) {
                long removed = Math.max(0, Math.min(needed, inventory.remove(objective.target(), needed)));
                progress[i] += removed;
                delivered += removed;
            }
        }
        if (delivered == 0) {
            return ActionResult.fail("nothing_to_deliver");
        }
        current.contributions().merge(player, delivered, Long::sum);
        party.incrementStat(ObjectiveType.DELIVER_ITEM.statKey(), delivered);
        if (tryComplete(party, player, state.nameOf(player), def, current)) {
            return ActionResult.ok("mission_completed", def.id());
        }
        return ActionResult.ok("delivered", delivered);
    }

    private boolean tryComplete(Party party, UUID player, String playerName, MissionDefinition def, MissionState current) {
        for (int i = 0; i < def.objectives().size(); i++) {
            if (current.progress()[i] < def.objectives().get(i).amount()) {
                return false;
            }
        }
        if (def.requireAllMembers()
                && !party.members().stream().allMatch(m -> current.contributions().getOrDefault(m, 0L) > 0)) {
            return false;
        }
        current.setStatus(MissionStatus.COMPLETED);
        current.setCompletedAt(clock.now());
        chronicle.add(party, ChronicleEntry.MISSION_COMPLETED, player, playerName, def.id());
        return true;
    }
}
