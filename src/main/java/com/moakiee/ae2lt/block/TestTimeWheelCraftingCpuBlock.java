package com.moakiee.ae2lt.block;

import appeng.block.AEBaseEntityBlock;

import com.moakiee.ae2lt.blockentity.TestTimeWheelCraftingCpuBlockEntity;

public class TestTimeWheelCraftingCpuBlock extends AEBaseEntityBlock<TestTimeWheelCraftingCpuBlockEntity> {
    public TestTimeWheelCraftingCpuBlock() {
        super(metalProps().forceSolidOn());
    }
}
