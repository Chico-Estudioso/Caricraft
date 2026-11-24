package com.chicoestudioso.caricraft.core.mission;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Conjunto validado de definiciones de misión. */
public final class MissionCatalog {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Gson GSON = new Gson();

    private final Map<String, MissionDefinition> missions = new LinkedHashMap<>();

    private record FileFormat(List<MissionDefinition> missions) {
    }

    /**
     * Lee un JSON {@code {"missions": [...]}} y descarta las misiones cuyo {@code requiresMod} no esté cargado.
     *
     * @throws IllegalArgumentException si el JSON o alguna misión no es válida
     */
    public static MissionCatalog fromJson(Reader reader, Predicate<String> modLoaded) {
        FileFormat file;
        try {
            file = GSON.fromJson(reader, FileFormat.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("JSON de misiones no válido: " + e.getMessage(), e);
        }
        if (file == null || file.missions() == null) {
            throw new IllegalArgumentException("El JSON de misiones debe tener una lista \"missions\"");
        }
        return of(file.missions(), modLoaded);
    }

    public static MissionCatalog of(Collection<MissionDefinition> definitions, Predicate<String> modLoaded) {
        MissionCatalog catalog = new MissionCatalog();
        List<String> errors = new ArrayList<>();
        for (MissionDefinition def : definitions) {
            validate(def, errors);
            if (def != null && def.id() != null && catalog.missions.containsKey(def.id())) {
                errors.add("id duplicado: " + def.id());
            }
            if (def != null && def.id() != null) {
                catalog.missions.put(def.id(), def);
            }
        }
        Set<String> ids = new HashSet<>(catalog.missions.keySet());
        for (MissionDefinition def : catalog.missions.values()) {
            for (String req : def.requires()) {
                if (!ids.contains(req)) {
                    errors.add(def.id() + ": requiere una misión inexistente " + req);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Misiones no válidas: " + String.join("; ", errors));
        }
        catalog.missions.values().removeIf(def -> def.requiresMod() != null && !def.requiresMod().isBlank()
                && !modLoaded.test(def.requiresMod()));
        // Quitar en cascada las que dependen de misiones descartadas.
        boolean changed = true;
        while (changed) {
            changed = catalog.missions.values().removeIf(def -> !catalog.missions.keySet().containsAll(def.requires()));
        }
        return catalog;
    }

    private static void validate(MissionDefinition def, List<String> errors) {
        if (def == null) {
            errors.add("entrada nula");
            return;
        }
        String id = def.id();
        if (id == null || !ID.matcher(id).matches()) {
            errors.add("id no válido: " + id);
            return;
        }
        if (def.scope() == null) {
            errors.add(id + ": falta scope");
        }
        if (def.category() == null) {
            errors.add(id + ": falta category");
        }
        if (def.reward() < 0) {
            errors.add(id + ": recompensa negativa");
        }
        if (def.objectives().isEmpty()) {
            errors.add(id + ": sin objetivos");
        }
        for (Objective objective : def.objectives()) {
            if (objective == null || objective.type() == null) {
                errors.add(id + ": objetivo sin tipo");
            } else if (objective.amount() <= 0) {
                errors.add(id + ": cantidad no válida en " + objective.type());
            }
        }
        if (def.requireAllMembers() && def.scope() == MissionScope.INDIVIDUAL) {
            errors.add(id + ": requireAllMembers no vale en misiones INDIVIDUAL");
        }
    }

    public Optional<MissionDefinition> get(String id) {
        return Optional.ofNullable(missions.get(id));
    }

    public Collection<MissionDefinition> all() {
        return Collections.unmodifiableCollection(missions.values());
    }

    public int size() {
        return missions.size();
    }
}
