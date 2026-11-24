package com.chicoestudioso.caricraft.client.phone;

import com.chicoestudioso.caricraft.network.PhoneSnapshot;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Traducción de ids del snapshot a textos legibles. Solo presentación. */
public final class PhoneText {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private PhoneText() {
    }

    public static Component appName(String id) {
        return Component.translatable("caricraft.app." + id);
    }

    public static Component appDescription(String id) {
        return Component.translatable("caricraft.app." + id + ".desc");
    }

    public static Component missionName(PhoneSnapshot.MissionView mission) {
        String key = "caricraft.mission." + mission.id();
        if (I18n.exists(key) || mission.title() == null || mission.title().isBlank()) {
            return Component.translatable(key);
        }
        return Component.literal(mission.title());
    }

    public static Component missionName(PhoneSnapshot snapshot, String id) {
        if (snapshot != null) {
            for (PhoneSnapshot.MissionView mission : snapshot.missions()) {
                if (mission.id().equals(id)) {
                    return missionName(mission);
                }
            }
        }
        return Component.translatable("caricraft.mission." + id);
    }

    public static Component missionDescription(PhoneSnapshot.MissionView mission) {
        String key = "caricraft.mission." + mission.id() + ".desc";
        if (I18n.exists(key)) {
            return Component.translatable(key);
        }
        return mission.description() == null ? Component.empty() : Component.literal(mission.description());
    }

    /** Nombre legible de un objetivo (id de ítem/bloque/entidad/bioma/especie, {@code #tag} o marca). */
    public static Component target(String target) {
        if (target == null || target.isEmpty()) {
            return Component.empty();
        }
        String flagKey = "caricraft.target." + target.replace(':', '.');
        if (I18n.exists(flagKey)) {
            return Component.translatable(flagKey);
        }
        if (target.startsWith("#")) {
            String tagKey = "caricraft.tag." + target.substring(1).replace(':', '.').replace('/', '.');
            return I18n.exists(tagKey) ? Component.translatable(tagKey) : Component.literal(target.substring(1));
        }
        if (target.startsWith("type:")) {
            String typeKey = "cobblemon.type." + target.substring(5);
            return I18n.exists(typeKey) ? Component.translatable(typeKey) : Component.literal(target.substring(5));
        }
        return idName(target);
    }

    /** Intenta traducir un id con namespace: bioma, especie de Cobblemon, ítem, bloque o entidad. */
    public static Component idName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return Component.literal(id);
        }
        String biomeKey = "biome." + rl.getNamespace() + "." + rl.getPath();
        if (I18n.exists(biomeKey)) {
            return Component.translatable(biomeKey);
        }
        String speciesKey = rl.getNamespace() + ".species." + rl.getPath() + ".name";
        if (I18n.exists(speciesKey)) {
            return Component.translatable(speciesKey);
        }
        if (BuiltInRegistries.ITEM.containsKey(rl)) {
            return BuiltInRegistries.ITEM.get(rl).getDescription();
        }
        if (BuiltInRegistries.BLOCK.containsKey(rl)) {
            return BuiltInRegistries.BLOCK.get(rl).getName();
        }
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            return BuiltInRegistries.ENTITY_TYPE.get(rl).getDescription();
        }
        return Component.literal(prettify(rl.getPath()));
    }

    private static String prettify(String path) {
        String spaced = path.replace('_', ' ').replace('/', ' ');
        return spaced.isEmpty() ? spaced : spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    /** Texto de un objetivo, p. ej. "Pica 16 × menas de hierro". */
    public static Component objective(PhoneSnapshot.ObjectiveView objective) {
        String type = objective.type().toLowerCase(Locale.ROOT);
        if (objective.target() == null || objective.target().isEmpty()) {
            return Component.translatable("caricraft.objective." + type, objective.required());
        }
        return Component.translatable("caricraft.objective." + type + ".target", objective.required(), target(objective.target()));
    }

    /** Resuelve un argumento de aviso o crónica: misión, app, id con namespace o texto literal. */
    public static Object arg(PhoneSnapshot snapshot, String raw) {
        if (snapshot != null) {
            for (PhoneSnapshot.MissionView mission : snapshot.missions()) {
                if (mission.id().equals(raw)) {
                    return missionName(mission);
                }
            }
            for (PhoneSnapshot.AppView app : snapshot.apps()) {
                if (app.id().equals(raw)) {
                    return appName(raw);
                }
            }
        }
        if (raw.indexOf(':') > 0 && ResourceLocation.tryParse(raw) != null) {
            return idName(raw);
        }
        return raw;
    }

    public static MutableComponent format(PhoneSnapshot snapshot, String key, List<String> args) {
        Object[] resolved = args.stream().map(a -> arg(snapshot, a)).toArray();
        return Component.translatable(key, resolved);
    }

    public static Component chronicle(PhoneSnapshot snapshot, PhoneSnapshot.ChronicleView entry) {
        Object[] args = new Object[entry.args().size() + 1];
        args[0] = entry.actor();
        for (int i = 0; i < entry.args().size(); i++) {
            args[i + 1] = entry.type().equals("note") ? entry.args().get(i) : arg(snapshot, entry.args().get(i));
        }
        return Component.translatable("caricraft.chronicle." + entry.type(), args);
    }

    public static String dateTime(long millis) {
        return DATE_TIME.format(Instant.ofEpochMilli(millis));
    }

    public static String clock(long millis) {
        return CLOCK.format(Instant.ofEpochMilli(millis));
    }

    /** "3 h 20 min", "12 min" o "2 d 4 h". */
    public static Component remaining(long millis) {
        long minutes = Math.max(1, millis / 60_000);
        if (minutes < 60) {
            return Component.translatable("caricraft.time.minutes", minutes);
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return Component.translatable("caricraft.time.hours", hours, minutes % 60);
        }
        return Component.translatable("caricraft.time.days", hours / 24, hours % 24);
    }

    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', '.');
    }
}
