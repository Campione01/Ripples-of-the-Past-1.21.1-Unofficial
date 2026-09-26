package rotp.core.worldgen.structure;

import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.ModStructures;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
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
        IntBinaryOperator surface = (x, z) -> context.chunkGenerator().getFirstOccupiedHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        return generationPoint(context, surface);
    }

    // Surface is injectable so gametests can use it on a flat world.
    public static Optional<GenerationStub> generationPoint(GenerationContext context, IntBinaryOperator surface) {
        // 1.16 "Structures Spawn" toggle (common config)
        if (!JojoModConfig.getCommonConfigInstance(false).hamonTempleSpawn.get()) {
            return Optional.empty();
        }
        int centerX = context.chunkPos().getMinBlockX() + 7;
        int centerZ = context.chunkPos().getMinBlockZ() + 7;
        int centerY = surface.applyAsInt(centerX, centerZ);
        if (centerY < 90 || !surfaceBiomeAllowed(context, centerX, centerY, centerZ)) {
            return Optional.empty();
        }
        // Biome is tested at the centre surface (1.16 used the surface biome), not at the sunk origin.
        BlockPos probe = new BlockPos(centerX, centerY, centerZ);
        // /locate checks the stub without building pieces; sample the footprint only when generating.
        return Optional.of(new GenerationStub(probe, builder -> generatePieces(context.structureTemplateManager(),
                footprintAnchor(centerX, centerZ, surface), builder, context.random())));
    }

    // Same test vanilla applies to the stub position afterwards.
    private static boolean surfaceBiomeAllowed(GenerationContext context, int x, int y, int z) {
        return context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), context.randomState().sampler()));
    }

    // 1.16 anchor: chunk corner + 7, null when that column is below 90, else the footprint
    // minimum (each sample floored at 80) sunk 3 blocks.
    public static BlockPos anchor(ChunkPos chunkPos, IntBinaryOperator surfaceHeight) {
        int centerX = chunkPos.getMinBlockX() + 7;
        int centerZ = chunkPos.getMinBlockZ() + 7;
        if (surfaceHeight.applyAsInt(centerX, centerZ) < 90) {
            return null;
        }
        return footprintAnchor(centerX, centerZ, surfaceHeight);
    }

    private static BlockPos footprintAnchor(int centerX, int centerZ, IntBinaryOperator surfaceHeight) {
        int minY = Integer.MAX_VALUE;
        for (int x = centerX - 24; x <= centerX + 24; x += 8) {
            for (int z = centerZ - 24; z <= centerZ + 24; z += 8) {
                minY = Math.min(minY, Math.max(80, surfaceHeight.applyAsInt(x, z)));
            }
        }
        return new BlockPos(centerX, minY - 3, centerZ);
    }

    // Every template the temple places (gametest checks they load).
    public static List<ResourceLocation> templateIds() {
        List<ResourceLocation> ids = new java.util.ArrayList<>(List.of(BUILDING, PATHWAY));
        ids.addAll(List.of(ROCKS));
        return ids;
    }

    public static void generatePieces(StructureTemplateManager templateManager, BlockPos origin, StructurePiecesBuilder builder, RandomSource random) {
        builder.addPiece(new Piece(templateManager, BUILDING.toString(), origin.offset(-24, -3, -24), Rotation.NONE));
        for (Rotation rotation : Rotation.values()) {
            builder.addPiece(new Piece(templateManager, PATHWAY.toString(), origin.offset(-1, -4, -1).offset(new BlockPos(-22, 0, -1).rotate(rotation)), rotation));
            ResourceLocation rock = ROCKS[random.nextInt(ROCKS.length)];
            // 1.16: rock sits at (-4,-3,-4) + R(-21,0,0), centred on the pathway's outer end.
            builder.addPiece(new Piece(templateManager, rock.toString(), origin.offset(-4, -3, -4).offset(new BlockPos(-21, 0, 0).rotate(rotation)), Rotation.NONE));
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

        public String templateId() {
            return templateName;
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
