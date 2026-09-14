package rotp.core.api.rps;

import net.minecraft.resources.ResourceLocation;

public record RpsCheatRegistration(
		ResourceLocation owner,
		RpsCheatSpec spec) {}
