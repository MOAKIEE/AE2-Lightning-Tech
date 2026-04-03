package com.moakiee.ae2lt.mixin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.BorrowedCapacityCalculator;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import appeng.me.pathfinding.PathingCalculation;

/**
 * Replaces AE2's BFS-based channel assignment with Dinic's max-flow
 * for all controller networks.
 * <p>
 * Any network containing at least one controller (vanilla or overloaded)
 * uses max-flow for channel assignment. Only ad-hoc networks (no
 * controllers at all) fall through to vanilla AE2 logic.
 * <p>
 * Phase 1 (constructor TAIL): identify overloaded controllers, unify them
 * into a single BFS root for correct DFS tree propagation.
 * <p>
 * Phase 2 (tryUseChannel HEAD): return false for ALL devices in the network
 * so AE2 builds the routing tree without assigning channels.
 * <p>
 * Phase 3 (compute, before propagateAssignments): run max-flow, inject
 * winning devices into {@code channelNodes}, and set {@code usedChannels}
 * on each cable/node from the flow decomposition.
 */
@Mixin(PathingCalculation.class)
public abstract class PathingCalculationCapMixin {

    @Shadow @Final private IGrid grid;
    @Shadow @Final private Set<IPathItem> visited;
    @Shadow @Final private Queue<IPathItem>[] queues;
    @Shadow @Final private Set<GridNode> channelNodes;
    @Shadow @Final private Set<GridNode> multiblocksWithChannel;

    @Unique private List<IGridNode> ae2lt$overloadedControllers;
    @Unique private boolean ae2lt$useMaxFlow;
    @Unique private BorrowedCapacityCalculator.Result ae2lt$flowResult;

    // ── Phase 1: constructor – identify & unify overloaded controllers ──

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2lt$unifyOverloadedControllers(IGrid grid, CallbackInfo ci) {
        List<IGridNode> overloaded = new ArrayList<>();
        for (var node : grid.getMachineNodes(ControllerBlockEntity.class)) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) {
                overloaded.add(node);
            }
        }

        ae2lt$overloadedControllers = overloaded;
        boolean hasControllers = grid.getMachineNodes(ControllerBlockEntity.class).iterator().hasNext();
        var channelMode = grid.getPathingService().getChannelMode();
        ae2lt$useMaxFlow = hasControllers && channelMode != ChannelMode.INFINITE;

        if (overloaded.size() <= 1) {
            return;
        }

        IGridNode source = overloaded.get(0);
        Set<IGridNode> nonSource = new ReferenceOpenHashSet<>();
        for (int i = 1; i < overloaded.size(); i++) {
            nonSource.add(overloaded.get(i));
        }

        for (var node : nonSource) {
            if (node instanceof IPathItem p) {
                visited.remove(p);
            }
        }

        Queue<IPathItem> q0 = queues[0];
        var keep = new ArrayDeque<IPathItem>();
        while (!q0.isEmpty()) {
            var item = q0.poll();
            if (item instanceof GridConnection gc) {
                if (nonSource.contains(gc.a()) || nonSource.contains(gc.b())) {
                    visited.remove((IPathItem) gc);
                    gc.setControllerRoute(null);
                    continue;
                }
            }
            keep.add(item);
        }
        q0.addAll(keep);

        for (var connection : source.getConnections()) {
            if (connection instanceof GridConnection gc) {
                IGridNode other = gc.getOtherSide(source);
                if (nonSource.contains(other) && !visited.contains((IPathItem) gc)) {
                    gc.setControllerRoute((IPathItem) source);
                    visited.add((IPathItem) gc);
                    q0.add((IPathItem) gc);
                }
            }
        }
    }

    // ── Phase 2: skip AE2 channel assignment for ALL devices ──

    @Inject(method = "tryUseChannel", at = @At("HEAD"), cancellable = true)
    private void ae2lt$skipAllDevices(GridNode node, CallbackInfoReturnable<Boolean> cir) {
        if (ae2lt$useMaxFlow) {
            cir.setReturnValue(false);
        }
    }

    // ── Phase 3: run max-flow between BFS and DFS ──
    //   Sets static flow data so GridNode.propagateChannelsUpwards can read it
    //   during the subsequent DFS pass.

    @Inject(method = "compute",
            at = @At(value = "INVOKE",
                     target = "Lappeng/me/pathfinding/PathingCalculation;propagateAssignments()V"))
    private void ae2lt$runMaxFlowBeforeDFS(CallbackInfo ci) {
        if (!ae2lt$useMaxFlow) {
            BorrowedCapacityCalculator.activeNodeFlow = null;
            BorrowedCapacityCalculator.activeNetworkNodes = null;
            return;
        }

        ae2lt$flowResult = BorrowedCapacityCalculator.assignChannels(grid, ae2lt$overloadedControllers);
        if (ae2lt$flowResult == null) {
            BorrowedCapacityCalculator.activeNodeFlow = null;
            BorrowedCapacityCalculator.activeNetworkNodes = null;
            return;
        }

        channelNodes.addAll(ae2lt$flowResult.channelNodes());

        for (var winner : ae2lt$flowResult.channelNodes()) {
            if (!winner.hasFlag(GridFlags.MULTIBLOCK)) continue;
            var multiblock = ((IGridNode) winner).getService(IGridMultiblock.class);
            if (multiblock == null) continue;
            var siblings = multiblock.getMultiblockNodes();
            while (siblings.hasNext()) {
                var sibling = siblings.next();
                if (sibling != null && sibling != winner) {
                    multiblocksWithChannel.add((GridNode) sibling);
                }
            }
        }

        BorrowedCapacityCalculator.activeNodeFlow = ae2lt$flowResult.nodeFlow();
        BorrowedCapacityCalculator.activeNetworkNodes = ae2lt$flowResult.networkNodes();
    }

    // ── Phase 4: cleanup after DFS ──

    @Inject(method = "compute", at = @At("TAIL"))
    private void ae2lt$clearFlowData(CallbackInfo ci) {
        ae2lt$flowResult = null;
    }
}
