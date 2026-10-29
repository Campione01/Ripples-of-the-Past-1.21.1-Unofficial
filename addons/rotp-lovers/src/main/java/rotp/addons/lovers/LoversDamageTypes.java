package rotp.addons.lovers;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class LoversDamageTypes {
    public static final ResourceKey<DamageType> LOVERS_FEEDBACK =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    LoversAddon.id("lovers_feedback"));

    private LoversDamageTypes() {}
}
