package rotp.addons.wonderofu;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class WonderOfUDamageTypes {
    public static final ResourceKey<DamageType> CALAMITY_REFLECTION =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    WonderOfUAddon.id("calamity_reflection"));
    public static final ResourceKey<DamageType> CALAMITY_ACTIVE =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    WonderOfUAddon.id("calamity_active"));
    public static final ResourceKey<DamageType> CALAMITY_CAR =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    WonderOfUAddon.id("calamity_car"));

    private WonderOfUDamageTypes() {}
}
