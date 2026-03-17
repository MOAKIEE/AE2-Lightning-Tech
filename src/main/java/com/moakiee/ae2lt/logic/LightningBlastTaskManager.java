package com.moakiee.ae2lt.logic;

import com.moakiee.ae2lt.AE2LightningTech;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class LightningBlastTaskManager {
    private static final int GLOBAL_BLOCK_BUDGET_PER_TICK = 2400;
    private static final int GLOBAL_LIGHTNING_BUDGET_PER_TICK = 8;
    private static final List<LightningBlastTask> ACTIVE_TASKS = new ArrayList<>();

    private LightningBlastTaskManager() {
    }

    public static void schedule(LightningBlastTask task) {
        if (task.isCompleted()) {
            return;
        }
        ACTIVE_TASKS.add(task);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_TASKS.isEmpty()) {
            return;
        }

        int remainingBlockBudget = GLOBAL_BLOCK_BUDGET_PER_TICK;
        int remainingLightningBudget = GLOBAL_LIGHTNING_BUDGET_PER_TICK;
        Iterator<LightningBlastTask> iterator = ACTIVE_TASKS.iterator();
        int remainingTasks = ACTIVE_TASKS.size();
        while (iterator.hasNext()) {
            LightningBlastTask task = iterator.next();
            int blockShare = remainingBlockBudget <= 0
                    ? 0
                    : (remainingTasks > 0 ? Math.max(1, remainingBlockBudget / remainingTasks) : remainingBlockBudget);
            int lightningShare = remainingTasks > 0
                    ? Math.max(0, remainingLightningBudget / remainingTasks)
                    : remainingLightningBudget;

            int consumedLightning = task.tick(blockShare, lightningShare);
            remainingBlockBudget = Math.max(0, remainingBlockBudget - blockShare);
            remainingLightningBudget = Math.max(0, remainingLightningBudget - consumedLightning);
            remainingTasks--;

            if (task.isCompleted()) {
                iterator.remove();
            }
        }
    }
}
