package rotp.addons.whitesnake.client;

import java.util.Optional;

import rotp.core.client.entityrender.stand.StandEntityRenderState;

import net.minecraft.resources.ResourceLocation;

public final class WhitesnakeRenderState
        extends StandEntityRenderState {
    public Optional<ResourceLocation> disguiseType = Optional.empty();
    public int disguiseChangedTick;
}
