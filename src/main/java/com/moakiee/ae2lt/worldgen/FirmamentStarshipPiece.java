package com.moakiee.ae2lt.worldgen;

import com.moakiee.ae2lt.registry.ModStructureTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class FirmamentStarshipPiece extends TemplateStructurePiece {

    public FirmamentStarshipPiece(
            StructureTemplateManager structureTemplateManager,
            ResourceLocation template,
            BlockPos position,
            Rotation rotation) {
        super(
                ModStructureTypes.FIRMAMENT_STARSHIP_PIECE.get(),
                0,
                structureTemplateManager,
                template,
                template.toString(),
                makeSettings(rotation),
                position);
    }

    public FirmamentStarshipPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
        super(
                ModStructureTypes.FIRMAMENT_STARSHIP_PIECE.get(),
                tag,
                structureTemplateManager,
                location -> makeSettings(readRotation(tag)));
    }

    private static StructurePlaceSettings makeSettings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
                .setRotation(rotation);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().getSerializedName());
    }

    private static Rotation readRotation(CompoundTag tag) {
        String serializedName = tag.getString("Rot");
        for (Rotation rotation : Rotation.values()) {
            if (rotation.getSerializedName().equals(serializedName)) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    @Override
    protected void handleDataMarker(
            String markerId,
            BlockPos position,
            ServerLevelAccessor level,
            RandomSource random,
            BoundingBox chunkBB) {
    }
}
