package com.chicoestudioso.caricraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración del servidor ({@code config/caricraft.json}). Los valores se validan al cargar.
 * Los precios y recompensas del cliente siempre vienen del servidor.
 */
public final class CaricraftConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Caricraft Mod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String FILE_NAME = "caricraft.json";
    /** Versión del formato de configuración; permite migrar valores por defecto antiguos. */
    public static final int CURRENT_VERSION = 3;

    /** Versión con la que se escribió el archivo (0 = anterior a las migraciones). */
    public int configVersion = CURRENT_VERSION;

    /**
     * Zona horaria de los reinicios diarios (regalo misterioso, ofertas, misiones diarias y semanales): cambian a las
     * 00:00 de esta zona. Identificador IANA, p. ej. "Europe/Madrid", "America/Mexico_City" o "UTC".
     */
    public String timeZone = "Europe/Madrid";

    /** Entregar un CariPhone la primera vez que un jugador entra al servidor (solo si {@code phone.personal} es falso). */
    public boolean giveStarterPhone = true;
    /** CariPhone personal: uno por jugador (ver docs/PHONE_AND_APPS.md). */
    public PhoneConfig phone = new PhoneConfig();
    /** Consejos del CariPhone en el chat. */
    public TipsConfig tips = new TipsConfig();
    /** Saldo inicial de un grupo nuevo. */
    public long startingBalance = 0;
    /** Tamaño máximo de grupo. El MVP está pensado para dúos (2). */
    public int maxPartySize = 2;
    /** Segundos que dura una invitación. */
    public int inviteExpirySeconds = 300;
    /** Invitaciones salientes pendientes por jugador. */
    public int maxPendingInvites = 3;
    /** Horas de espera tras desvincularse antes de poder unirse a otro grupo. */
    public int unlinkCooldownHours = 24;
    /** Asientos del ledger que se conservan por grupo. */
    public int ledgerSize = 100;
    /** Entradas de crónica que se conservan por grupo. */
    public int chronicleMaxEntries = 300;
    /** Guardar coordenadas exactas en las crónicas. */
    public boolean chronicleRecordCoordinates = false;
    /** Carins por bioma nuevo cuando el grupo tiene la app Exploración. */
    public long biomeDiscoveryReward = 5;
    /** Multiplicador global de recompensas de misiones. */
    public double missionRewardMultiplier = 1.0;
    /** Acciones del teléfono permitidas por segundo y jugador. */
    public int actionsPerSecond = 20;
    /** Cada cuántos ticks se muestrea bioma y distancia de cada jugador. */
    public int progressSampleTicks = 40;
    /** Sobrescritura de precios por id de artículo (p. ej. "chronicles": 300). */
    public Map<String, Long> prices = new LinkedHashMap<>();
    /** Artículos de la tienda desactivados por id. */
    public List<String> disabledItems = new ArrayList<>();
    /** CariLens: álbum de fotos (ver docs/CARILENS.md). */
    public LensConfig lens = new LensConfig();
    /** CariCamp: campamento y viajes (ver docs/CARICAMP.md). */
    public CampConfig camp = new CampConfig();
    /** Mercado: objetos por Carins (ver docs/MARKET.md). */
    public MarketConfig market = new MarketConfig();
    /** Cobbleverse: regalo misterioso, tienda y venta de Pokémon en CobbleDollars (ver docs/COBBLEVERSE.md). */
    public CobbleverseConfig cobbleverse = new CobbleverseConfig();
    /** Desafío Dúo: noches con mobs vanilla equipados (ver docs/CHALLENGE.md). */
    public ChallengeConfig challenge = new ChallengeConfig();

    /** Ajustes del CariPhone personal. */
    public static final class PhoneConfig {
        /**
         * Cada jugador tiene un único CariPhone ligado a él: no se puede tirar ni guardar en contenedores, no se
         * pierde al morir y, si falta, el servidor da otro y el anterior deja de valer.
         */
        public boolean personal = true;
        /** Segundos sin CariPhone antes de dar uno nuevo (cubre el rato en que está fuera del inventario). */
        public int replaceAfterSeconds = 3;

        void validate() {
            replaceAfterSeconds = clamp(replaceAfterSeconds, 1, 300);
        }
    }

    /** Consejos periódicos en el chat (cada jugador puede desactivarlos en Ajustes del CariPhone). */
    public static final class TipsConfig {
        public boolean enabled = true;
        /** Minutos entre consejos. */
        public int intervalMinutes = 15;
        /** Minutos desde que entra el jugador hasta el primer consejo. */
        public int firstDelayMinutes = 4;

        void validate() {
            intervalMinutes = clamp(intervalMinutes, 1, 24 * 60);
            firstDelayMinutes = clamp(firstDelayMinutes, 0, 24 * 60);
        }
    }

    /** Ajustes de CariLens. */
    public static final class LensConfig {
        /** Fotos que caben en el álbum sin ampliaciones. */
        public int basePhotos = 12;
        /** Fotos extra por cada ampliación de álbum. */
        public int photosPerExpansion = 12;
        /** Tamaño máximo de una foto (bytes). */
        public int maxPhotoBytes = 160 * 1024;
        /** Segundos mínimos entre fotos de un jugador. */
        public int uploadCooldownSeconds = 10;

        LensConfig validate() {
            basePhotos = clamp(basePhotos, 1, 200);
            photosPerExpansion = clamp(photosPerExpansion, 0, 200);
            maxPhotoBytes = clamp(maxPhotoBytes, 16 * 1024, 512 * 1024);
            uploadCooldownSeconds = clamp(uploadCooldownSeconds, 0, 3600);
            return this;
        }
    }

    /** Ajustes de CariCamp. */
    public static final class CampConfig {
        /** Carins por viajar al campamento. */
        public long campTravelCost = 30;
        /** Carins por viajar junto al compañero. */
        public long partnerTravelCost = 50;
        /** Espera entre viajes de un mismo jugador. */
        public int cooldownSeconds = 300;
        /** Bloquea los viajes durante la noche de desafío. */
        public boolean blockDuringChallenge = true;
        /** Distancia máxima a la hoguera para fijar el campamento. */
        public int campfireRadius = 3;
        /** Segundos que dura una petición de viaje al compañero sin responder. */
        public int requestSeconds = 60;
        /** Cuenta atrás (sin moverse) antes de viajar. */
        public int warmupSeconds = 3;

        CampConfig validate() {
            campTravelCost = Math.max(0, Math.min(100_000, campTravelCost));
            partnerTravelCost = Math.max(0, Math.min(100_000, partnerTravelCost));
            cooldownSeconds = clamp(cooldownSeconds, 0, 86_400);
            campfireRadius = clamp(campfireRadius, 1, 8);
            requestSeconds = clamp(requestSeconds, 10, 600);
            warmupSeconds = clamp(warmupSeconds, 0, 30);
            return this;
        }
    }

    /** Parte Cobbleverse del Mercado. Necesita Cobblemon; la tienda y la venta, también CobbleDollars. */
    public static final class CobbleverseConfig {
        /** Activa la parte Cobbleverse (y los objetos de Cobblemon en el Mercado de Carins). Desactivado por defecto. */
        public boolean enabled = false;
        /** Regalo misterioso diario para cada jugador. */
        public boolean mysteryGift = true;
        /** Tienda en CobbleDollars (Pokémon especiales y objetos raros). */
        public boolean shop = true;
        /** Vender Pokémon del equipo por CobbleDollars. */
        public boolean sellPokemon = true;
        /** Ventas de Pokémon por jugador y día. */
        public int maxSalesPerDay = 10;
        /** Multiplica lo que se paga por cada Pokémon. */
        public double sellPriceMultiplier = 1.0;
        /** Permite vender legendarios, singulares y ultraentes (por defecto no, para evitar sustos). */
        public boolean sellLegendaries = false;
        /** Regalos misteriosos por jugador y día (se reinician a las 00:00 de {@code timeZone}). */
        public int giftsPerDay = 2;
        /** Intercambio prodigioso: un Pokémon del equipo por otro al azar (de otro jugador o generado). */
        public boolean wonderTrade = true;
        /** Intercambios prodigiosos por jugador y día. */
        public int wonderTradesPerDay = 1;
        /** Probabilidad de que el Pokémon generado (si no hay ninguno de otro jugador) sea variocolor. */
        public double wonderShinyChance = 0.02;
        /** Pokémon de otros jugadores que esperan en el intercambio prodigioso (los más antiguos se van). */
        public int wonderPoolSize = 100;
        /** Cajas de premios en el CobbleCentro (contenido en {@code caricraft_cobbleverse.json}). */
        public boolean crates = true;
        /** Tragaperras en el CobbleCentro. */
        public boolean slots = true;
        /** Mejoras de Pokémon (IV, nivel, variocolor) por CobbleDollars. */
        public boolean upgrades = true;
        /** Fusiones de objetos (p. ej. Ultra Balls → Master Ball con probabilidad). */
        public boolean fusions = true;
        /** Aviso al dúo con brújula cuando aparece un Pokémon variocolor cerca de uno de los dos. */
        public boolean shinyAlerts = true;
        /** Minutos que dura el aviso de variocolor (si nadie lo captura antes). */
        public int shinyAlertMinutes = 5;
        /** Centro Pokémon de bolsillo: curar el equipo desde el móvil pagando y con espera. */
        public boolean pocketHeal = true;
        /** Precio en CobbleDollars de curar el equipo desde el móvil. */
        public long pocketHealPrice = 1500;
        /** Minutos de espera entre curas desde el móvil. */
        public int pocketHealCooldownMinutes = 15;
        /** Caza del día: tres objetivos de captura con premio y bonus de dúo. */
        public boolean dailyHunt = true;
        /** Capturas que tiene que aportar cada uno del dúo para el bonus de dúo de la caza. */
        public int huntDuoMinEach = 3;
        /** Cazas completas (en dúo) en la semana para el premio semanal. */
        public int huntWeeklyClears = 4;
        /** Multiplica los premios de la caza del día. */
        public double huntRewardMultiplier = 1.0;
        /** Expediciones: mandar un Pokémon unas horas a buscar cosas (ingreso pasivo modesto). */
        public boolean expeditions = true;
        /** Expediciones que puede empezar cada jugador al día. */
        public int expeditionsPerDay = 3;
        /** Multiplica el dinero de las expediciones. */
        public double expeditionRewardMultiplier = 1.0;
        /** Pokédex del dúo: variocolores, capturas raras y metas compartidas. */
        public boolean duoDex = true;
        /**
         * Opcional y desactivado: apariciones según el level cap de los entrenadores (RCT). Si se activa, los salvajes
         * no salen por encima del cap del jugador cercano con el cap más bajo. Por defecto los niveles quedan como en
         * Cobbleverse (Cobblemon los acerca al Pokémon de más nivel del equipo). Sin RCT instalado no hace nada.
         */
        public boolean levelCapSpawns = false;
        /** Niveles por encima del cap que se corrigen bajando al cap; si se pasa más, esa aparición se cancela. */
        public int levelCapMargin = 5;
        /** Level cap a partir del cual pueden aparecer legendarios, singulares y ultraentes salvajes. */
        public int legendaryMinLevelCap = 60;

        CobbleverseConfig validate() {
            maxSalesPerDay = clamp(maxSalesPerDay, 0, 100);
            wonderTradesPerDay = clamp(wonderTradesPerDay, 0, 50);
            giftsPerDay = clamp(giftsPerDay, 0, 20);
            shinyAlertMinutes = clamp(shinyAlertMinutes, 1, 60);
            levelCapMargin = clamp(levelCapMargin, 0, 100);
            legendaryMinLevelCap = clamp(legendaryMinLevelCap, 1, 100);
            pocketHealPrice = Math.max(0, Math.min(10_000_000, pocketHealPrice));
            pocketHealCooldownMinutes = clamp(pocketHealCooldownMinutes, 0, 24 * 60);
            huntDuoMinEach = clamp(huntDuoMinEach, 0, 100);
            huntWeeklyClears = clamp(huntWeeklyClears, 1, 7);
            if (!(huntRewardMultiplier >= 0) || huntRewardMultiplier > 100) {
                huntRewardMultiplier = 1.0;
            }
            expeditionsPerDay = clamp(expeditionsPerDay, 0, 24);
            if (!(expeditionRewardMultiplier >= 0) || expeditionRewardMultiplier > 100) {
                expeditionRewardMultiplier = 1.0;
            }
            wonderPoolSize = clamp(wonderPoolSize, 1, 1000);
            if (!(wonderShinyChance >= 0) || wonderShinyChance > 1) {
                wonderShinyChance = 0.02;
            }
            if (!(sellPriceMultiplier >= 0) || sellPriceMultiplier > 100) {
                sellPriceMultiplier = 1.0;
            }
            return this;
        }
    }

    /** Ajustes del Mercado. */
    public static final class MarketConfig {
        /** Activa el Mercado. */
        public boolean enabled = true;
        /** Incluye objetos de Cobblemon/COBBLEVERSE (Poké Balls, caramelos, piedras…). Desactivado por defecto. */
        public boolean includeCobblemonItems = false;
        /** Ofertas que salen cada día. */
        public int offersPerDay = 8;

        MarketConfig validate() {
            offersPerDay = clamp(offersPerDay, 1, 30);
            return this;
        }
    }

    /** Ajustes del Desafío Dúo. */
    public static final class ChallengeConfig {
        /** Activa el Desafío Dúo. Si es falso, la app y sus misiones desaparecen. */
        public boolean enabled = true;
        /** Cada cuántas noches hay un desafío obligatorio. */
        public int intervalNights = 3;
        /** Mobs vanilla que aparecen solo durante el desafío (deben quitarse de la lista de MobsBeGone). */
        public List<String> mobs = new ArrayList<>(List.of(
                "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:skeleton", "minecraft:stray",
                "minecraft:spider", "minecraft:cave_spider", "minecraft:witch", "minecraft:slime", "minecraft:enderman"));
        /** Carins base por mob derrotado (se suma el nivel de desafío - 1). */
        public long killReward = 2;
        /** Carins por sobrevivir una noche de desafío completa. */
        public long nightReward = 50;
        /** Nivel máximo de desafío (equipamiento y fuerza de los mobs). */
        public int maxLevel = 30;
        /** Solo para grupos de versiones anteriores: su nivel inicial era 1 + noches superadas / este valor. */
        public int nightsPerLevel = 1;
        /** Experiencia para pasar del nivel 1 al 2; cada nivel siguiente pide {@code xpGrowth} más. */
        public long xpPerLevel = 150;
        /** Cuánto crece la experiencia necesaria por nivel (0.10 = un 10 % más cada nivel). */
        public double xpGrowth = 0.10;
        /** Experiencia por mob del desafío derrotado (más nivel/5; triple si es élite). */
        public long xpPerKill = 3;
        /** Experiencia por escuadrón derrotado. */
        public long xpPerSquad = 25;
        /** Experiencia por noche de desafío superada. */
        public long xpPerNight = 60;
        /** Experiencia por cofre del desafío conquistado. */
        public long xpPerChest = 40;
        /**
         * Cofres del desafío por el mundo (de día y de noche): aparecen cerca del dúo, lejos de estructuras y
         * construcciones, y se abren tras una emboscada de mobs o venciendo a un Pokémon guardián.
         */
        public boolean worldChests = true;
        /** Cada cuántos minutos se intenta sacar un cofre del desafío para cada grupo conectado. */
        public int chestIntervalMinutes = 20;
        /** Probabilidad de que salga en cada intento. */
        public double chestChance = 0.75;
        /** Minutos que espera un cofre sin conquistar antes de desvanecerse. */
        public int chestLifetimeMinutes = 30;
        /** Con Cobblemon: probabilidad de que lo guarde un Pokémon salvaje fuerte en vez de una emboscada. */
        public double chestGuardianChance = 0.4;
        /** Carins por conquistar un cofre, más 5 por nivel. */
        public long chestReward = 30;
        /** Tres oleadas por noche: la segunda y la tercera suman +1 y +2 niveles. */
        public boolean waves = true;
        /** Mobs élite (con nombre, brillan, +3 niveles de equipo y triple recompensa). */
        public boolean elites = true;
        /** Barra de jefe con lo que queda de noche (y aviso poco antes de que empiece). */
        public boolean bossBar = true;
        /** Hordas: a más nivel, más probable que un mob aparezca acompañado. */
        public boolean hordes = true;
        /** Impide dormir durante la noche de desafío. */
        public boolean blockSleep = true;
        /** Fuera del desafío, impide que aparezcan los mobs de la lista (el mundo queda como sin ellos). */
        public boolean gateMobsOutsideChallenge = true;
        /** Radio (bloques) alrededor de un jugador en desafío donde se permiten los mobs. */
        public int radius = 128;
        /** Escuadrones que protegen un cofre de botín (bloqueado hasta derrotarlos). */
        public boolean squads = true;
        /** Escuadrones como mucho por noche (1, y uno más cada 8 niveles). */
        public int maxSquadsPerNight = 3;
        /** Carins por derrotar un escuadrón, más 5 por nivel. */
        public long squadReward = 40;

        ChallengeConfig validate() {
            intervalNights = clamp(intervalNights, 1, 30);
            killReward = Math.max(0, Math.min(1000, killReward));
            nightReward = Math.max(0, Math.min(100_000, nightReward));
            maxLevel = clamp(maxLevel, 1, 100);
            nightsPerLevel = clamp(nightsPerLevel, 1, 50);
            xpPerLevel = Math.max(1, Math.min(1_000_000, xpPerLevel));
            if (!(xpGrowth >= 0) || xpGrowth > 10) {
                xpGrowth = 0.10;
            }
            xpPerKill = Math.max(0, Math.min(100_000, xpPerKill));
            xpPerSquad = Math.max(0, Math.min(100_000, xpPerSquad));
            xpPerNight = Math.max(0, Math.min(100_000, xpPerNight));
            xpPerChest = Math.max(0, Math.min(100_000, xpPerChest));
            chestIntervalMinutes = clamp(chestIntervalMinutes, 1, 24 * 60);
            chestLifetimeMinutes = clamp(chestLifetimeMinutes, 2, 24 * 60);
            if (!(chestChance >= 0) || chestChance > 1) {
                chestChance = 0.75;
            }
            if (!(chestGuardianChance >= 0) || chestGuardianChance > 1) {
                chestGuardianChance = 0.4;
            }
            chestReward = Math.max(0, Math.min(100_000, chestReward));
            radius = clamp(radius, 32, 256);
            maxSquadsPerNight = clamp(maxSquadsPerNight, 0, 6);
            squadReward = Math.max(0, Math.min(100_000, squadReward));
            if (mobs == null) {
                mobs = new ArrayList<>();
            }
            mobs.removeIf(id -> id == null || id.isBlank());
            return this;
        }
    }

    public static CaricraftConfig loadOrCreate(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        CaricraftConfig config = new CaricraftConfig();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                CaricraftConfig loaded = GSON.fromJson(json, CaricraftConfig.class);
                if (loaded != null) {
                    if (!json.has("configVersion")) {
                        loaded.configVersion = 0;
                    }
                    config = loaded.migrated();
                }
            } catch (IOException | JsonParseException | IllegalStateException e) {
                LOGGER.error("No se pudo leer {}; se usan valores por defecto.", file, e);
                return config.validated();
            }
        }
        config = config.validated();
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("No se pudo escribir {}.", file, e);
        }
        return config;
    }

    /**
     * Actualiza archivos antiguos: solo cambia valores que siguen en el antiguo valor por defecto, para respetar
     * lo que el administrador haya ajustado a mano.
     */
    CaricraftConfig migrated() {
        if (configVersion < 2 && challenge != null) {
            // v2: el desafío escala sin techo bajo (antes máx. 5 y un nivel cada 2 noches).
            if (challenge.maxLevel == 5) {
                challenge.maxLevel = 30;
            }
            if (challenge.nightsPerLevel == 2) {
                challenge.nightsPerLevel = 1;
            }
            LOGGER.info("Configuración de Caricraft Mod migrada a la versión {}.", CURRENT_VERSION);
        }
        if (configVersion < 3 && market != null && market.includeCobblemonItems) {
            // v3: la opción de Cobbleverse pasa a su propio bloque y activa también la tienda en CobbleDollars.
            if (cobbleverse == null) {
                cobbleverse = new CobbleverseConfig();
            }
            cobbleverse.enabled = true;
        }
        configVersion = CURRENT_VERSION;
        return this;
    }

    /** Corrige valores fuera de rango y devuelve la misma instancia. */
    public CaricraftConfig validated() {
        startingBalance = Math.max(0, startingBalance);
        maxPartySize = clamp(maxPartySize, 1, 2);
        inviteExpirySeconds = clamp(inviteExpirySeconds, 30, 3600);
        maxPendingInvites = clamp(maxPendingInvites, 1, 10);
        unlinkCooldownHours = clamp(unlinkCooldownHours, 0, 24 * 30);
        ledgerSize = clamp(ledgerSize, 10, 1000);
        chronicleMaxEntries = clamp(chronicleMaxEntries, 20, 5000);
        biomeDiscoveryReward = Math.max(0, biomeDiscoveryReward);
        if (!(missionRewardMultiplier >= 0) || missionRewardMultiplier > 100) {
            missionRewardMultiplier = 1.0;
        }
        actionsPerSecond = clamp(actionsPerSecond, 1, 100);
        progressSampleTicks = clamp(progressSampleTicks, 10, 200);
        if (prices == null) {
            prices = new LinkedHashMap<>();
        }
        prices.replaceAll((id, price) -> price == null ? 0L : Math.max(0L, price));
        if (disabledItems == null) {
            disabledItems = new ArrayList<>();
        }
        if (phone == null) {
            phone = new PhoneConfig();
        }
        phone.validate();
        if (tips == null) {
            tips = new TipsConfig();
        }
        tips.validate();
        if (lens == null) {
            lens = new LensConfig();
        }
        lens.validate();
        if (camp == null) {
            camp = new CampConfig();
        }
        camp.validate();
        if (market == null) {
            market = new MarketConfig();
        }
        market.validate();
        if (cobbleverse == null) {
            cobbleverse = new CobbleverseConfig();
        }
        cobbleverse.validate();
        if (challenge == null) {
            challenge = new ChallengeConfig();
        }
        challenge.validate();
        return this;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
