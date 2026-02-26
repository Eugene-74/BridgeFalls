package fr.eureur7.bridgefalls;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.gmail.goosius.siegewar.objects.BattleSession;

public class SiegeWarHelper {
    public static boolean isSiegeActiveGlobal() {
        BattleSession battleSession = BattleSession.getBattleSession();
        boolean active = battleSession != null && battleSession.isActive();
        BridgeFallsPlugin
                .log("Checked SiegeWar battle session: " + (battleSession != null ? battleSession.toString() : "null")
                        + ", active: " + active);
        return active;
    }

}