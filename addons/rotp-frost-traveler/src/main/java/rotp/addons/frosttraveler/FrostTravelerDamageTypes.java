package rotp.addons.frosttraveler;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class FrostTravelerDamageTypes {
    public static final ResourceKey<DamageType> CS_ROTTEN =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    FrostTravelerAddon.id("cs_rotten"));

    private FrostTravelerDamageTypes() {}
}
