package com.moakiee.ae2lt.event;

import appeng.api.parts.IPartItem;
import appeng.parts.PartPlacement;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class FrequencyCardAutoConnectHandler {
    private FrequencyCardAutoConnectHandler() {
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            registry.queueAutoConnect(player, player.level().dimension(), event.getPos(), null, 2);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof IPartItem<?>)) {
            return;
        }
        var clickedFace = event.getFace();
        if (clickedFace == null) {
            return;
        }
        var placement = PartPlacement.getPartPlacement(
                player,
                player.level(),
                event.getItemStack(),
                event.getPos(),
                clickedFace,
                event.getHitVec().getLocation());
        if (placement == null) {
            return;
        }
        var target = toAutoConnectTarget(event.getPos(), clickedFace, placement);
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            registry.queueAutoConnect(player, player.level().dimension(), target.pos(), target.side(), 2);
        }
    }

    static PartAutoConnectTarget toAutoConnectTarget(
            BlockPos clickedPos,
            Direction clickedSide,
            PartPlacement.Placement placement) {
        var target = FrequencyCardAutoConnectTarget.fromPartPlacement(
                new FrequencyCardAutoConnectTarget.GridPos(clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()),
                clickedSide.getName(),
                new FrequencyCardAutoConnectTarget.GridPos(
                        placement.pos().getX(),
                        placement.pos().getY(),
                        placement.pos().getZ()),
                placement.side().getName());
        return new PartAutoConnectTarget(
                new BlockPos(target.pos().x(), target.pos().y(), target.pos().z()),
                Direction.byName(target.sideName()));
    }

    record PartAutoConnectTarget(BlockPos pos, Direction side) {
    }
}
