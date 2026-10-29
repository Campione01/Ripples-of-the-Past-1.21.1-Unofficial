package rotp.addons.cmoon;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class CMoonDamageTypes {
    public static final ResourceKey<DamageType> INVERSION =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    CMoonAddon.id("inversion"));

    private CMoonDamageTypes() {}
}
