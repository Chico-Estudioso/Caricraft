package com.chicoestudioso.caricraft.server;

import com.chicoestudioso.caricraft.core.ActionResult;
import com.chicoestudioso.caricraft.core.CaricraftCore;
import com.chicoestudioso.caricraft.core.chronicle.ChronicleEntry;
import com.chicoestudioso.caricraft.core.economy.EconomyService;
import com.chicoestudioso.caricraft.core.party.Party;
import com.chicoestudioso.caricraft.server.challenge.ChallengeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comandos:
 * <ul>
 *   <li>{@code /carins}: saldo propio.</li>
 *   <li>{@code /carins give|take|set <jugadores> <cantidad>}: administración (nivel 2).</li>
 *   <li>{@code /caricraft duo invite|accept|decline <jugador>}, {@code /caricraft duo leave}.</li>
 *   <li>{@code /caricraft info <jugador>} y {@code /caricraft reload} (nivel 2).</li>
 *   <li>{@code /tpduo [ir|traer|aceptar|rechazar|campamento]} y {@code /caricraft viaje ...}: viajes de CariCamp.</li>
 *   <li>{@code /caricraft desafio [estado]}; {@code preparar} (nivel 3), {@code iniciar|terminar|cofre|nivel <n>} (nivel 2).</li>
 * </ul>
 */
public final class CaricraftCommands {
    private CaricraftCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("carins")
                .executes(CaricraftCommands::balance)
                .then(Commands.literal("give").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1, EconomyService.MAX_BALANCE))
                                        .executes(ctx -> adjust(ctx, Mode.GIVE)))))
                .then(Commands.literal("take").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1, EconomyService.MAX_BALANCE))
                                        .executes(ctx -> adjust(ctx, Mode.TAKE)))))
                .then(Commands.literal("set").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0, EconomyService.MAX_BALANCE))
                                        .executes(ctx -> adjust(ctx, Mode.SET))))));

        // Viajes estilo /tpa con el compañero (requiere TP Dúo; el campamento, CariCamp).
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> travel = Commands.literal("tpduo")
                .executes(ctx -> travel(ctx, "ir"))
                .then(Commands.literal("ir").executes(ctx -> travel(ctx, "ir")))
                .then(Commands.literal("traer").executes(ctx -> travel(ctx, "traer")))
                .then(Commands.literal("aceptar").executes(ctx -> travel(ctx, "aceptar")))
                .then(Commands.literal("rechazar").executes(ctx -> travel(ctx, "rechazar")))
                .then(Commands.literal("campamento").executes(ctx -> travel(ctx, "campamento")));
        dispatcher.register(travel);

        dispatcher.register(Commands.literal("caricraft")
                .then(Commands.literal("viaje")
                        .then(Commands.literal("ir").executes(ctx -> travel(ctx, "ir")))
                        .then(Commands.literal("traer").executes(ctx -> travel(ctx, "traer")))
                        .then(Commands.literal("aceptar").executes(ctx -> travel(ctx, "aceptar")))
                        .then(Commands.literal("rechazar").executes(ctx -> travel(ctx, "rechazar")))
                        .then(Commands.literal("campamento").executes(ctx -> travel(ctx, "campamento"))))
                .then(Commands.literal("consejo").executes(CaricraftCommands::tip))
                .then(Commands.literal("cobbleverse").requires(src -> src.hasPermission(4))
                        .then(Commands.literal("prueba").executes(CaricraftCommands::cobbleverseDiagnostics))
                        .then(Commands.literal("expediciones").executes(CaricraftCommands::finishExpeditions))
                        .then(Commands.literal("variocolor").executes(ctx -> shinyTest(ctx, "cobblemon:gible"))
                                .then(Commands.argument("especie", com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .executes(ctx -> shinyTest(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "especie")))))
                        .then(Commands.literal("cap").executes(CaricraftCommands::levelCap))
                        .then(Commands.literal("herir").executes(CaricraftCommands::hurtParty))
                        .then(Commands.literal("captura")
                                .then(Commands.argument("especie", com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .executes(ctx -> fakeCapture(ctx, false))
                                        .then(Commands.literal("variocolor").executes(ctx -> fakeCapture(ctx, true))))))
                .then(Commands.literal("reload").requires(src -> src.hasPermission(2))
                        .executes(CaricraftCommands::reload))
                .then(Commands.literal("desafio")
                        .executes(CaricraftCommands::challengeStatus)
                        .then(Commands.literal("estado").executes(CaricraftCommands::challengeStatus))
                        .then(Commands.literal("preparar").requires(src -> src.hasPermission(3))
                                .executes(CaricraftCommands::challengePrepare))
                        .then(Commands.literal("iniciar").requires(src -> src.hasPermission(2))
                                .executes(CaricraftCommands::challengeStart))
                        .then(Commands.literal("terminar").requires(src -> src.hasPermission(2))
                                .executes(CaricraftCommands::challengeStop))
                        .then(Commands.literal("jefe").requires(src -> src.hasPermission(2))
                                .executes(CaricraftCommands::challengeBoss))
                        .then(Commands.literal("cofre").requires(src -> src.hasPermission(2))
                                .executes(ctx -> challengeChest(ctx, null))
                                .then(Commands.literal("emboscada").executes(ctx -> challengeChest(ctx,
                                        com.chicoestudioso.caricraft.server.challenge.ChallengeChests.Kind.AMBUSH)))
                                .then(Commands.literal("guardian").executes(ctx -> challengeChest(ctx,
                                        com.chicoestudioso.caricraft.server.challenge.ChallengeChests.Kind.GUARDIAN))))
                        .then(Commands.literal("nivel").requires(src -> src.hasPermission(2))
                                .then(Commands.argument("nivel", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                        .executes(CaricraftCommands::challengeLevel))))
                .then(Commands.literal("rutina").requires(src -> src.hasPermission(2))
                        .executes(CaricraftCommands::routineStatus)
                        .then(Commands.literal("evento")
                                .then(Commands.argument("cantidad", LongArgumentType.longArg(1, 1_000_000))
                                        .executes(CaricraftCommands::routineEvent)))
                        .then(Commands.literal("racha")
                                .then(Commands.argument("dias", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3650))
                                        .executes(CaricraftCommands::routineStreak))))
                .then(Commands.literal("info").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(CaricraftCommands::info)))
                .then(Commands.literal("duo")
                        .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> duo(ctx, DuoAction.INVITE))))
                        .then(Commands.literal("accept").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> duo(ctx, DuoAction.ACCEPT))))
                        .then(Commands.literal("decline").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> duo(ctx, DuoAction.DECLINE))))
                        .then(Commands.literal("leave").executes(ctx -> duo(ctx, DuoAction.LEAVE)))));
    }

    private enum Mode { GIVE, TAKE, SET }

    private enum DuoAction { INVITE, ACCEPT, DECLINE, LEAVE }

    private static CaricraftServer mod(CommandContext<CommandSourceStack> ctx) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            ctx.getSource().sendFailure(Component.translatable("caricraft.command.not_ready"));
        }
        return mod;
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        long balance = mod.core().partyOf(player.getUUID()).wallet().balance();
        ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.balance", balance)
                .withStyle(ChatFormatting.GOLD), false);
        return (int) Math.min(Integer.MAX_VALUE, balance);
    }

    private static int adjust(CommandContext<CommandSourceStack> ctx, Mode mode) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        long amount = LongArgumentType.getLong(ctx, "amount");
        CaricraftCore core = mod.core();
        String admin = ctx.getSource().getTextName();
        int done = 0;
        for (ServerPlayer target : targets) {
            core.onPlayerJoin(target.getUUID(), target.getGameProfile().getName());
            Party party = core.partyOf(target.getUUID());
            EconomyService.Result result = switch (mode) {
                case GIVE -> core.economy().credit(party.wallet(), amount, "admin_give", admin, null);
                case TAKE -> core.economy().debit(party.wallet(), Math.min(amount, party.wallet().balance()), "admin_take", admin);
                case SET -> core.economy().set(party.wallet(), amount, "admin_set", admin);
            };
            if (result == EconomyService.Result.OK || (mode == Mode.TAKE && party.wallet().balance() == 0)) {
                done++;
                long balance = party.wallet().balance();
                ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.adjusted",
                        target.getDisplayName(), balance), true);
                mod.sendPartySnapshots(target.getUUID());
            } else {
                ctx.getSource().sendFailure(Component.translatable("caricraft.command.adjust_failed",
                        target.getDisplayName(), result.name()));
            }
        }
        mod.markDirty();
        return done;
    }

    /** Comprueba la integración con Cobblemon y CobbleDollars con un jugador ficticio. */
    private static int cobbleverseDiagnostics(CommandContext<CommandSourceStack> ctx) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return 0;
        }
        java.util.List<String> lines = com.chicoestudioso.caricraft.server.cobbleverse.CobbleverseDiagnostics.run(mod);
        lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal("[Cobbleverse] " + line), false));
        return lines.stream().anyMatch(line -> line.startsWith("FALLO")) ? 0 : 1;
    }

    /** Administración/QA: las expediciones en marcha terminan ya. */
    private static int finishExpeditions(CommandContext<CommandSourceStack> ctx) {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return 0;
        }
        int n = com.chicoestudioso.caricraft.server.cobbleverse.ExpeditionManager.finishAll(mod.server());
        ctx.getSource().sendSuccess(() -> Component.literal("[Cobbleverse] Expediciones terminadas: " + n), true);
        return n;
    }

    /** Administración/QA: aparece un variocolor salvaje a unos 40 bloques y salta el aviso con brújula. */
    private static int shinyTest(CommandContext<CommandSourceStack> ctx, String species)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CaricraftServer mod = CaricraftServer.get();
        var backend = com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat.backend();
        if (mod == null || backend == null) {
            ctx.getSource().sendFailure(Component.literal("[Cobbleverse] Cobblemon no está instalado."));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = species.contains(":") ? species : "cobblemon:" + species;
        if (!backend.speciesExists(id)) {
            ctx.getSource().sendFailure(Component.literal("[Cobbleverse] Especie desconocida: " + id));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        int x = net.minecraft.util.Mth.floor(player.getX() + Math.cos(angle) * 40);
        int z = net.minecraft.util.Mth.floor(player.getZ() + Math.sin(angle) * 40);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        net.minecraft.world.entity.Entity entity = backend.spawnWild(level, id, 20, true, x + 0.5, y, z + 0.5);
        if (entity == null) {
            ctx.getSource().sendFailure(Component.literal("[Cobbleverse] No se pudo crear el Pokémon."));
            return 0;
        }
        com.chicoestudioso.caricraft.server.cobbleverse.ShinyAlerts.onShinySpawn(mod, level, entity, id, 20);
        ctx.getSource().sendSuccess(() -> Component.literal("[Cobbleverse] Variocolor de prueba en " + x + " " + y + " " + z), true);
        return 1;
    }

    /** Administración/QA: level cap de RCT del jugador. */
    private static int levelCap(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        com.chicoestudioso.caricraft.integration.rct.RctBridge.forget(player.getUUID());
        var cap = com.chicoestudioso.caricraft.integration.rct.RctBridge.levelCap(player);
        String text = !com.chicoestudioso.caricraft.integration.rct.RctBridge.available() ? "RCT no está instalado"
                : cap.isPresent() ? "level cap " + cap.getAsInt() : "sin level cap";
        String line = player.getGameProfile().getName() + ": " + text + " · apariciones: "
                + com.chicoestudioso.caricraft.server.cobbleverse.LevelCapStats.summary();
        com.chicoestudioso.caricraft.CaricraftMod.LOGGER.info("[Cobbleverse] {}", line);
        ctx.getSource().sendSuccess(() -> Component.literal("[Cobbleverse] " + line), false);
        return cap.orElse(0);
    }

    /** Administración/QA: deja al equipo a media vida (para probar el Centro Pokémon de bolsillo). */
    private static int hurtParty(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var backend = com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat.backend();
        if (backend == null) {
            ctx.getSource().sendFailure(Component.literal("[Cobbleverse] Cobblemon no está instalado."));
            return 0;
        }
        backend.hurtParty(ctx.getSource().getPlayerOrException());
        ctx.getSource().sendSuccess(() -> Component.literal("[Cobbleverse] Equipo a media vida."), false);
        return 1;
    }

    /**
     * Administración/QA: como si el jugador capturase un Pokémon salvaje de esa especie (se le da y cuenta para la
     * caza del día y la Pokédex del dúo).
     */
    private static int fakeCapture(CommandContext<CommandSourceStack> ctx, boolean shiny)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CaricraftServer mod = CaricraftServer.get();
        var backend = com.chicoestudioso.caricraft.integration.cobblemon.CobblemonCompat.backend();
        if (mod == null || backend == null) {
            ctx.getSource().sendFailure(Component.literal("[Cobbleverse] Cobblemon no está instalado."));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String species = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "especie");
        String id = species.contains(":") ? species : "cobblemon:" + species;
        if (!backend.speciesExists(id)) {
            ctx.getSource().sendFailure(Component.literal("[Cobbleverse] Especie desconocida: " + id));
            return 0;
        }
        Object pokemon = backend.create(id, 15, shiny, 0, java.util.List.of());
        var info = backend.captureInfo(pokemon);
        backend.restore(player, pokemon);
        com.chicoestudioso.caricraft.server.cobbleverse.DuoPokemonManager.onCapture(mod, player, info);
        ctx.getSource().sendSuccess(() -> Component.literal("[Cobbleverse] Captura simulada: " + info), false);
        return 1;
    }

    /** Racha, evento de la semana y medallas del grupo del jugador. */
    private static int routineStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CaricraftCore core = mod.core();
        Party party = core.partyOf(player.getUUID());
        var routine = party.routine();
        var event = core.weekly().eventOf(core.week());
        String line = "racha " + core.streak().current(routine, core.day()) + " (récord " + routine.streakBest() + ")"
                + " · evento " + (event == null ? "-" : event.id() + " " + core.weekly().progress(party, core.week()) + "/" + event.goal())
                + " · medallas " + routine.medals() + " · jefes " + party.challenge().bosses().size();
        com.chicoestudioso.caricraft.CaricraftMod.LOGGER.info("[Rutina] {}: {}", player.getGameProfile().getName(), line);
        ctx.getSource().sendSuccess(() -> Component.literal("[Rutina] " + line), false);
        return 1;
    }

    /** Administración/QA: suma progreso a la meta del evento de esta semana (a la estadística que mide). */
    private static int routineEvent(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CaricraftCore core = mod.core();
        var event = core.weekly().eventOf(core.week());
        if (event == null) {
            ctx.getSource().sendFailure(Component.literal("[Rutina] Los eventos semanales están desactivados."));
            return 0;
        }
        Party party = core.partyOf(player.getUUID());
        core.weekly().progress(party, core.week()); // fija la estadística de partida si es la primera vez esta semana
        long amount = LongArgumentType.getLong(ctx, "cantidad");
        party.incrementStat(event.stat(), amount);
        mod.markDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("[Rutina] +" + amount + " a " + event.stat()), true);
        return 1;
    }

    /** Administración/QA: fija la racha del grupo (como si el último día cumplido fuera ayer). */
    private static int routineStreak(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CaricraftCore core = mod.core();
        Party party = core.partyOf(player.getUUID());
        int days = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "dias");
        var r = party.routine();
        r.restoreStreak(days > 0 ? core.day() - 1 : -1, days, Math.max(r.streakBest(), days), r.shieldWeek(), r.duoPaidDay(),
                new java.util.LinkedHashMap<>(r.activeDay()));
        mod.markDirty();
        mod.sendPartySnapshots(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("[Rutina] Racha: " + days + " días"), true);
        return 1;
    }

    /** Administración/QA: saca ya el jefe del desafío (con el desafío en marcha). */
    private static int challengeBoss(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return 0;
        }
        String result = com.chicoestudioso.caricraft.server.challenge.ChallengeBosses.spawnNow(mod, ctx.getSource().getPlayerOrException());
        if (!"ok".equals(result)) {
            ctx.getSource().sendFailure(Component.translatable("caricraft.command.boss_" + result));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.boss_ok"), true);
        return 1;
    }

    /** Muestra un consejo del CariPhone ahora mismo (aunque estén desactivados). */
    private static int tip(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CaricraftServer mod = CaricraftServer.get();
        if (mod == null) {
            return 0;
        }
        return com.chicoestudioso.caricraft.server.tips.TipManager.send(mod, ctx.getSource().getPlayerOrException(), true) ? 1 : 0;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        mod.reload();
        ChallengeManager.onServerStarted(mod);
        int missions = mod.core().missions().catalog().size();
        ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.reloaded", missions), true);
        mod.server().getPlayerList().getPlayers().forEach(mod::sendSnapshot);
        return 1;
    }

    private static int challengeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CaricraftCore core = mod.core();
        core.onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        Party party = core.partyOf(player.getUUID());
        long day = ChallengeManager.day(mod.server());
        var state = party.challenge();
        Component status;
        if (!core.config().challenge.enabled) {
            status = Component.translatable("caricraft.command.challenge.disabled");
        } else if (state.active()) {
            status = Component.translatable("caricraft.command.challenge.active", state.nightKills());
        } else {
            status = Component.translatable("caricraft.command.challenge.next", core.challenge().nightsUntilForced(party, day));
        }
        ctx.getSource().sendSuccess(() -> status.copy().withStyle(ChatFormatting.DARK_RED), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.challenge.level",
                core.challenge().level(party), state.nightsCompleted(), state.totalKills()), false);
        if (!ChallengeManager.blockedByModpack().isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("caricraft.command.challenge.blocked",
                    String.join(", ", ChallengeManager.blockedByModpack())));
        }
        return 1;
    }

    private static int challengePrepare(CommandContext<CommandSourceStack> ctx) {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        try {
            int removed = ChallengeManager.prepareMobsBeGone(mod.core().config().challenge);
            if (removed < 0) {
                ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.challenge.no_mobsbegone"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.challenge.prepared", removed)
                        .withStyle(ChatFormatting.GREEN), true);
            }
            return Math.max(0, removed);
        } catch (java.io.IOException | RuntimeException e) {
            ctx.getSource().sendFailure(Component.translatable("caricraft.command.challenge.prepare_failed", e.getMessage()));
            return 0;
        }
    }

    private static int challengeStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CaricraftCore core = mod.core();
        core.onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        ActionResult result = core.challenge().bringForward(core.partyOf(player.getUUID()),
                ChallengeManager.day(mod.server()), ChallengeManager.night(mod.server()));
        Component message = Component.translatable(result.key(), result.args().toArray());
        if (result.success()) {
            ctx.getSource().sendSuccess(() -> message, true);
        } else {
            ctx.getSource().sendFailure(message);
        }
        mod.markDirty();
        mod.sendPartySnapshots(player.getUUID());
        return result.success() ? 1 : 0;
    }

    private static int travel(CommandContext<CommandSourceStack> ctx, String what) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        ActionResult result = switch (what) {
            case "traer" -> com.chicoestudioso.caricraft.server.camp.TravelManager.request(mod, player,
                    com.chicoestudioso.caricraft.core.camp.TravelRequest.Kind.BRING);
            case "aceptar" -> com.chicoestudioso.caricraft.server.camp.TravelManager.accept(mod, player);
            case "rechazar" -> com.chicoestudioso.caricraft.server.camp.TravelManager.decline(mod, player);
            case "campamento" -> com.chicoestudioso.caricraft.server.camp.TravelManager.travelToCamp(mod, player);
            default -> com.chicoestudioso.caricraft.server.camp.TravelManager.request(mod, player,
                    com.chicoestudioso.caricraft.core.camp.TravelRequest.Kind.GO);
        };
        Component message = Component.translatable(result.key(), result.args().toArray());
        if (result.success()) {
            ctx.getSource().sendSuccess(() -> message, false);
        } else {
            ctx.getSource().sendFailure(message);
        }
        mod.markDirty();
        mod.sendPartySnapshots(player.getUUID());
        return result.success() ? 1 : 0;
    }

    private static int challengeLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CaricraftCore core = mod.core();
        core.onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        Party party = core.partyOf(player.getUUID());
        int wanted = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "nivel");
        party.challenge().restoreXp(core.challenge().xpForLevel(wanted), false);
        int level = core.challenge().level(party);
        ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.challenge.level_set", level), true);
        mod.markDirty();
        mod.sendPartySnapshots(player.getUUID());
        return level;
    }

    /** Hace aparecer ya un cofre del desafío cerca del jugador (para probar o para un evento). */
    private static int challengeChest(CommandContext<CommandSourceStack> ctx,
                                      com.chicoestudioso.caricraft.server.challenge.ChallengeChests.Kind kind) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        mod.core().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        boolean spawned = com.chicoestudioso.caricraft.server.challenge.ChallengeChests.spawnNear(mod, player, kind);
        ctx.getSource().sendSuccess(() -> Component.translatable(spawned ? "caricraft.command.challenge.chest_spawned"
                : "caricraft.command.challenge.chest_failed"), true);
        return spawned ? 1 : 0;
    }

    private static int challengeStop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Party party = mod.core().partyOf(player.getUUID());
        if (party == null || mod.core().challenge().forceEnd(party) == null) {
            ctx.getSource().sendFailure(Component.translatable("caricraft.command.challenge.not_active"));
            return 0;
        }
        com.chicoestudioso.caricraft.server.challenge.ChallengeSquads.endNight(mod, party);
        for (java.util.UUID member : party.members()) {
            ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
            if (online != null) {
                ChallengeManager.despawnAround(online, mod.core().config().challenge.radius);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("caricraft.command.challenge.stopped"), true);
        mod.markDirty();
        mod.sendPartySnapshots(player.getUUID());
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        CaricraftCore core = mod.core();
        core.onPlayerJoin(target.getUUID(), target.getGameProfile().getName());
        Party party = core.partyOf(target.getUUID());
        String members = party.members().stream().map(core.state()::nameOf).collect(Collectors.joining(", "));
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("caricraft.command.info.header", target.getDisplayName())
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.translatable("caricraft.command.info.party", members, party.wallet().balance()), false);
        source.sendSuccess(() -> Component.translatable("caricraft.command.info.apps",
                String.join(", ", party.ownedApps()), party.claimedEver().size(), party.discoveredBiomes().size()), false);
        party.chronicle().stream().limit(5).forEach(entry -> source.sendSuccess(() -> Component.literal(" • ")
                .append(Component.translatable("caricraft.chronicle." + entry.type(), chronicleArgs(entry))), false));
        return 1;
    }

    private static Object[] chronicleArgs(ChronicleEntry entry) {
        Object[] args = new Object[entry.args().size() + 1];
        args[0] = entry.actorName();
        for (int i = 0; i < entry.args().size(); i++) {
            args[i + 1] = entry.args().get(i);
        }
        return args;
    }

    private static int duo(CommandContext<CommandSourceStack> ctx, DuoAction action) throws CommandSyntaxException {
        CaricraftServer mod = mod(ctx);
        if (mod == null) {
            return 0;
        }
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        CaricraftCore core = mod.core();
        core.onPlayerJoin(self.getUUID(), self.getGameProfile().getName());
        ServerPlayer other = action == DuoAction.LEAVE ? null : EntityArgument.getPlayer(ctx, "player");
        if (other != null) {
            core.onPlayerJoin(other.getUUID(), other.getGameProfile().getName());
        }
        UUID uuid = self.getUUID();
        Party before = core.partyOf(uuid);
        java.util.List<UUID> previousMembers = java.util.List.copyOf(before.members());
        ActionResult result = switch (action) {
            case INVITE -> core.parties().invite(core.state(), uuid, other.getUUID());
            case ACCEPT -> {
                UUID sourceParty = core.partyOf(uuid).id();
                ActionResult accepted = core.parties().accept(core.state(), uuid, other.getUUID());
                if (accepted.success()) {
                    com.chicoestudioso.caricraft.server.box.CariBoxManager.onMerge(mod, sourceParty, core.partyOf(uuid), self);
                }
                yield accepted;
            }
            case DECLINE -> core.parties().decline(core.state(), uuid, other.getUUID());
            case LEAVE -> core.parties().leavePending(core.state(), uuid)
                    ? core.parties().confirmLeave(core.state(), uuid)
                    : core.parties().requestLeave(core.state(), uuid);
        };
        Component message = Component.translatable(result.key(), result.args().toArray());
        if (result.success()) {
            ctx.getSource().sendSuccess(() -> message, false);
        } else {
            ctx.getSource().sendFailure(message);
        }
        if (result.success() && action == DuoAction.INVITE) {
            mod.notify(other, true, "caricraft.notify.invite_received", java.util.List.of(self.getGameProfile().getName()));
        }
        mod.markDirty();
        mod.sendPartySnapshots(uuid);
        if (other != null) {
            mod.sendPartySnapshots(other.getUUID());
        }
        for (UUID member : previousMembers) {
            ServerPlayer online = mod.server().getPlayerList().getPlayer(member);
            if (online != null) {
                mod.sendSnapshot(online);
            }
        }
        return result.success() ? 1 : 0;
    }
}
