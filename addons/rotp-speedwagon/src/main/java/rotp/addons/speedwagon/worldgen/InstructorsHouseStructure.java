package rotp.addons.speedwagon.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import rotp.addons.speedwagon.SpeedwagonAddon;
import rotp.addons.speedwagon.SpeedwagonConfig;
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

public final class InstructorsHouseStructure extends Structure {
    public static final MapCodec<InstructorsHouseStructure> CODEC =
            simpleCodec(InstructorsHouseStructure::new);
    private static final ResourceLocation BUILDING =
            SpeedwagonAddon.id(
                    "instructors_house/instructors_house");

    public InstructorsHouseStructure(
            StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(
            GenerationContext context) {
        if (!SpeedwagonConfig.INSTRUCTORS_HOUSE_SPAWN.get()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMinBlockX() + 7;
        int z = chunk.getMinBlockZ() + 7;
        int surface = context.chunkGenerator()
                .getFirstOccupiedHeight(
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(),
                        context.randomState());
        BlockPos origin = new BlockPos(x, surface - 2, z);
        return Optional.of(new GenerationStub(
                origin,
                builder -> generatePiece(
                        context.structureTemplateManager(),
                        origin,
                        builder,
                        context.random())));
    }

    private static void generatePiece(
            StructureTemplateManager manager,
            BlockPos origin,
            StructurePiecesBuilder builder,
            RandomSource random) {
        Rotation rotation = Rotation.values()[
                random.nextInt(Rotation.values().length)];
        builder.addPiece(new Piece(
                manager,
                BUILDING.toString(),
                origin,
                rotation));
    }

    @Override
    public StructureType<?> type() {
        return SpeedwagonAddon.INSTRUCTORS_HOUSE.get();
    }

    public static final class Piece
            extends TemplateStructurePiece {
        private final Rotation rotation;

        public Piece(
                StructureTemplateManager manager,
                String templateName,
                BlockPos templatePosition,
                Rotation rotation) {
            super(
                    SpeedwagonAddon
                            .INSTRUCTORS_HOUSE_PIECE.get(),
                    0,
                    manager,
                    ResourceLocation.parse(templateName),
                    templateName,
                    settings(rotation),
                    templatePosition);
            this.rotation = rotation;
        }

        public Piece(
                StructureTemplateManager manager,
                CompoundTag tag) {
            super(
                    SpeedwagonAddon
                            .INSTRUCTORS_HOUSE_PIECE.get(),
                    tag,
                    manager,
                    location -> settings(readRotation(tag)));
            this.rotation = readRotation(tag);
        }

        @Override
        protected void addAdditionalSaveData(
                StructurePieceSerializationContext context,
                CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("Rotation", rotation.name());
        }

        @Override
        protected void handleDataMarker(
                String name,
                BlockPos pos,
                ServerLevelAccessor level,
                RandomSource random,
                BoundingBox box) {}

        private static StructurePlaceSettings settings(
                Rotation rotation) {
            return new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setMirror(Mirror.NONE);
        }

        private static Rotation readRotation(
                CompoundTag tag) {
            try {
                return Rotation.valueOf(
                        tag.getString("Rotation"));
            }
            catch (IllegalArgumentException ignored) {
                return Rotation.NONE;
            }
        }
    }
}
