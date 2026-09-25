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

/**
 * 1.16 MeteoriteStructure + MeteoritePieces: a crater, a trail and a body
 * (meteoric iron around the meteorite core), one random rotation for all three.
 */
public class MeteoriteStructure extends Structure {
    public static final MapCodec<MeteoriteStructure> CODEC = simpleCodec(MeteoriteStructure::new);

    public static final ResourceLocation[] CRATERS = {
            JojoMod.resLoc("meteorite/crater_1"),
            JojoMod.resLoc("meteorite/crater_2"),
            JojoMod.resLoc("meteorite/crater_3"),
            JojoMod.resLoc("meteorite/crater_4"),
            JojoMod.resLoc("meteorite/crater_5") };
    public static final ResourceLocation[] BODIES = {
            JojoMod.resLoc("meteorite/body_1"),
            JojoMod.resLoc("meteorite/body_2"),
            JojoMod.resLoc("meteorite/body_3"),
            JojoMod.resLoc("meteorite/body_4"),
            JojoMod.resLoc("meteorite/body_5"),
            JojoMod.resLoc("meteorite/body_6"),
            JojoMod.resLoc("meteorite/body_7"),
            JojoMod.resLoc("meteorite/body_8") };
    public static final ResourceLocation TRAIL = JojoMod.resLoc("meteorite/trail");

    public MeteoriteStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return generationPoint(context, (x, z) -> context.chunkGenerator().getFirstOccupiedHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
    }

    // Surface is injectable so gametests can use it on a flat world.
    public static Optional<GenerationStub> generationPoint(GenerationContext context, IntBinaryOperator surface) {
        // 1.16 "Structures Spawn" toggle (common config)
        if (!JojoModConfig.getCommonConfigInstance(false).meteoriteSpawn.get()) {
            return Optional.empty();
        }
        ChunkPos chunkPos = context.chunkPos();
        // 1.16: (chunk << 4) + 7, one block below the first occupied height
        int x = chunkPos.getBlockX(7);
        int z = chunkPos.getBlockZ(7);
        int y = surface.applyAsInt(x, z) - 1;
        BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin, builder -> generatePieces(context.structureTemplateManager(), origin, builder, context.random())));
    }

    public static void generatePieces(StructureTemplateManager templateManager, BlockPos origin, StructurePiecesBuilder builder, RandomSource random) {
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        builder.addPiece(new Piece(templateManager, CRATERS[random.nextInt(CRATERS.length)],
                origin.offset(new BlockPos(0, -2, 0).rotate(rotation)), rotation));
        builder.addPiece(new Piece(templateManager, TRAIL,
                origin.offset(new BlockPos(0, 1, 0).rotate(rotation)), rotation));
        builder.addPiece(new Piece(templateManager, BODIES[random.nextInt(BODIES.length)],
                origin.offset(new BlockPos(2, -3, 2).rotate(rotation)), rotation));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.METEORITE.get();
    }

    public static class Piece extends TemplateStructurePiece {
        private final Rotation rotation;

        public Piece(StructureTemplateManager manager, ResourceLocation template, BlockPos templatePosition, Rotation rotation) {
            super(ModStructures.METEORITE_PIECE.get(), 0, manager, template, template.toString(),
                    new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE), templatePosition);
            this.rotation = rotation;
        }

        public Piece(StructureTemplateManager manager, CompoundTag tag) {
            super(ModStructures.METEORITE_PIECE.get(), tag, manager,
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
