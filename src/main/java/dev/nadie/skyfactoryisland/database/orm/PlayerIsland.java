package dev.nadie.skyfactoryisland.database.orm;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import dev.nadie.skyfactoryisland.data.Coords;

import java.util.UUID;

@DatabaseTable(tableName = "player_islands")
public class PlayerIsland {

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(canBeNull = false)
    private UUID playerUUID;

    @DatabaseField(canBeNull = false)
    private double x;

    @DatabaseField(canBeNull = false)
    private double y;

    @DatabaseField(canBeNull = false)
    private double z;

    @DatabaseField(dataType = DataType.BOOLEAN, defaultValue = "true")
    private boolean isActive;

    public PlayerIsland() {
        // ORMLite needs a no-arg constructor
    }

    public PlayerIsland(UUID playerUUID, double x, double y, double z, boolean isActive) {
        this.playerUUID = playerUUID;
        this.x = x;
        this.y = y;
        this.z = z;
        this.isActive = isActive;
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public boolean isActive() {
        return isActive;
    }

    public Coords getCoords() {
        return new Coords((int) x, (int) y, (int) z);
    }

    public void delete() {
        isActive = false;
    }

    @Override
    public String toString() {
        return "PlayerIsland{" +
                "id=" + id +
                ", playerUUID=" + playerUUID +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", isActive=" + isActive +
                '}';
    }
}
