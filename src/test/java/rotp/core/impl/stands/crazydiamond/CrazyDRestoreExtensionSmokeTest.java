package rotp.core.impl.stands.crazydiamond;

import rotp.core.init.ModBlockTags;

import net.minecraft.resources.ResourceLocation;

public final class CrazyDRestoreExtensionSmokeTest {
	private CrazyDRestoreExtensionSmokeTest() {}

	public static void run() {
		ResourceLocation tagId = ModBlockTags.CRAZY_D_CANNOT_RESTORE.location();
		if (!"jojo_ripples".equals(tagId.getNamespace())
				|| !"crazy_d_cannot_restore".equals(tagId.getPath())) {
			throw new AssertionError(
					"Crazy Diamond restoration exclusion tag ID drifted");
		}
	}
}
