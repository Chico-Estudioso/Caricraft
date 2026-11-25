package com.chicoestudioso.caricraft.core.market;

/**
 * Oferta posible del Mercado.
 *
 * @param item       id del ítem que se entrega
 * @param dailyStock compras permitidas por grupo y día
 * @param weight     peso para salir en el sorteo diario (más alto = más frecuente)
 * @param source     {@code vanilla} o {@code cobblemon}
 */
public record MarketOffer(String id, String item, int count, long price, int dailyStock, int weight, String source) {
    public MarketOffer {
        source = source == null || source.isBlank() ? "vanilla" : source;
        count = Math.max(1, count);
        dailyStock = Math.max(1, dailyStock);
        weight = Math.max(1, weight);
    }
}
