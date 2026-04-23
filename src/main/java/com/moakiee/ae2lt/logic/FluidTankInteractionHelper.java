package com.moakiee.ae2lt.logic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 玩家 GUI 菜单上"流体槽 ↔ 手持容器"的通用交互:
 * <ul>
 *   <li>右键(insert): 把玩家"光标携带"物品内的流体倒入 tank;</li>
 *   <li>左键(extract): 从 tank 抽出流体填入光标携带的容器;</li>
 *   <li>shift+任意键(clear): 清空 tank。</li>
 * </ul>
 * 所有路径只操作 {@link Player#containerMenu} 的 carried 栈,失败返回 false,
 * 不改动 player 实物背包——与 AE2/Adv-AE 反应仓 GUI 语义对齐。
 *
 * <p>当 carried 是**多数量**的桶(如 16 个空桶)时,NeoForge {@code FluidUtil#tryEmptyContainer}
 * 只能处理首个单位,因此本类手动分桶:每次取 1,执行后把结果桶 stash 回 carried
 * (同类型直接叠加,否则放入玩家背包,再不行丢到地上)。</p>
 */
public final class FluidTankInteractionHelper {
    private FluidTankInteractionHelper() {
    }

    /** 右键:把光标容器里的流体倒入 tank。成功返回 true,说明有任何单位被转移。 */
    public static boolean insertFromCarried(Player player, FluidTank tank) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }

        ItemStack singleUnit = carried.copyWithCount(1);
        var simulated = FluidUtil.tryEmptyContainer(singleUnit, tank, Integer.MAX_VALUE, player, false);
        if (!simulated.isSuccess()) {
            return false;
        }

        var executed = FluidUtil.tryEmptyContainer(singleUnit, tank, Integer.MAX_VALUE, player, true);
        if (!executed.isSuccess()) {
            return false;
        }

        consumeOneFromCarried(player);
        stashIntoCarriedOrInventory(player, executed.getResult());
        return true;
    }

    /** 左键:从 tank 抽出流体到光标容器。成功返回 true。 */
    public static boolean extractToCarried(Player player, IFluidHandler tankHandler) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }

        ItemStack singleUnit = carried.copyWithCount(1);
        var simulated = FluidUtil.tryFillContainer(singleUnit, tankHandler, Integer.MAX_VALUE, player, false);
        if (!simulated.isSuccess()) {
            return false;
        }

        var executed = FluidUtil.tryFillContainer(singleUnit, tankHandler, Integer.MAX_VALUE, player, true);
        if (!executed.isSuccess()) {
            return false;
        }

        consumeOneFromCarried(player);
        stashIntoCarriedOrInventory(player, executed.getResult());
        return true;
    }

    /** shift+click:直接清空 tank;调用方负责后续 setChanged / notify。 */
    public static void clear(FluidTank tank) {
        tank.setFluid(FluidStack.EMPTY);
    }

    private static void consumeOneFromCarried(Player player) {
        ItemStack carried = player.containerMenu.getCarried();
        carried.shrink(1);
        player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
    }

    /**
     * 优先把 result 叠回光标(同 item + 同组件才行);否则塞玩家背包,
     * 塞不下就丢到玩家脚下。避免出现"桶卡在 carried 和 inventory 之间消失"的情况。
     */
    private static void stashIntoCarriedOrInventory(Player player, ItemStack result) {
        if (result.isEmpty()) {
            return;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(result);
            return;
        }

        if (ItemStack.isSameItemSameComponents(carried, result)
                && carried.getCount() + result.getCount() <= carried.getMaxStackSize()) {
            carried.grow(result.getCount());
            player.containerMenu.setCarried(carried);
            return;
        }

        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }
}
