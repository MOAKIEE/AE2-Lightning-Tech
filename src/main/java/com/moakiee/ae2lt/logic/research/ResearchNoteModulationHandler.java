package com.moakiee.ae2lt.logic.research;

import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * 铁砧调制:空白研究笔记(左槽) + 催化物(右槽) -> 同数量空白笔记但 NBT 里写入
 * {@link ResearchNoteData#TAG_FORCED_GOAL}。仅消耗 1 个催化物(不论左槽叠数),
 * 不消耗经验。已生成笔记不接受调制。
 */
public final class ResearchNoteModulationHandler {

    public ResearchNoteModulationHandler() {
    }

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        if (!left.is(ModItems.RESEARCH_NOTE.get())) {
            return;
        }
        if (!ResearchNoteData.isBlank(left)) {
            return;
        }

        RitualGoal goal = NoteModulationCatalysts.findGoal(right).orElse(null);
        if (goal == null) {
            return;
        }

        RitualGoal existing = ResearchNoteData.readForcedGoal(left);
        if (existing == goal) {
            return;
        }

        ItemStack output = left.copy();
        ResearchNoteData.writeForcedGoal(output, goal);

        event.setOutput(output);
        event.setMaterialCost(1);
        event.setCost(0);
    }
}
