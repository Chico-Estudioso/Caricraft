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
    CAMP_TRAVEL,
    /** arg1 = id de la foto que el cliente quiere ver. */
    PHOTO_REQUEST,
    /** arg1 = id de la foto que se borra del álbum. */
    PHOTO_DELETE,
    /** Petición de viaje al compañero: arg1 = {@code go} (ir hacia él) o {@code bring} (traerlo). */
    TRAVEL_REQUEST,
    /** Acepta la petición de viaje recibida. */
    TRAVEL_ACCEPT,
    /** Rechaza la petición recibida o cancela la enviada. */
    TRAVEL_DECLINE,
    /** Abre el CariBox (cofre compartido). */
    CARIBOX_OPEN,
    /** Activa o desactiva los consejos del CariPhone en el chat (personal). */
    TOGGLE_TIPS,
    /** Abre el regalo misterioso del día (Cobbleverse). */
    CV_GIFT,
    /** Compra en la tienda Cobbleverse: arg1 = id de la oferta. */
    CV_BUY,
    /** Vende un Pokémon del equipo por CobbleDollars: arg1 = UUID del Pokémon. */
    CV_SELL,
    /** Intercambio prodigioso: arg1 = UUID del Pokémon que se envía. */
    CV_WONDER,
    /** Abre una caja del CobbleCentro: arg1 = id de la caja. */
    CV_CRATE,
    /** Tirada de la tragaperras: arg1 = posición de la apuesta. */
    CV_SPIN,
    /** Mejora de un Pokémon: arg1 = id de la mejora, arg2 = UUID del Pokémon. */
    CV_UPGRADE,
    /** Fusión de objetos: arg1 = id de la fusión. */
    CV_FUSE,
    /** Centro Pokémon de bolsillo: curar el equipo. */
    CV_HEAL,
    /** Mandar de expedición: arg1 = "destino:horas", arg2 = UUID del Pokémon. */
    CV_EXP_START,
    /** Recoger al Pokémon que ha vuelto de la expedición (con su botín). */
    CV_EXP_CLAIM,
    /** Hacer volver antes al Pokémon (sin botín). */
    CV_EXP_CANCEL,
    /** Activa o desactiva la brújula del aviso de variocolor (personal). */
    TOGGLE_SHINY_COMPASS;

    private static final PhoneAction[] VALUES = values();

    /** Devuelve la acción o {@code null} si el índice no es válido. */
    public static PhoneAction byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : null;
    }
}
