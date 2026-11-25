package com.chicoestudioso.caricraft.client.phone;

import net.minecraft.network.chat.Component;

/** Páginas del CariPhone. */
public enum PhonePage {
    HOME(null),
    MISSIONS("missions"),
    WALLET("wallet"),
    DUO("duo"),
    SHOP("shop"),
    SETTINGS("settings"),
    CHRONICLES("chronicles"),
    EXPLORER("explorer"),
    STATS("stats"),
    CHALLENGE("challenge"),
    MARKET("market"),
    CARICAMP("caricamp"),
    CARILENS("carilens"),
    /** App que aún no se tiene o que llegará más adelante. */
    LOCKED(null);

    private final String appId;

    PhonePage(String appId) {
        this.appId = appId;
    }

    public String appId() {
        return appId;
    }

    public Component title() {
        return appId == null ? Component.translatable("screen.caricraft.phone") : PhoneText.appName(appId);
    }

    public PhonePage parent() {
        return HOME;
    }

    /** Página de una app del catálogo, o {@code null} si no tiene pantalla. */
    public static PhonePage forApp(String appId) {
        for (PhonePage page : values()) {
            if (appId.equals(page.appId)) {
                return page;
            }
        }
        return null;
    }
}
