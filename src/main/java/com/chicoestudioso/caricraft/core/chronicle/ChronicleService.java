package com.chicoestudioso.caricraft.core.chronicle;

import com.chicoestudioso.caricraft.config.CaricraftConfig;
import com.chicoestudioso.caricraft.core.Clock;
import com.chicoestudioso.caricraft.core.party.Party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Gestiona el diario compartido de cada grupo. */
public final class ChronicleService {
    public static final int MAX_NOTE_LENGTH = 120;

    private final CaricraftConfig config;
    private final Clock clock;

    public ChronicleService(CaricraftConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    public void add(Party party, String type, UUID actor, String actorName, String... args) {
        add(party, new ChronicleEntry(clock.now(), type, actor, actorName, List.of(args)));
    }

    public void add(Party party, ChronicleEntry entry) {
        party.chronicle().addFirst(entry);
        trim(party);
    }

    /** Fusiona dos diarios por fecha (más reciente primero) respetando el máximo. */
    public void mergeInto(Party target, Iterable<ChronicleEntry> entries) {
        List<ChronicleEntry> all = new ArrayList<>(target.chronicle());
        entries.forEach(all::add);
        all.sort(Comparator.comparingLong(ChronicleEntry::time).reversed());
        target.chronicle().clear();
        target.chronicle().addAll(all);
        trim(target);
    }

    private void trim(Party party) {
        while (party.chronicle().size() > config.chronicleMaxEntries) {
            party.chronicle().removeLast();
        }
    }

    /**
     * Limpia una nota escrita por un jugador: quita códigos de formato y caracteres de control,
     * colapsa espacios y recorta a {@value #MAX_NOTE_LENGTH} caracteres.
     */
    public static String sanitizeNote(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean lastSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++; // salta el código de formato
                continue;
            }
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) {
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!lastSpace && sb.length() > 0) {
                    sb.append(' ');
                }
                lastSpace = true;
                continue;
            }
            sb.append(c);
            lastSpace = false;
        }
        String result = sb.toString().strip();
        return result.length() > MAX_NOTE_LENGTH ? result.substring(0, MAX_NOTE_LENGTH).strip() : result;
    }
}
