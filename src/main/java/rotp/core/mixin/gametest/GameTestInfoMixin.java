package rotp.core.mixin.gametest;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import rotp.core.gametest.GameTestChunkMargin;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

@Mixin(GameTestInfo.class)
public abstract class GameTestInfoMixin {
	// A test starts once these chunks tick their entities. The chunks loaded around the structure are ready a little
	// later than the ones under it, so the test waits for them too.
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/gametest/framework/StructureUtils;getStructureBoundingBox(Lnet/minecraft/world/level/block/entity/StructureBlockEntity;)Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"))
	private BoundingBox jojo_ripples$waitForTheChunksAroundTheTest(StructureBlockEntity structureBlock) {
		return GameTestChunkMargin.around(StructureUtils.getStructureBoundingBox(structureBlock));
	}
}
