package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.worldgen.structure.HamonTempleStructure;
import com.github.standobyte.jojo.worldgen.structure.PillarmanTempleStructure;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(BuiltInRegistries.STRUCTURE_TYPE, JojoMod.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(BuiltInRegistries.STRUCTURE_PIECE, JojoMod.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<HamonTempleStructure>> HAMON_TEMPLE = STRUCTURE_TYPES.register("hamon_temple", () -> () -> HamonTempleStructure.CODEC);
    public static final DeferredHolder<StructureType<?>, StructureType<PillarmanTempleStructure>> PILLARMAN_TEMPLE = STRUCTURE_TYPES.register("pillarman_temple", () -> () -> PillarmanTempleStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType.StructureTemplateType> HAMON_TEMPLE_PIECE = STRUCTURE_PIECES.register("hamon_temple_piece", () -> HamonTempleStructure.Piece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType.StructureTemplateType> PILLARMAN_TEMPLE_PIECE = STRUCTURE_PIECES.register("pillarman_temple_piece", () -> PillarmanTempleStructure.Piece::new);

    private ModStructures() {}

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECES.register(modEventBus);
    }
}
