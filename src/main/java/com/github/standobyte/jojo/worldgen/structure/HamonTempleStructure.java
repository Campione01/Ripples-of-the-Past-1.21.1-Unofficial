package com.github.standobyte.jojo.worldgen.structure;

import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.nbt.CompoundTag;

public class HamonTempleStructure extends Structure {
    public static final MapCodec<HamonTempleStructure> CODEC = simpleCodec(HamonTempleStructure::new);

    private static final ResourceLocation BUILDING = JojoMod.resLoc("hamon_temple/building");
    private static final ResourceLocation PATHWAY = JojoMod.resLoc("hamon_temple/pathway");
    private static final ResourceLocation[] ROCKS = {
            JojoMod.resLoc("hamon_temple/rock_1"),
            JojoMod.resLoc("hamon_temple/rock_2"),
            JojoMod.resLoc("hamon_temple/rock_3"),
            JojoMod.resLoc("hamon_temple/rock_4"),
            JojoMod.resLoc("hamon_temple/rock_5"),
            JojoMod.resLoc("hamon_temple/rock_6"),
            JojoMod.resLoc("hamon_temple/rock_7"),
            JojoMod.resLoc("hamon_temple/rock_8") };

    public HamonTempleStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        if (y < 90) {
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin, builder -> generatePieces(context.structureTemplateManager(), origin, builder, context.random())));
    }

    private static void generatePieces(StructureTemplateManager templateManager, BlockPos origin, StructurePiecesBuilder builder, RandomSource random) {
        builder.addPiece(new Piece(templateManager, BUILDING.toString(), origin.offset(-24, -3, -24), Rotation.NONE));
        for (Rotation rotation : Rotation.values()) {
            builder.addPiece(new Piece(templateManager, PATHWAY.toString(), origin.offset(-1, -4, -1).offset(new BlockPos(-22, 0, -1).rotate(rotation)), rotation));
            ResourceLocation rock = ROCKS[random.nextInt(ROCKS.length)];
            builder.addPiece(new Piece(templateManager, rock.toString(), origin.offset(new BlockPos(-21, -3, 0).rotate(rotation)), Rotation.NONE));
        }
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HAMON_TEMPLE.get();
    }

    public static class Piece extends TemplateStructurePiece {
        private final Rotation rotation;

        public Piece(StructureTemplateManager manager, String templateName, BlockPos templatePosition, Rotation rotation) {
            super(ModStructures.HAMON_TEMPLE_PIECE.get(), 0, manager, ResourceLocation.parse(templateName), templateName,
                    new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE), templatePosition);
            this.rotation = rotation;
        }

        public Piece(StructureTemplateManager manager, CompoundTag tag) {
            super(ModStructures.HAMON_TEMPLE_PIECE.get(), tag, manager,
                    location -> new StructurePlaceSettings().setRotation(Rotation.valueOf(tag.getString("Rotation"))).setMirror(Mirror.NONE));
            this.rotation = Rotation.valueOf(tag.getString("Rotation"));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("Rotation", this.rotation.name());
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
    }
}
