package com.chicoestudioso.caricraft.core.tips;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Consejos del CariPhone que se envían por el chat de vez en cuando. Se reparten en orden aleatorio sin
 * repetirse hasta haberlos dado todos, y solo los que vienen a cuento (p. ej. no se anuncia TP Dúo a quien
 * ya lo tiene). Java puro; el texto está en los archivos de idioma ({@code caricraft.tip.<id>}).
 */
public final class TipRotation {
    /** Lo que se sabe del jugador para elegir consejos. */
    public record TipContext(boolean duo, Set<String> ownedApps, boolean challengeEnabled, boolean cobblemon) {
        public boolean owns(String app) {
            return ownedApps.contains(app);
        }
    }

    public record Tip(String id, Predicate<TipContext> condition) {
        static Tip always(String id) {
            return new Tip(id, c -> true);
        }
    }

    public static final List<Tip> ALL = List.of(
            Tip.always("open_key"),
            Tip.always("missions_renew"),
            Tip.always("missions_claim"),
            new Tip("duo_invite", c -> !c.duo()),
            new Tip("duo_share", TipContext::duo),
            new Tip("tpduo_buy", c -> c.duo() && !c.owns("tpduo")),
            new Tip("tpduo_owned", c -> c.duo() && c.owns("tpduo")),
            new Tip("caricamp_buy", c -> !c.owns("caricamp")),
            new Tip("caricamp_owned", c -> c.owns("caricamp")),
            new Tip("carilens", c -> !c.owns("carilens")),
            new Tip("caribox", c -> !c.owns("caribox")),
            new Tip("explorer", c -> !c.owns("explorer")),
            new Tip("chronicles", c -> c.owns("chronicles")),
            Tip.always("market"),
            Tip.always("themes"),
            new Tip("challenge", TipContext::challengeEnabled),
            new Tip("challenge_forward", TipContext::challengeEnabled),
            Tip.always("navigation"),
            Tip.always("notices"),
            Tip.always("phone_personal"),
            new Tip("cobblemon", TipContext::cobblemon),
            Tip.always("tips_off"));

    private final List<Tip> tips;
    private final Random random;
    private final Deque<Tip> pending = new ArrayDeque<>();

    public TipRotation(Random random) {
        this(ALL, random);
    }

    public TipRotation(List<Tip> tips, Random random) {
        this.tips = List.copyOf(tips);
        this.random = random;
    }

    /** Siguiente consejo que viene a cuento, o vacío si ninguno aplica. */
    public Optional<String> next(TipContext context) {
        for (int pass = 0; pass < 2; pass++) {
            while (!pending.isEmpty()) {
                Tip tip = pending.poll();
                if (tip.condition().test(context)) {
                    return Optional.of(tip.id());
                }
            }
            List<Tip> shuffled = new ArrayList<>(tips);
            Collections.shuffle(shuffled, random);
            pending.addAll(shuffled);
        }
        return Optional.empty();
    }
}
