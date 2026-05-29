package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.LightningTransformService;
import com.moakiee.ae2lt.lightning.ProtectedItemEntityHelper;
import com.moakiee.ae2lt.logic.research.ResearchRitualService;
import com.moakiee.ae2lt.network.EasterEggPacket;
import net.minecraft.core.BlockPos;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;
import com.moakiee.ae2lt.network.NetworkInit;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class LightningItemTransformationHandler {
    private static final String TRANSFORMATION_CHECKED_TAG = "ae2lt.lightning_item_transform_checked";
    private static final ResourceLocation FUMO_BLOCK_ID =
            new ResourceLocation("appliedcreate", "whichball_skin_doll");
    private static final int EASTER_EGG_SEARCH_RADIUS = 3;

    private LightningItemTransformationHandler() {
    }

    /**
     * Called by LightningBoltTickMixin at the HEAD of LightningBolt#tick.
     */
    public static void onLightningTick(LightningBolt lightningBolt, ServerLevel serverLevel) {
        var data = lightningBolt.getPersistentData();
        if (data.getBoolean(TRANSFORMATION_CHECKED_TAG)) {
            return;
        }

        data.putBoolean(TRANSFORMATION_CHECKED_TAG, true);
        ResearchRitualService.handleLightning(serverLevel, lightningBolt);
        LightningTransformService.handleLightning(serverLevel, lightningBolt);
        checkEasterEgg(serverLevel, lightningBolt);
    }

    /**
     * Called by ItemEntityTickMixin at the TAIL of ItemEntity#tick.
     */
    public static void onItemTick(ItemEntity itemEntity) {
        ProtectedItemEntityHelper.tick(itemEntity);
    }

    /**
     * Called by EntityInvulnerableMixin at the RETURN of Entity#isInvulnerableTo.
     */
    public static boolean shouldIgnoreDamage(ItemEntity itemEntity, DamageSource source) {
        return ProtectedItemEntityHelper.shouldIgnoreDamage(itemEntity, source);
    }

    @SubscribeEvent
    public static void onEntityStruckByLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ItemEntity itemEntity
                && (ProtectedItemEntityHelper.isProtectedItem(itemEntity)
                        || ProtectedItemEntityHelper.isFireproofItem(itemEntity))) {
            event.setCanceled(true);
        }
    }

    private static void checkEasterEgg(ServerLevel level, LightningBolt lightningBolt) {
        Block fumoBlock = ForgeRegistries.BLOCKS.getValue(FUMO_BLOCK_ID);
        if (fumoBlock == null) {
            return;
        }

        BlockPos center = new BlockPos(
                (int) Math.floor(lightningBolt.getX()),
                (int) Math.floor(lightningBolt.getY()),
                (int) Math.floor(lightningBolt.getZ()));
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-EASTER_EGG_SEARCH_RADIUS, -1, -EASTER_EGG_SEARCH_RADIUS),
                center.offset(EASTER_EGG_SEARCH_RADIUS, 2, EASTER_EGG_SEARCH_RADIUS))) {
            if (level.getBlockState(pos).is(fumoBlock)) {
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) {
                        NetworkInit.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EasterEggPacket());
                    }
                }
                return;
            }
        }
    }
}
