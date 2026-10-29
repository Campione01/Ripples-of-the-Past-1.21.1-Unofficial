package rotp.addons.thelock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class TheLockDamageTypes {
    public static final ResourceKey<DamageType> SELF_HARM_LOCK =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    TheLockAddon.id("self_harm_lock"));

    private TheLockDamageTypes() {}
}
