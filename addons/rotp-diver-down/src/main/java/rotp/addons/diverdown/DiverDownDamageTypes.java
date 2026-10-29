package rotp.addons.diverdown;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class DiverDownDamageTypes {
    public static final ResourceKey<DamageType> PROTECTION_FEEDBACK =
            key("protection_feedback");
    public static final ResourceKey<DamageType> KINETIC_TRAP =
            key("kinetic_trap");
    public static final ResourceKey<DamageType> DISASSEMBLE =
            key("disassemble");

    private DiverDownDamageTypes() {}

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(
                Registries.DAMAGE_TYPE, DiverDownAddon.id(path));
    }
}
