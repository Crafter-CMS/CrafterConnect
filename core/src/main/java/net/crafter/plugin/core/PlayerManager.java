package net.crafter.plugin.core;

import net.crafter.plugin.core.model.PlayerData;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private final ConcurrentHashMap<String, PlayerData> players = new ConcurrentHashMap<>();

    public void updatePlayer(PlayerData data) {
        if (data != null && data.getUsername() != null) {
            players.put(data.getUsername().toLowerCase(), data);
        }
    }

    public PlayerData getPlayerData(String username) {
        if (username == null) return null;
        return players.get(username.toLowerCase());
    }

    public void removePlayer(String username) {
        if (username != null) {
            players.remove(username.toLowerCase());
        }
    }
}
