package rotp.addons.weatherreport;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class WeatherReportDamageTypes {
    public static final ResourceKey<DamageType> BLEEDING =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    WeatherReportAddon.id("bleeding"));
    public static final ResourceKey<DamageType> SPIKES =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    WeatherReportAddon.id("spikes"));

    private WeatherReportDamageTypes() {}
}
