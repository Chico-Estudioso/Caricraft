package com.chicoestudioso.caricraft.core.mission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Estado mutable de una misión aceptada por un grupo o jugador. */
public final class MissionState {
    private final String missionId;
    private final UUID owner;
    private MissionStatus status;
    private final long[] progress;
    private final Map<UUID, Long> contributions = new LinkedHashMap<>();
    private final long cycle;
    private final long acceptedAt;
    private long completedAt;
    private long claimedAt;

    /**
     * @param owner jugador dueño en misiones individuales; {@code null} en cooperativas
     */
    public MissionState(String missionId, UUID owner, MissionStatus status, long[] progress, long cycle, long acceptedAt) {
        this.missionId = missionId;
        this.owner = owner;
        this.status = status;
        this.progress = progress;
        this.cycle = cycle;
        this.acceptedAt = acceptedAt;
    }

    public String missionId() {
        return missionId;
    }

    public UUID owner() {
        return owner;
    }

    public MissionStatus status() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public long[] progress() {
        return progress;
    }

    public Map<UUID, Long> contributions() {
        return contributions;
    }

    public long cycle() {
        return cycle;
    }

    public long acceptedAt() {
        return acceptedAt;
    }

    public long completedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public long claimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(long claimedAt) {
        this.claimedAt = claimedAt;
    }
}
