package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import appeng.client.render.overlay.OverlayRenderType;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;

/**
 * Client-side renderer for the Overloaded Wireless Connector.
 * <p>
 * When the player holds the connector, all wireless-capable hosts within range
 * are highlighted with their connections. A preview overlay is shown for the selected host.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public class WirelessConnectorRenderer {

    // Preview color: semi-transparent yellow (ARGB)
    private static final int COLOR_PREVIEW = 0x60FFFF00;
    // Connected face color: semi-transparent blue (ARGB)
    private static final int COLOR_CONNECTED = 0x600080FF;
    // Preview line color: bright yellow
    private static final int COLOR_PREVIEW_LINE = 0xC0FFFF00;
    // Host inner cube color (unselected): semi-transparent blue (ARGB)
    private static final int COLOR_HOST = 0x800080FF;
    // Host inner cube color (selected): semi-transparent yellow (ARGB)
    private static final int COLOR_HOST_SELECTED = 0x80FFFF00;
    // Line color: blue (ARGB)
    private static final int COLOR_LINE = 0xC00080FF;

    private static final int SCAN_RANGE = 64;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // Check if player is holding the wireless connector in either hand
        ItemStack stack = ItemStack.EMPTY;
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var held = player.getItemInHand(hand);
            if (held.getItem() instanceof OverloadedWirelessConnectorItem) {
                stack = held;
                break;
            }
        }
        if (stack.isEmpty()) {
            return;
        }

        // -- Set up camera offset --
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Quaternionf rotation = new Quaternionf(mc.gameRenderer.getMainCamera().rotation());
        rotation.invert();
        poseStack.mulPose(rotation);
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        // Resolve selected host for special coloring and preview rendering.
        var selectedPos = getSelectedHostPos(stack);
        var selectedDim = getSelectedHostDim(stack);
        var selectedHostType = getSelectedHostType(stack);
        boolean hasSelection = selectedPos != null
                && selectedDim != null
                && selectedHostType != null
                && mc.level.dimension().equals(selectedDim);
        boolean selectedRendered = false;

        // Scan all wireless hosts in loaded chunks within range.
        BlockPos playerPos = player.blockPosition();
        int minCX = (playerPos.getX() - SCAN_RANGE) >> 4;
        int maxCX = (playerPos.getX() + SCAN_RANGE) >> 4;
        int minCZ = (playerPos.getZ() - SCAN_RANGE) >> 4;
        int maxCZ = (playerPos.getZ() + SCAN_RANGE) >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                var chunk = mc.level.getChunk(cx, cz);
                for (var bePos : chunk.getBlockEntitiesPos()) {
                    var be = chunk.getBlockEntity(bePos);
                    if (be instanceof OverloadedPatternProviderBlockEntity provider) {
                        if (provider.getProviderMode() == OverloadedPatternProviderBlockEntity.ProviderMode.NORMAL) {
                            continue;
                        }

                        boolean isSelected = hasSelection
                                && OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                                && bePos.equals(selectedPos);
                        renderProviderHost(poseStack, buffer, mc.level, bePos, provider, isSelected);
                        selectedRendered |= isSelected;
                        continue;
                    }

                    if (be instanceof OverloadedInterfaceBlockEntity iface) {
                        if (iface.getInterfaceMode() != OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                            continue;
                        }

                        boolean isSelected = hasSelection
                                && OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                                && bePos.equals(selectedPos);
                        renderInterfaceHost(poseStack, buffer, mc.level, bePos, iface, isSelected);
                        selectedRendered |= isSelected;
                    }
                }
            }
        }

        if (hasSelection && !selectedRendered && mc.level.isLoaded(selectedPos)) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                    && selectedBe instanceof OverloadedPatternProviderBlockEntity provider
                    && provider.getProviderMode() != OverloadedPatternProviderBlockEntity.ProviderMode.NORMAL) {
                renderProviderHost(poseStack, buffer, mc.level, selectedPos, provider, true);
            } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                    && selectedBe instanceof OverloadedInterfaceBlockEntity iface
                    && iface.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                renderInterfaceHost(poseStack, buffer, mc.level, selectedPos, iface, true);
            }
        }

        // Preview face overlay (only for the selected wireless host).
        if (hasSelection) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                    && selectedBe instanceof OverloadedPatternProviderBlockEntity selectedProvider
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                for (var lookPos : previewTargets) {
                    boolean alreadyConnected = selectedProvider.getConnections().stream()
                            .anyMatch(c -> c.dimension().equals(mc.level.dimension())
                                    && c.pos().equals(lookPos)
                                    && c.boundFace() == lookFace);

                    if (!alreadyConnected) {
                        renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                        renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE);
                    }
                }
            } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                    && selectedBe instanceof OverloadedInterfaceBlockEntity selectedInterface
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                for (var lookPos : previewTargets) {
                    boolean alreadyConnected = selectedInterface.getConnections().stream()
                            .anyMatch(c -> c.dimension().equals(mc.level.dimension())
                                    && c.pos().equals(lookPos)
                                    && c.boundFace() == lookFace);

                    if (!alreadyConnected) {
                        renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                        renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE);
                    }
                }
            }
        }

        poseStack.popPose();

        // Flush render batches
        buffer.endBatch(Ae2ltRenderTypes.getFaceSeeThrough());
        buffer.endBatch(OverlayRenderType.getBlockHilightFace());
        buffer.endBatch(OverlayRenderType.getBlockHilightLine());
    }

    // -- Render helpers --

    /**
     * Render a small cube inside the block using a see-through render type
     * (GREATER depth test) so it is visible through the opaque block.
     */
    private static void renderInnerCube(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, int color) {
        VertexConsumer vc = buffer.getBuffer(Ae2ltRenderTypes.getFaceSeeThrough());
        int[] c = OverlayRenderType.decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();

        float lo = 0.25f, hi = 0.75f;

        // DOWN (Y-)
        quad(vc, mat, c, lo, lo, lo,  hi, lo, lo,  hi, lo, hi,  lo, lo, hi,  0, -1, 0);
        // UP (Y+)
        quad(vc, mat, c, lo, hi, hi,  hi, hi, hi,  hi, hi, lo,  lo, hi, lo,  0, 1, 0);
        // NORTH (Z-)
        quad(vc, mat, c, lo, lo, lo,  lo, hi, lo,  hi, hi, lo,  hi, lo, lo,  0, 0, -1);
        // SOUTH (Z+)
        quad(vc, mat, c, hi, lo, hi,  hi, hi, hi,  lo, hi, hi,  lo, lo, hi,  0, 0, 1);
        // WEST (X-)
        quad(vc, mat, c, lo, lo, hi,  lo, hi, hi,  lo, hi, lo,  lo, lo, lo,  -1, 0, 0);
        // EAST (X+)
        quad(vc, mat, c, hi, lo, lo,  hi, hi, lo,  hi, hi, hi,  hi, lo, hi,  1, 0, 0);

        poseStack.popPose();
    }

    private static void renderProviderHost(PoseStack poseStack, MultiBufferSource buffer,
            Level level, BlockPos hostPos, OverloadedPatternProviderBlockEntity provider, boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : provider.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) continue;
            renderFaceOverlay(poseStack, buffer, conn.pos(), conn.boundFace(), COLOR_CONNECTED);
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE);
        }
    }

    private static void renderInterfaceHost(PoseStack poseStack, MultiBufferSource buffer,
            Level level, BlockPos hostPos, OverloadedInterfaceBlockEntity iface, boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : iface.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) continue;
            renderFaceOverlay(poseStack, buffer, conn.pos(), conn.boundFace(), COLOR_CONNECTED);
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, int[] c,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz) {
        vc.addVertex(mat, x1, y1, z1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, x4, y4, z4).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
    }

    private static void line(VertexConsumer vc, Matrix4f mat, int[] c,
            float x1, float y1, float z1, float x2, float y2, float z2) {
        // Compute direction for normal
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        vc.addVertex(mat, x1, y1, z1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
    }

    /**
     * Render a single face overlay (quad) on the given block face.
     */
    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, Direction face, int color) {
        VertexConsumer vc = buffer.getBuffer(OverlayRenderType.getBlockHilightFace());
        int[] c = OverlayRenderType.decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();

        // Slight offset to avoid z-fighting
        float offset = 0.001f;

        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        switch (face) {
            case DOWN -> {
                vc.addVertex(mat, 0, -offset, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, -offset, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, -offset, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, -offset, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case UP -> {
                vc.addVertex(mat, 0, 1 + offset, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 1 + offset, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 1 + offset, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, 1 + offset, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case NORTH -> {
                vc.addVertex(mat, 0, 0, -offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, 1, -offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 1, -offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 0, -offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case SOUTH -> {
                vc.addVertex(mat, 1, 0, 1 + offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 1, 1 + offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, 1, 1 + offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, 0, 1 + offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case WEST -> {
                vc.addVertex(mat, -offset, 0, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, -offset, 1, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, -offset, 1, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, -offset, 0, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case EAST -> {
                vc.addVertex(mat, 1 + offset, 0, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1 + offset, 1, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1 + offset, 1, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1 + offset, 0, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
        }

        poseStack.popPose();
    }

    /**
     * Render a line from provider center to the center of the connected face.
     */
    private static void renderLine(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos from, BlockPos to, Direction face, int color) {
        VertexConsumer vc = buffer.getBuffer(OverlayRenderType.getBlockHilightLine());
        int[] c = OverlayRenderType.decomposeColor(color);

        Matrix4f mat = poseStack.last().pose();

        // Provider center
        float fx = from.getX() + 0.5f;
        float fy = from.getY() + 0.5f;
        float fz = from.getZ() + 0.5f;

        // Target face center
        float tx = to.getX() + 0.5f + face.getStepX() * 0.501f;
        float ty = to.getY() + 0.5f + face.getStepY() * 0.501f;
        float tz = to.getZ() + 0.5f + face.getStepZ() * 0.501f;

        float dx = tx - fx, dy = ty - fy, dz = tz - fz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;

        vc.addVertex(mat, fx, fy, fz).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, tx, ty, tz).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
    }

    // -- Item NBT helpers (client-side read-only) --

    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HOST_TYPE = "HostType";

    private static BlockPos getSelectedHostPos(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        var sel = tag.getCompound(TAG_SELECTED);
        return BlockPos.of(sel.getLong(TAG_POS));
    }

    private static ResourceKey<Level> getSelectedHostDim(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        var sel = tag.getCompound(TAG_SELECTED);
        var dimStr = sel.getString(TAG_DIM);
        if (dimStr.isEmpty()) {
            return null;
        }
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimStr));
    }

    private static String getSelectedHostType(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        var sel = tag.getCompound(TAG_SELECTED);
        return sel.contains(TAG_HOST_TYPE, CompoundTag.TAG_STRING)
                ? sel.getString(TAG_HOST_TYPE)
                : OverloadedWirelessConnectorItem.HOST_PROVIDER;
    }
}
