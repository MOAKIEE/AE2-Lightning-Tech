package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.OverloadCrystalItem;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class ArtificialLightningHandler {
    public static final String ARTIFICIAL_LIGHTNING_TAG = "ae2lt.artificial_lightning";
    private static final String LIGHTNING_SOURCE_TAG = "ae2lt.lightning_source";
    private static final String HELD_TICKS_TAG = "ae2lt.overload_held_ticks";
    private static final int SUMMON_DELAY_TICKS = 200;

    private ArtificialLightningHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel serverLevel) || player.isSpectator()) {
            return;
        }

        boolean carryingOverloadCrystal = player.getOffhandItem().getItem() instanceof OverloadCrystalItem
                || player.getInventory().items.stream().anyMatch(stack -> stack.getItem() instanceof OverloadCrystalItem);

        if (!carryingOverloadCrystal) {
            player.getPersistentData().remove(HELD_TICKS_TAG);
            return;
        }

        int heldTicks = player.getPersistentData().getInt(HELD_TICKS_TAG) + 1;
        if (heldTicks < SUMMON_DELAY_TICKS) {
            player.getPersistentData().putInt(HELD_TICKS_TAG, heldTicks);
            return;
        }

        player.getPersistentData().putInt(HELD_TICKS_TAG, 0);
        spawnArtificialLightning(serverLevel, player.position(), player instanceof ServerPlayer serverPlayer ? serverPlayer : null,
                "held");
    }

    public static void spawnArtificialLightning(ServerLevel level, Vec3 position, @Nullable ServerPlayer cause,
            String source) {
        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(level);
        if (lightningBolt == null) {
            return;
        }

        lightningBolt.moveTo(position);
        lightningBolt.setVisualOnly(false);
        if (cause != null) {
            lightningBolt.setCause(cause);
        }

        var data = lightningBolt.getPersistentData();
        data.putBoolean(ARTIFICIAL_LIGHTNING_TAG, true);
        data.putString(LIGHTNING_SOURCE_TAG, source);

        level.addFreshEntity(lightningBolt);
    }
}
