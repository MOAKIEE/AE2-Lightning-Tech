package com.moakiee.ae2lt.item;

import appeng.core.particles.ParticleTypes;
import com.moakiee.ae2lt.event.ArtificialLightningHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class OverloadCrystalItem extends Item {
    private static final String DROPPED_TICKS_TAG = "ae2lt.overload_dropped_ticks";
    private static final int SUMMON_DELAY_TICKS = 200;
    /**
     * Granularity of the dropped-state countdown on the server. With this set to 4, the
     * NBT counter advances at 5 Hz instead of 20 Hz, cutting persistent-data churn by 75%
     * while still firing the artificial bolt within ~200ms of the configured 10s delay.
     */
    private static final int DROPPED_TICK_INTERVAL = 4;

    public OverloadCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level().isClientSide()) {
            spawnDroppedLightning(entity);
        } else if (entity.level() instanceof ServerLevel serverLevel) {
            // Only advance the timer once per DROPPED_TICK_INTERVAL ticks. tickCount is an
            // in-memory counter that resets when the entity reloads, but the NBT-stored
            // counter preserves progress across saves, so the worst-case slip after a
            // reload is one interval (≤200ms).
            if ((entity.tickCount % DROPPED_TICK_INTERVAL) != 0) {
                return false;
            }

            int droppedTicks = entity.getPersistentData().getIntOr(DROPPED_TICKS_TAG, 0) + DROPPED_TICK_INTERVAL;
            if (droppedTicks >= SUMMON_DELAY_TICKS) {
                entity.getPersistentData().putInt(DROPPED_TICKS_TAG, 0);
                ArtificialLightningHandler.spawnArtificialLightning(serverLevel, entity.position(), null);
            } else {
                entity.getPersistentData().putInt(DROPPED_TICKS_TAG, droppedTicks);
            }
        }
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static void spawnHeldLightning(Level level, Entity player, boolean mainHand) {
        RandomSource random = level.getRandom();
        if (random.nextInt(12) != 0) {
            return;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-4D) {
            right = new Vec3(mainHand ? 1.0D : -1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize().scale(mainHand ? 0.28D : -0.28D);
        }

        Vec3 handPos = eyePos
                .add(look.scale(0.35D))
                .add(right)
                .add(0.0D, -0.22D, 0.0D);
        double x = handPos.x + (random.nextDouble() - 0.5D) * 0.12D;
        double y = handPos.y + (random.nextDouble() - 0.5D) * 0.12D;
        double z = handPos.z + (random.nextDouble() - 0.5D) * 0.12D;

        spawnParticle(level, x, y, z);
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnDroppedLightning(ItemEntity entity) {
        RandomSource random = entity.level().getRandom();
        if (random.nextInt(12) != 0) {
            return;
        }

        double x = entity.getX() + (random.nextDouble() - 0.5D) * 0.3D;
        double y = entity.getY() + 0.1D + (random.nextDouble() - 0.5D) * 0.2D;
        double z = entity.getZ() + (random.nextDouble() - 0.5D) * 0.3D;

        spawnParticle(entity.level(), x, y, z);
    }

    @OnlyIn(Dist.CLIENT)
    private static void spawnParticle(Level level, double x, double y, double z) {
        level.addParticle(ParticleTypes.LIGHTNING, x, y, z, 0.0D, 0.0D, 0.0D);
    }
}
