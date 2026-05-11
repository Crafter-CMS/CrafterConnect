package net.crafter.plugin.core;

import net.crafter.plugin.core.model.StatisticsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsManager {
    private static final Logger log = LoggerFactory.getLogger(StatisticsManager.class);
    
    private StatisticsData currentStats = new StatisticsData();

    public synchronized void updateStats(StatisticsData newData) {
        this.currentStats = newData;
        if (currentStats != null) {
            log.info("[CrafterConnect] Statistics updated. Total Users: {}", currentStats.getTotalUsers());
        }
    }

    public synchronized StatisticsData getStats() {
        return currentStats;
    }
}
