package com.moakiee.ae2lt.entity;

import com.moakiee.ae2lt.logic.LightningBlastTask;
import com.moakiee.ae2lt.logic.LightningBlastTaskManager;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.state.BlockState;

public class OverloadTntEntity extends PrimedTnt {
    private static final String TAG_OWNER = "Owner";
    private static final float COMPATIBILITY_EXPLOSION_POWER = 4.0F;

    @Nullable
    private LivingEntity owner;

    @Nullable
    private UUID ownerUuid;

    public OverloadTntEntity(EntityType<? extends OverloadTntEntity> entityType, Level level) {
        super(entityType, level);
        this.setBlockState(getDefaultBlockState());
    }

    public OverloadTntEntity(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        super(ModEntities.OVERLOAD_TNT.get(), level);
        this.setPos(x, y, z);
        this.setFuse(80);
        this.setBlockState(getDefaultBlockState());
        this.owner = owner;
        this.ownerUuid = owner != null ? owner.getUUID() : null;

        double angle = level.random.nextDouble() * (Math.PI * 2.0D);
        this.setDeltaMovement(-Math.sin(angle) * 0.02D, 0.2D, -Math.cos(angle) * 0.02D);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        if (owner == null && ownerUuid != null && this.level() instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(ownerUuid);
            if (entity instanceof LivingEntity livingEntity) {
                owner = livingEntity;
            }
        }
        return owner != null ? owner : super.getOwner();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER, ownerUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = null;
        ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    @Override
    protected void explode() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.explode(
                    this,
                    this.getX(),
                    this.getY(0.0625D),
                    this.getZ(),
                    COMPATIBILITY_EXPLOSION_POWER,
                    false,
                    ExplosionInteraction.TNT);
            LightningBlastTaskManager.schedule(new LightningBlastTask(serverLevel, this.blockPosition()));
        }
    }

    private static BlockState getDefaultBlockState() {
        return ModBlocks.OVERLOAD_TNT.get().defaultBlockState();
    }
}
