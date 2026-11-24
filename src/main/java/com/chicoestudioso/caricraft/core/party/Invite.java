package com.chicoestudioso.caricraft.core.party;

import java.util.UUID;

/** Invitación pendiente para formar un dúo. No se persiste: caduca en minutos. */
public record Invite(UUID from, UUID to, long expiresAt) {
    public boolean expired(long now) {
        return now >= expiresAt;
    }
}
