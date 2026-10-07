package rotp.core.mixin.gametest;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import rotp.core.gametest.GameTestChunkMargin;

import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

@Mixin(StructureUtils.class)
public abstract class StructureUtilsMixin {
	@ModifyVariable(method = "forceLoadChunks", at = @At("HEAD"), argsOnly = true)
	private static BoundingBox jojo_ripples$loadTheChunksAroundTheTest(BoundingBox structureBounds) {
		return GameTestChunkMargin.around(structureBounds);
	}
}
