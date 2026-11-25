package com.chicoestudioso.caricraft.network;

/**
 * Acciones que el CariPhone puede solicitar al servidor. El servidor valida cada una.
 * El orden forma parte del protocolo: añadir siempre al final e incrementar
 * {@link com.chicoestudioso.caricraft.CaricraftConstants#PROTOCOL_VERSION} si se reordena.
 */
public enum PhoneAction {
    /** Pide un snapshot actualizado. */
    REQUEST_SNAPSHOT,
    /** arg1 = id del artículo. */
    BUY_ITEM,
    /** arg1 = id de la misión. */
    ACCEPT_MISSION,
    /** arg1 = id de la misión. */
    ABANDON_MISSION,
    /** arg1 = id de la misión. */
    CLAIM_MISSION,
    /** arg1 = id de la misión. Entrega los ítems que pide. */
    DELIVER_MISSION,
    /** arg1 = nombre del jugador. */
    INVITE_PLAYER,
    /** arg1 = UUID de quien invita. */
    ACCEPT_INVITE,
    /** arg1 = UUID de quien invita. */
    DECLINE_INVITE,
    /** arg1 = UUID del invitado. */
    CANCEL_INVITE,
    /** Primera pulsación de desvincular: pide confirmación. */
    LEAVE_PARTY,
    /** Segunda pulsación: confirma la desvinculación. */
    CONFIRM_LEAVE_PARTY,
    /** arg1 = id del tema. */
    SELECT_THEME,
    /** arg1 = texto de la nota. */
    ADD_NOTE,
    /** Adelanta el Desafío Dúo a la noche actual. */
    CHALLENGE_BRING_FORWARD,
    /** arg1 = id de la oferta del Mercado. */
    MARKET_BUY,
    /** Fija el campamento en la hoguera encendida más cercana. */
    CAMP_SET,
    /** arg1 = {@code camp} o {@code partner}. */
    CAMP_TRAVEL;

    private static final PhoneAction[] VALUES = values();

    /** Devuelve la acción o {@code null} si el índice no es válido. */
    public static PhoneAction byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }
}
