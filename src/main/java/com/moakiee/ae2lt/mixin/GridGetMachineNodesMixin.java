package com.moakiee.ae2lt.mixin;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGridNode;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.Grid;

@Mixin(value = Grid.class, remap = false)
public abstract class GridGetMachineNodesMixin {

    @Shadow
    @Final
    private SetMultimap<Class<?>, IGridNode> machines;

    @Inject(method = "getMachineClasses", at = @At("HEAD"), cancellable = true)
    private void ae2lt$normalizeControllerMachineClasses(CallbackInfoReturnable<Iterable<Class<?>>> cir) {
        if (!ae2lt$hasOverloadedControllerClass()) {
            return;
        }

        var machineClasses = new LinkedHashSet<Class<?>>(this.machines.keySet());
        machineClasses.removeIf(OverloadedControllerBlockEntity.class::isAssignableFrom);
        machineClasses.add(ControllerBlockEntity.class);
        cir.setReturnValue(machineClasses);
    }

    @Inject(method = "getMachineNodes", at = @At("HEAD"), cancellable = true)
    private void ae2lt$includeOverloadedControllersForControllerQueries(Class<?> machineClass,
            CallbackInfoReturnable<Iterable<IGridNode>> cir) {
        if (machineClass != ControllerBlockEntity.class) {
            return;
        }

        var controllerNodeSets = new ArrayList<Iterable<IGridNode>>();
        controllerNodeSets.add(this.machines.get(ControllerBlockEntity.class));

        for (var clazz : this.machines.keySet()) {
            if (OverloadedControllerBlockEntity.class.isAssignableFrom(clazz)) {
                controllerNodeSets.add(this.machines.get(clazz));
            }
        }

        if (controllerNodeSets.size() == 1) {
            return;
        }

        // Compatibility shim only:
        // AE2 stores nodes by exact owner class, so a query for
        // ControllerBlockEntity.class would miss our subclass otherwise.
        // This only affects controller-class queries and appends all AE2LT
        // overloaded controller subtypes, leaving vanilla lookups intact.
        cir.setReturnValue(Iterables.concat(controllerNodeSets));
    }

    private boolean ae2lt$hasOverloadedControllerClass() {
        for (var clazz : this.machines.keySet()) {
            if (OverloadedControllerBlockEntity.class.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }
}
