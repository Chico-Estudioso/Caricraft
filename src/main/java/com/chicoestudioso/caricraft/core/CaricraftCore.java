package com.chicoestudioso.caricraft.core;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.box.BoxService;
import com.chicoestudioso.caricraft.core.camp.CampService;
import com.chicoestudioso.caricraft.core.challenge.ChallengeService;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleService;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.lens.LensService;
import com.chicoestudioso.caricraft.core.market.MarketCatalog;
import com.chicoestudioso.caricraft.core.market.MarketService;
import com.chicoestudioso.caricraft.core.mission.MissionCatalog;
import com.chicoestudioso.caricraft.core.mission.MissionDefinition;
import com.chicoestudioso.caricraft.core.mission.MissionService;
import com.chicoestudioso.caricraft.core.mission.ObjectiveType;
import com.chicoestudioso.caricraft.core.mission.ProgressEvent;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.core.party.PartyService;
import com.chicoestudioso.caricraft.core.party.PlayerProfile;
import com.chicoestudioso.caricraft.core.shop.AppCatalog;
import com.chicoestudioso.caricraft.core.shop.ShopService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fachada del dominio: agrupa estado y servicios. No conoce Minecraft; el servidor la alimenta
 * con eventos y la consulta para construir snapshots.
 */
public final class CaricraftCore {
    public static final String STAT_NOTES = "notes";

    private final CaricraftState state;
    private final CaricraftConfig config;
    private final Clock clock;
    private final EconomyService economy;
    private final ChronicleService chronicle;
    private final ShopService shop;
    private final MissionService missions;
    private final PartyService parties;
    private final ChallengeService challenge;
    private final MarketService market;
    private final com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService cobbleverse;
    private final com.chicoestudioso.caricraft.core.cobbleverse.LuckService luck;
    private final com.chicoestudioso.caricraft.core.cobbleverse.HuntService hunt;
    private final com.chicoestudioso.caricraft.core.cobbleverse.DuoDexService duoDex;
    private final com.chicoestudioso.caricraft.core.cobbleverse.ExpeditionService expeditions;
    private final CampService camp;
    private final LensService lens;
    private final BoxService box;

    public CaricraftCore(CaricraftState state, CaricraftConfig config, MissionCatalog missionCatalog, Clock clock) {
        this(state, config, missionCatalog, MarketCatalog.of(java.util.List.of(), source -> true, item -> true), clock);
    }

    public CaricraftCore(CaricraftState state, CaricraftConfig config, MissionCatalog missionCatalog,
                         MarketCatalog marketCatalog, Clock clock) {
        this(state, config, missionCatalog, marketCatalog,
                com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog.empty(), clock);
    }

    public CaricraftCore(CaricraftState state, CaricraftConfig config, MissionCatalog missionCatalog,
                         MarketCatalog marketCatalog, com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseCatalog cobbleverseCatalog,
                         Clock clock) {
        this.cobbleverse = new com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService(config, cobbleverseCatalog, clock);
        this.luck = new com.chicoestudioso.caricraft.core.cobbleverse.LuckService(cobbleverse);
        this.hunt = new com.chicoestudioso.caricraft.core.cobbleverse.HuntService(config);
        this.duoDex = new com.chicoestudioso.caricraft.core.cobbleverse.DuoDexService();
        this.expeditions = new com.chicoestudioso.caricraft.core.cobbleverse.ExpeditionService(config);
        this.state = state;
        this.config = config;
        this.clock = clock;
        this.economy = new EconomyService(config, clock);
        this.chronicle = new ChronicleService(config, clock);
        this.shop = new ShopService(AppCatalog.createDefault(config), economy, chronicle);
        this.missions = new MissionService(missionCatalog, shop, economy, chronicle, config, clock);
        this.parties = new PartyService(config, clock, economy, chronicle);
        this.challenge = new ChallengeService(config, economy, chronicle);
        this.market = new MarketService(config, economy, clock, marketCatalog);
        this.camp = new CampService(config, economy, chronicle, shop, clock);
        this.lens = new LensService(config, chronicle, shop, clock);
        this.box = new BoxService(shop);
    }

    public BoxService box() {
        return box;
    }

    public LensService lens() {
        return lens;
    }

    public CampService camp() {
        return camp;
    }

    public com.chicoestudioso.caricraft.core.cobbleverse.HuntService hunt() {
        return hunt;
    }

    public com.chicoestudioso.caricraft.core.cobbleverse.DuoDexService duoDex() {
        return duoDex;
    }

    public com.chicoestudioso.caricraft.core.cobbleverse.ExpeditionService expeditions() {
        return expeditions;
    }

    /** Cajas, tragaperras, mejoras y fusiones del CobbleCentro. */
    public com.chicoestudioso.caricraft.core.cobbleverse.LuckService luck() {
        return luck;
    }

    public com.chicoestudioso.caricraft.core.cobbleverse.CobbleverseService cobbleverse() {
        return cobbleverse;
    }

    public MarketService market() {
        return market;
    }

    public ChallengeService challenge() {
        return challenge;
    }

    public CaricraftState state() {
        return state;
    }

    public CaricraftConfig config() {
        return config;
    }

    public Clock clock() {
        return clock;
    }

    public EconomyService economy() {
        return economy;
    }

    public ChronicleService chronicle() {
        return chronicle;
    }

    public ShopService shop() {
        return shop;
    }

    public MissionService missions() {
        return missions;
    }

    public PartyService parties() {
        return parties;
    }

    /** Al entrar un jugador. Devuelve su perfil (creado si es nuevo). */
    public PlayerProfile onPlayerJoin(UUID player, String name) {
        return parties.ensureProfile(state, player, name);
    }

    public Party partyOf(UUID player) {
        return state.partyOf(player);
    }

    /** Aplica un evento de progreso y devuelve las misiones completadas. */
    public List<MissionDefinition> progress(UUID player, ProgressEvent event) {
        return missions.onProgress(state, player, event);
    }

    /**
     * El jugador está en un bioma. Si es nuevo para el grupo, lo registra, escribe la crónica, paga la
     * bonificación de Exploración (si el grupo tiene la app) y genera el evento {@code DISCOVER_BIOME}.
     */
    public BiomeOutcome onBiomeSampled(UUID player, String biomeId, boolean changedBiome) {
        Party party = state.partyOf(player);
        if (party == null) {
            return BiomeOutcome.NONE;
        }
        List<MissionDefinition> completed = new ArrayList<>();
        if (changedBiome) {
            completed.addAll(progress(player, ProgressEvent.of(ObjectiveType.VISIT_BIOME, 1, biomeId)));
        }
        if (!party.discoveredBiomes().add(biomeId)) {
            return new BiomeOutcome(false, 0, completed);
        }
        chronicle.add(party, ChronicleEntry.BIOME_DISCOVERED, player, state.nameOf(player), biomeId);
        long reward = 0;
        if (config.biomeDiscoveryReward > 0 && shop.hasApp(party, AppCatalog.EXPLORER)) {
            EconomyService.Result result = economy.credit(party.wallet(), config.biomeDiscoveryReward,
                    "biome_discovery", biomeId, "biome:" + party.id() + ":" + biomeId);
            if (result == EconomyService.Result.OK) {
                reward = config.biomeDiscoveryReward;
            }
        }
        completed.addAll(progress(player, ProgressEvent.of(ObjectiveType.DISCOVER_BIOME, 1, biomeId)));
        return new BiomeOutcome(true, reward, completed);
    }

    public record BiomeOutcome(boolean discovered, long reward, List<MissionDefinition> completedMissions) {
        public static final BiomeOutcome NONE = new BiomeOutcome(false, 0, List.of());
    }

    /** Añade una nota del jugador a las crónicas (requiere la app Crónicas). */
    public ActionResult addNote(UUID player, String rawText) {
        Party party = state.partyOf(player);
        if (party == null) {
            return ActionResult.fail("no_profile");
        }
        if (!shop.hasApp(party, AppCatalog.CHRONICLES)) {
            return ActionResult.fail("app_locked", AppCatalog.CHRONICLES);
        }
        String text = ChronicleService.sanitizeNote(rawText);
        if (text.isEmpty()) {
            return ActionResult.fail("note_empty");
        }
        chronicle.add(party, ChronicleEntry.NOTE, player, state.nameOf(player), text);
        party.incrementStat(STAT_NOTES, 1);
        return ActionResult.ok("note_added");
    }

    /** Registra en crónicas un hecho externo (p. ej. capturas de Cobblemon). */
    public void recordChronicle(UUID player, String type, String... args) {
        Party party = state.partyOf(player);
        if (party != null) {
            chronicle.add(party, type, player, state.nameOf(player), args);
        }
    }
}
