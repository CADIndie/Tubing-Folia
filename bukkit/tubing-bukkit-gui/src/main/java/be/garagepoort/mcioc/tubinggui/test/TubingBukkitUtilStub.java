package be.garagepoort.mcioc.tubinggui.test;

import be.garagepoort.mcioc.tubingbukkit.common.ITubingBukkitUtil;

public class TubingBukkitUtilStub implements ITubingBukkitUtil {
    @Override
    public void runAsync(Runnable runnable) {
        runnable.run();
    }

    @Override
    public void runTaskLater(Runnable runnable, int i) {
        runnable.run();
    }
}
