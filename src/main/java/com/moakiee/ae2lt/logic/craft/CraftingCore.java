package com.moakiee.ae2lt.logic.craft;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

public final class CraftingCore implements Sweepable {
    private static final String NBT_CELLS = "cells";
    private static final String NBT_INDEX = "i";
    private static final String NBT_COPIES = "n";
    private static final String NBT_OUTPUTS = "out";
    private static final String NBT_KEY = "k";
    private static final String NBT_AMOUNT = "v";

    private final CraftingCoreHost host;
    private final CoreParams params;
    private final CopyAssembler assembler;
    private final CraftingCoreRegistry registry;
    private final WheelCell[] wheel = new WheelCell[CoreParams.WHEEL_SIZE];
    private int threadsInFlight;
    private long lastSweptTick;

    public CraftingCore(CraftingCoreHost host, CoreParams params, CopyAssembler assembler,
                        CraftingCoreRegistry registry) {
        this.host = host;
        this.params = params;
        this.assembler = assembler;
        this.registry = registry;
        for (int i = 0; i < wheel.length; i++) {
            wheel[i] = new WheelCell();
        }
    }

    public int pushBatch(IPatternDetails details, KeyCounter[] scaledInputs, int maxCraft) {
        if (maxCraft <= 0) return 0;
        if (!(details instanceof IMolecularAssemblerSupportedPattern)) return maxCraft;

        long now = host.getGameTime();
        sweepNonLive(now);

        int available = host.maxThreads() - threadsInFlight;
        if (available <= 0) return maxCraft;
        int copies = Math.min(maxCraft, available);

        KeyCounter[] oneCopy = buildOneCopyTemplate(scaledInputs, maxCraft);
        if (oneCopy == null) return maxCraft;

        if (params.aePerCopy() > 0.0D) {
            double wanted = params.aePerCopy() * copies;
            double extracted = host.extractEnergy(wanted);
            int affordable = (int) Math.floor(extracted / params.aePerCopy());
            if (affordable <= 0) {
                host.injectEnergy(extracted);
                return maxCraft;
            }
            if (affordable < copies) {
                double refund = extracted - affordable * params.aePerCopy();
                if (refund > 0.0D) {
                    host.injectEnergy(refund);
                }
                copies = affordable;
            }
        }

        CopyAssembler.AssembledCopy assembled;
        try {
            assembled = assembler.assembleOneCopy(details, oneCopy);
        } catch (Throwable t) {
            if (params.aePerCopy() > 0.0D) {
                host.injectEnergy(copies * params.aePerCopy());
            }
            appeng.core.AELog.warn("[ae2lt] batch crafting core assemble failed for %s; refunding %d copies. %s",
                    details, copies, t);
            return maxCraft;
        }

        if (assembled == null || assembled.output() == null || assembled.outputCount() <= 0) {
            if (params.aePerCopy() > 0.0D) {
                host.injectEnergy(copies * params.aePerCopy());
            }
            return maxCraft;
        }

        WheelCell cell = wheel[(int) (now & CoreParams.WHEEL_MASK)];
        accumulate(cell, assembled.output(), assembled.outputCount() * copies);
        if (assembled.remainders() != null) {
            for (var remainder : assembled.remainders()) {
                if (remainder != null) {
                    accumulate(cell, remainder.key(), remainder.count() * copies);
                }
            }
        }

        cell.copies += copies;
        threadsInFlight += copies;
        registry.markActive(this);
        return maxCraft - copies;
    }

    @Override
    public boolean sweepTick() {
        if (host.isRemoved()) {
            drainAll(true);
            return false;
        }
        sweepNonLive(host.getGameTime());
        return threadsInFlight > 0;
    }

    public void drainAll(boolean forceSpawn) {
        for (int i = 0; i < wheel.length; i++) {
            drainSlot(i, forceSpawn);
        }
    }

    public void writeTo(CompoundTag tag, HolderLookup.Provider registries) {
        if (threadsInFlight <= 0) return;

        var cells = new ListTag();
        for (int i = 0; i < wheel.length; i++) {
            WheelCell cell = wheel[i];
            if (cell.copies <= 0 || cell.outputs.isEmpty()) continue;

            var cellTag = new CompoundTag();
            cellTag.putByte(NBT_INDEX, (byte) i);
            cellTag.putInt(NBT_COPIES, cell.copies);
            var outputs = new ListTag();
            for (Object2LongMap.Entry<AEKey> entry : cell.outputs.object2LongEntrySet()) {
                if (entry.getKey() == null || entry.getLongValue() <= 0) continue;
                var outputTag = new CompoundTag();
                outputTag.put(NBT_KEY, entry.getKey().toTagGeneric(registries));
                outputTag.putLong(NBT_AMOUNT, entry.getLongValue());
                outputs.add(outputTag);
            }
            if (outputs.isEmpty()) continue;
            cellTag.put(NBT_OUTPUTS, outputs);
            cells.add(cellTag);
        }

        if (!cells.isEmpty()) {
            tag.put(NBT_CELLS, cells);
        }
    }

    public void readFrom(CompoundTag tag, HolderLookup.Provider registries) {
        reset();
        if (!tag.contains(NBT_CELLS, Tag.TAG_LIST)) return;

        ListTag cells = tag.getList(NBT_CELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag cellTag = cells.getCompound(i);
            int idx = cellTag.getByte(NBT_INDEX) & CoreParams.WHEEL_MASK;
            int copies = cellTag.getInt(NBT_COPIES);
            if (copies <= 0) continue;

            WheelCell cell = wheel[idx];
            ListTag outputs = cellTag.getList(NBT_OUTPUTS, Tag.TAG_COMPOUND);
            for (int o = 0; o < outputs.size(); o++) {
                CompoundTag outputTag = outputs.getCompound(o);
                long amount = outputTag.getLong(NBT_AMOUNT);
                if (amount <= 0) continue;
                AEKey key = AEKey.fromTagGeneric(registries, outputTag.getCompound(NBT_KEY));
                if (key != null) {
                    cell.outputs.addTo(key, amount);
                }
            }
            if (cell.outputs.isEmpty()) continue;
            cell.copies += copies;
            threadsInFlight += copies;
        }

        if (threadsInFlight > 0) {
            registry.markActive(this);
        }
    }

    public int threadsInFlight() {
        return threadsInFlight;
    }

    public int availableCapacity() {
        sweepNonLive(host.getGameTime());
        return Math.max(0, host.maxThreads() - threadsInFlight);
    }

    private void sweepNonLive(long now) {
        if (threadsInFlight == 0) {
            lastSweptTick = now;
            return;
        }

        long from = Math.max(lastSweptTick + 1, now - CoreParams.WHEEL_SIZE + 1L);
        long completedThrough = lastSweptTick;
        for (long tick = from; tick <= now; tick++) {
            int slot = (int) ((tick - params.delayTicks()) & CoreParams.WHEEL_MASK);
            if (!drainSlot(slot, false)) {
                lastSweptTick = tick - 1L;
                return;
            }
            completedThrough = tick;
            if (threadsInFlight == 0) break;
        }
        lastSweptTick = threadsInFlight == 0 ? now : completedThrough;
    }

    private boolean drainSlot(int idx, boolean forceSpawn) {
        WheelCell cell = wheel[idx];
        if (cell.copies <= 0) return true;

        if (forceSpawn) {
            for (Object2LongMap.Entry<AEKey> entry : cell.outputs.object2LongEntrySet()) {
                if (entry.getLongValue() > 0) {
                    host.spawnToWorld(entry.getKey(), entry.getLongValue());
                }
            }
            releaseCell(cell);
            return true;
        }

        if (!host.isConnected()) {
            return false;
        }

        boolean anyLeft = false;
        var iter = cell.outputs.object2LongEntrySet().fastIterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            long amount = entry.getLongValue();
            if (amount <= 0) {
                iter.remove();
                continue;
            }
            long inserted = host.insertToNetwork(entry.getKey(), amount);
            long leftover = amount - inserted;
            if (leftover > 0) {
                entry.setValue(leftover);
                anyLeft = true;
            } else {
                iter.remove();
            }
        }

        if (!anyLeft) {
            releaseCell(cell);
            return true;
        }
        return false;
    }

    private void releaseCell(WheelCell cell) {
        threadsInFlight -= cell.copies;
        if (threadsInFlight < 0) threadsInFlight = 0;
        cell.outputs.clear();
        cell.copies = 0;
    }

    private void reset() {
        for (var cell : wheel) {
            cell.outputs.clear();
            cell.copies = 0;
        }
        threadsInFlight = 0;
    }

    private static void accumulate(WheelCell cell, AEKey key, long amount) {
        if (key != null && amount > 0) {
            cell.outputs.addTo(key, amount);
        }
    }

    private static KeyCounter[] buildOneCopyTemplate(KeyCounter[] scaledInputs, int maxCraft) {
        var result = new KeyCounter[scaledInputs.length];
        for (int i = 0; i < scaledInputs.length; i++) {
            KeyCounter src = scaledInputs[i];
            if (src.size() > 1) {
                return null;
            }
            var dst = new KeyCounter();
            for (var entry : src) {
                long amount = entry.getLongValue();
                if (amount <= 0 || amount % maxCraft != 0) {
                    return null;
                }
                long perCopy = amount / maxCraft;
                if (perCopy <= 0) return null;
                dst.add(entry.getKey(), perCopy);
            }
            result[i] = dst;
        }
        return result;
    }
}
