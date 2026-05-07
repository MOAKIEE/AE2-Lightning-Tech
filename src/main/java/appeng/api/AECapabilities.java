package appeng.api;

import net.minecraftforge.common.capabilities.Capability;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.capabilities.Capabilities;

/**
 * 1.21 API alias for the 1.20.1 Forge AE2 capability container.
 */
public final class AECapabilities {
    public static final Capability<GenericInternalInventory> GENERIC_INTERNAL_INV =
            Capabilities.GENERIC_INTERNAL_INV;

    public static final Capability<IInWorldGridNodeHost> IN_WORLD_GRID_NODE_HOST =
            Capabilities.IN_WORLD_GRID_NODE_HOST;

    private AECapabilities() {
    }
}
