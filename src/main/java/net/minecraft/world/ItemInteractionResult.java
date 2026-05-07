package net.minecraft.world;

/**
 * Minimal 1.21 interaction-result shim for 1.20.1 block/item interaction code.
 */
public enum ItemInteractionResult {
    SUCCESS(InteractionResult.SUCCESS),
    CONSUME(InteractionResult.CONSUME),
    PASS(InteractionResult.PASS),
    FAIL(InteractionResult.FAIL),
    PASS_TO_DEFAULT_BLOCK_INTERACTION(InteractionResult.PASS);

    private final InteractionResult interactionResult;

    ItemInteractionResult(InteractionResult interactionResult) {
        this.interactionResult = interactionResult;
    }

    public InteractionResult asInteractionResult() {
        return interactionResult;
    }

    public static ItemInteractionResult sidedSuccess(boolean clientSide) {
        return clientSide ? SUCCESS : CONSUME;
    }
}
