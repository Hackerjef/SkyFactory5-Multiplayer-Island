package dev.nadie.skyfactoryisland.database.orm;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.UUID;

@DatabaseTable(tableName = "player_emergency")
public class PlayerEmergency {

    @DatabaseField(unique = true, canBeNull = false, id = true)
    private UUID playerUUID;

    @DatabaseField(canBeNull = false)
    private Long time;

    public PlayerEmergency() {
        // ORMLite needs a no-arg constructor
    }

    public PlayerEmergency(UUID playerUUID, Long time) {
        this.playerUUID = playerUUID;
        this.time = time;
    }


    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Long getTime() {
        return time;
    }
}