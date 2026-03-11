package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class HighVoltageAggregatorBlockEntity extends BlockEntity {
    public static final long ENERGY_CAPACITY = 1_024_000_000L;
    public static final long MAX_OUTPUT_PER_TICK = Long.MAX_VALUE;
    public static final long HIGH_VOLTAGE_GENERATION_PER_TICK = 1_000_000L;
    public static final int HIGH_VOLTAGE_DURATION_TICKS = 20 * 20;
    public static final long EXTREME_HIGH_VOLTAGE_GENERATION_PER_TICK = 50_000_000L;
    public static final int EXTREME_HIGH_VOLTAGE_DURATION_TICKS = 20 * 20;

    private long storedEnergy;
    private int highVoltageTicks;
    private int extremeHighVoltageTicks;
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IEnergyStorage, @Nullable Direction>[] energyOutputCaches =
            new BlockCapabilityCache[Direction.values().length];
    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            long extracted = Math.min(storedEnergy, Integer.toUnsignedLong(maxExtract));
            if (extracted > 0 && !simulate) {
                storedEnergy -= extracted;
                setChanged();
            }
            return (int) extracted;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, storedEnergy);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, ENERGY_CAPACITY);
        }

        @Override
        public boolean canExtract() {
            return storedEnergy > 0;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public HighVoltageAggregatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.HIGH_VOLTAGE_AGGREGATOR.get(), pos, blockState);
    }

    public IEnergyStorage getEnergyStorage() {
        return this.energyStorage;
    }

    public long addEnergy(long amount) {
        long accepted = Math.min(Math.max(0L, amount), ENERGY_CAPACITY - this.storedEnergy);
        if (accepted > 0) {
            this.storedEnergy += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Energy", this.storedEnergy);
        tag.putInt("HighVoltageTicks", this.highVoltageTicks);
        tag.putInt("ExtremeHighVoltageTicks", this.extremeHighVoltageTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.storedEnergy = Math.min(tag.getLong("Energy"), ENERGY_CAPACITY);
        this.highVoltageTicks = Math.max(0, tag.getInt("HighVoltageTicks"));
        this.extremeHighVoltageTicks = Math.max(0, tag.getInt("ExtremeHighVoltageTicks"));
    }

    public void activateHighVoltageMode() {
        if (this.extremeHighVoltageTicks > 0) {
            return;
        }
        this.highVoltageTicks = HIGH_VOLTAGE_DURATION_TICKS;
        setChanged();
    }

    public void activateExtremeHighVoltageMode() {
        if (this.highVoltageTicks > 0) {
            this.highVoltageTicks = 0;
            this.extremeHighVoltageTicks = EXTREME_HIGH_VOLTAGE_DURATION_TICKS;
        } else if (this.extremeHighVoltageTicks > 0) {
            this.extremeHighVoltageTicks = Math.min(
                    Integer.MAX_VALUE - EXTREME_HIGH_VOLTAGE_DURATION_TICKS,
                    this.extremeHighVoltageTicks) + EXTREME_HIGH_VOLTAGE_DURATION_TICKS;
        } else {
            this.extremeHighVoltageTicks = EXTREME_HIGH_VOLTAGE_DURATION_TICKS;
        }
        setChanged();
    }

    public boolean isHighVoltageMode() {
        return this.highVoltageTicks > 0;
    }

    public boolean isExtremeHighVoltageMode() {
        return this.extremeHighVoltageTicks > 0;
    }

    public int getHighVoltageTicks() {
        return this.highVoltageTicks;
    }

    public int getExtremeHighVoltageTicks() {
        return this.extremeHighVoltageTicks;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HighVoltageAggregatorBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean changed = false;
        if (blockEntity.extremeHighVoltageTicks > 0) {
            blockEntity.extremeHighVoltageTicks--;
            blockEntity.addEnergy(EXTREME_HIGH_VOLTAGE_GENERATION_PER_TICK);
            changed = true;
        } else if (blockEntity.highVoltageTicks > 0) {
            blockEntity.highVoltageTicks--;
            blockEntity.addEnergy(HIGH_VOLTAGE_GENERATION_PER_TICK);
            changed = true;
        }

        if (blockEntity.storedEnergy <= 0) {
            if (changed) {
                blockEntity.setChanged();
            }
            return;
        }

        long remainingOutputBudget = Math.min(blockEntity.storedEnergy, MAX_OUTPUT_PER_TICK);
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) {
                continue;
            }
            if (remainingOutputBudget <= 0 || blockEntity.storedEnergy <= 0) {
                break;
            }

            var target = blockEntity.getCachedEnergyTarget(serverLevel, direction);
            if (target == null || !target.canReceive()) {
                continue;
            }

            int offered = (int) Math.min(Integer.MAX_VALUE, Math.min(blockEntity.storedEnergy, remainingOutputBudget));
            if (offered <= 0) {
                continue;
            }

            int accepted = target.receiveEnergy(offered, false);
            if (accepted > 0) {
                blockEntity.storedEnergy -= accepted;
                remainingOutputBudget -= accepted;
                changed = true;
            }
        }

        if (changed) {
            blockEntity.setChanged();
        }
    }

    private @Nullable IEnergyStorage getCachedEnergyTarget(ServerLevel level, Direction direction) {
        int index = direction.ordinal();
        if (this.energyOutputCaches[index] == null) {
            this.energyOutputCaches[index] = BlockCapabilityCache.create(
                    Capabilities.EnergyStorage.BLOCK,
                    level,
                    this.worldPosition.relative(direction),
                    direction.getOpposite());
        }
        return this.energyOutputCaches[index].getCapability();
    }
}
