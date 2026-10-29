package rotp.addons.greenday;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class GreenDayDamageTypes {
    public static final ResourceKey<DamageType> MOLD =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    GreenDayAddon.id("mold"));

    private GreenDayDamageTypes() {}
}
