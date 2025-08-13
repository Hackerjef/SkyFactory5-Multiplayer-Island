package dev.nadie.skyfactoryisland.database;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import dev.nadie.skyfactoryisland.database.orm.PlayerEmergency;
import dev.nadie.skyfactoryisland.database.orm.PlayerIsland;

import java.io.File;
import java.sql.SQLException;

public class DatabaseManager {

    private static DatabaseManager instance;
    private ConnectionSource connectionSource;
    private Dao<PlayerIsland, String> playerIslandDao;
    private Dao<PlayerEmergency, String> playerEmergencyDao;

    private DatabaseManager() {
        // Private constructor to prevent direct instantiation
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public void initialize(File serverWorldDirectory) {
        if (connectionSource == null) {
            File databaseFile = new File(serverWorldDirectory, "islands.db");
            String databaseUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

            try {
                connectionSource = new JdbcConnectionSource(databaseUrl);
                TableUtils.createTableIfNotExists(connectionSource, PlayerIsland.class);
                TableUtils.createTableIfNotExists(connectionSource, PlayerEmergency.class);
                playerIslandDao = DaoManager.createDao(connectionSource, PlayerIsland.class);
                playerEmergencyDao = DaoManager.createDao(connectionSource, PlayerEmergency.class);
            } catch (SQLException e) {
                System.err.println("Error initializing database: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public ConnectionSource getConnectionSource() {
        return connectionSource;
    }

    public Dao<PlayerIsland, String> getPlayerIslandDao() {
        return playerIslandDao;
    }

    public Dao<PlayerEmergency, String> getPlayerEmergencyDao() {
        return playerEmergencyDao;
    }

    public void close() {
        if (connectionSource != null) {
            try {
                connectionSource.close();
            } catch (Exception e) {
                System.err.println("Error closing database connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}