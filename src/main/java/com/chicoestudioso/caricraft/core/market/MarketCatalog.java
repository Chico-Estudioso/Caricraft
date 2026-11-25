package com.chicoestudioso.caricraft.core.market;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Conjunto validado de ofertas posibles del Mercado. */
public final class MarketCatalog {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Gson GSON = new Gson();

    private final List<MarketOffer> pool;

    private record FileFormat(List<MarketOffer> offers) {
    }

    private MarketCatalog(List<MarketOffer> pool) {
        this.pool = List.copyOf(pool);
    }

    /**
     * Lee {@code {"offers": [...]}}.
     *
     * @param sourceEnabled qué orígenes se aceptan ({@code vanilla}, {@code cobblemon})
     * @param itemExists    descarta ofertas de ítems que no existen en este servidor
     */
    public static MarketCatalog fromJson(Reader reader, Predicate<String> sourceEnabled, Predicate<String> itemExists) {
        FileFormat file;
        try {
            file = GSON.fromJson(reader, FileFormat.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("JSON del mercado no válido: " + e.getMessage(), e);
        }
        if (file == null || file.offers() == null) {
            throw new IllegalArgumentException("El JSON del mercado debe tener una lista \"offers\"");
        }
        return of(file.offers(), sourceEnabled, itemExists);
    }

    public static MarketCatalog of(List<MarketOffer> offers, Predicate<String> sourceEnabled, Predicate<String> itemExists) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<MarketOffer> pool = new ArrayList<>();
        for (MarketOffer offer : offers) {
            if (offer == null || offer.id() == null || !ID.matcher(offer.id()).matches()) {
                errors.add("id no válido: " + (offer == null ? null : offer.id()));
                continue;
            }
            if (!ids.add(offer.id())) {
                errors.add("id duplicado: " + offer.id());
            }
            if (offer.item() == null || offer.item().isBlank()) {
                errors.add(offer.id() + ": falta item");
            }
            if (offer.price() <= 0) {
                errors.add(offer.id() + ": precio no válido");
            }
            if (sourceEnabled.test(offer.source()) && offer.item() != null && itemExists.test(offer.item())) {
                pool.add(offer);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Mercado no válido: " + String.join("; ", errors));
        }
        return new MarketCatalog(pool);
    }

    public List<MarketOffer> pool() {
        return Collections.unmodifiableList(pool);
    }
}
