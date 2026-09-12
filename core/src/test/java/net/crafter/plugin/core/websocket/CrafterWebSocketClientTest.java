package net.crafter.plugin.core.websocket;

import net.crafter.plugin.core.CommandExecutor;
import net.crafter.plugin.core.LanguageManager;
import net.crafter.plugin.core.MarketManager;
import net.crafter.plugin.core.PlayerManager;
import net.crafter.plugin.core.StatisticsManager;
import net.crafter.plugin.core.config.CrafterConfig;
import net.crafter.plugin.core.model.PendingCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CrafterWebSocketClientTest {

    static class DummyExecutor implements CommandExecutor {
        @Override public boolean execute(PendingCommand command) { return true; }
        @Override public void execute(String command) {}
        @Override public void sendMessage(String username, String message) {}
        @Override public void handlePurchaseResponse(String username, boolean success, String message) {}
        @Override public boolean isPlayerOnline(String playerName) { return false; }
        @Override public String getPlatformName() { return "test"; }
    }

    @Test
    void testStartDoesNotConnectWhenConfigIsDefault() {
        CrafterConfig config = new CrafterConfig(); // Default unconfigured values
        CommandExecutor executor = new DummyExecutor();
        StatisticsManager stats = new StatisticsManager();
        PlayerManager players = new PlayerManager();
        MarketManager market = new MarketManager();
        LanguageManager lang = new LanguageManager("tr");

        CrafterWebSocketClient client = new CrafterWebSocketClient(config, executor, stats, players, market, lang);

        assertDoesNotThrow(client::start);
        assertFalse(client.isOpen(), "Client should NOT be open or connecting with default config");
        assertFalse(client.isConnected(), "Client should not be connected");

        client.shutdown();
    }
}
