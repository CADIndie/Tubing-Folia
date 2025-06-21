package be.garagepoort.mcioc.tubingbukkit.common;

import be.garagepoort.mcioc.IocBean;
import be.garagepoort.mcioc.tubingbukkit.TubingBukkitPlugin;
import org.bukkit.Bukkit;

@IocBean
public class TubingBukkitUtil implements ITubingBukkitUtil {

    @Override
    public void runAsync(Runnable runnable) {
        TubingBukkitPlugin.getScheduler().runTaskAsynchronously(runnable);
    }

    @Override
    public void runTaskLater(Runnable runnable, int ticks) {
        TubingBukkitPlugin.getScheduler().runTaskLater(runnable, ticks);
    }
}
