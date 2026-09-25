package rotp.core.worldgen.structure;

import java.util.Optional;
import java.util.function.IntBinaryOperator;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class PillarmanTempleStructure extends Structure {
    public static final MapCodec<PillarmanTempleStructure> CODEC = simpleCodec(PillarmanTempleStructure::new);

    private static final ResourceLocation BUILDING = JojoMod.resLoc("pillarman_temple/building");
    private static final ResourceLocation STAIRWAY = JojoMod.resLoc("pillarman_temple/stairway");
    private static final ResourceLocation CORRIDOR = JojoMod.resLoc("pillarman_temple/corridor");
    private static final ResourceLocation BOSSROOM = JojoMod.resLoc("pillarman_temple/bossroom");

    public PillarmanTempleStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        IntBinaryOperator surface = (x, z) -> context.chunkGenerator().getFirstOccupiedHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        return generationPoint(context, surface);
    }

    // Surface is injectable so gametests can use it on a flat world.
    public static Optional<GenerationStub> generationPoint(GenerationContext context, IntBinaryOperator surface) {
        // 1.16 "Structures Spawn" toggle (common config)
        if (!JojoModConfig.getCommonConfigInstance(false).pillarManTempleSpawn.get()) {
            return Optional.empty();
        }
        BlockPos origin = anchor(context.chunkPos(), surface);
        // Biome is tested at the centre surface (1.16 used the surface biome), not at the sunk origin.
        BlockPos probe = new BlockPos(origin.getX(), surface.applyAsInt(origin.getX(), origin.getZ()), origin.getZ());
        return Optional.of(new GenerationStub(probe, builder -> generatePieces(context.structureTemplateManager(), origin, builder, context.random())));
    }

    // 1.16 anchor: chunk corner + 7, lowest surface over the footprint, sunk 3 blocks.
    public static BlockPos anchor(ChunkPos chunkPos, IntBinaryOperator surfaceHeight) {
        int centerX = chunkPos.getMinBlockX() + 7;
        int centerZ = chunkPos.getMinBlockZ() + 7;
        int minY = Integer.MAX_VALUE;
        for (int x = centerX - 26; x < centerX + 30; x += 8) {
            for (int z = centerZ - 26; z < centerZ + 30; z += 8) {
                minY = Math.min(minY, surfaceHeight.applyAsInt(x, z));
            }
        }
        return new BlockPos(centerX, minY - 3, centerZ);
    }

    // Every template the temple places (gametest checks they load).
    public static java.util.List<ResourceLocation> templateIds() {
        return java.util.List.of(BUILDING, STAIRWAY, CORRIDOR, BOSSROOM);
    }

    private static void generatePieces(StructureTemplateManager manager, BlockPos origin, StructurePiecesBuilder builder, RandomSource random) {
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        builder.addPiece(new Piece(manager, BUILDING.toString(), origin.offset(new BlockPos(-27, 0, -27).rotate(rotation)), rotation));
        builder.addPiece(new Piece(manager, STAIRWAY.toString(), origin.offset(new BlockPos(19, -42, -2).rotate(rotation)), rotation));
        builder.addPiece(new Piece(manager, CORRIDOR.toString(), origin.offset(new BlockPos(61, -43, -3).rotate(rotation)), rotation));
        builder.addPiece(new Piece(manager, BOSSROOM.toString(), origin.offset(new BlockPos(102, -45, -16).rotate(rotation)), rotation));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.PILLARMAN_TEMPLE.get();
    }

    public static class Piece extends TemplateStructurePiece {
        private final Rotation rotation;

        public Piece(StructureTemplateManager manager, String templateName, BlockPos templatePosition, Rotation rotation) {
            super(ModStructures.PILLARMAN_TEMPLE_PIECE.get(), 0, manager, ResourceLocation.parse(templateName), templateName,
                    new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE), templatePosition);
            this.rotation = rotation;
        }

        public Piece(StructureTemplateManager manager, CompoundTag tag) {
            super(ModStructures.PILLARMAN_TEMPLE_PIECE.get(), tag, manager,
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
