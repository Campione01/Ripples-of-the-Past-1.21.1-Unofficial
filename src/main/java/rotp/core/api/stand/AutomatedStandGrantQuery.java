package rotp.core.api.stand;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record AutomatedStandGrantQuery(
		ResourceLocation source,
		ServerPlayer player) {}
