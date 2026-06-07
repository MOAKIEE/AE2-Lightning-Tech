package com.moakiee.ae2lt.worldgen;

public final class FirmamentStarshipPlacement {

    private FirmamentStarshipPlacement() {
    }

    public static Position offsetFromAnchor(
            int anchorX,
            int anchorY,
            int anchorZ,
            int horizontalOffset,
            int verticalOffset) {
        double length = Math.sqrt((double) anchorX * anchorX + (double) anchorZ * anchorZ);
        if (length < 1.0D) {
            return new Position(anchorX + horizontalOffset, anchorY + verticalOffset, anchorZ);
        }

        int offsetX = (int) Math.round(anchorX / length * horizontalOffset);
        int offsetZ = (int) Math.round(anchorZ / length * horizontalOffset);
        return new Position(anchorX + offsetX, anchorY + verticalOffset, anchorZ + offsetZ);
    }

    public record Position(int x, int y, int z) {
    }
}
