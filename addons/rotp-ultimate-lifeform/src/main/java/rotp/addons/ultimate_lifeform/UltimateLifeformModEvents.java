package rotp.addons.ultimate_lifeform;

import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class UltimateLifeformModEvents {
    private UltimateLifeformModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                UltimateLifeformAddon.ULTIMATE_RABBIT.get(),
                ultimateSummonAttributes());
        event.put(
                UltimateLifeformAddon.ULTIMATE_PHANTOM.get(),
                ultimateSummonAttributes());
    }

    private static net.minecraft.world.entity.ai.attributes
            .AttributeSupplier ultimateSummonAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .build();
    }

    // 1.16 InitEffects.afterEffectsRegister (setEffectAsTracked): clients
    // tracking an entity learn these effects of it
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModStatusEffects.TRACKED_EFFECTS.add(
                    UltimateLifeformAddon.ULTIMATE_ARMOR);
            ModStatusEffects.TRACKED_EFFECTS.add(
                    UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR);
            ModStatusEffects.TRACKED_EFFECTS.add(
                    UltimateLifeformAddon.ULTIMATE_HEALTH_BOOST);
            ModStatusEffects.TRACKED_EFFECTS.add(
                    UltimateLifeformAddon.HAMON_SPREAD);
        });
    }

    public static void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != ModItems.MAIN_TAB.getKey()) {
            return;
        }
        event.accept(
                UltimateLifeformAddon.STONE_MASK_UL_NOSTONE.get());
        event.accept(
                UltimateLifeformAddon.STONE_MASK_UL_WITH_STONE.get());
        event.accept(UltimateLifeformAddon.SOUL_ESSENCE.get());
        event.accept(UltimateLifeformAddon.FIRE_ESSENCE.get());
        event.accept(UltimateLifeformAddon.EARTH_ESSENCE.get());
        event.accept(UltimateLifeformAddon.WATER_ESSENCE.get());
        event.accept(UltimateLifeformAddon.LIFE_ESSENCE.get());
        event.accept(UltimateLifeformAddon.DEATH_ESSENCE.get());
        event.accept(UltimateLifeformAddon.SUN_ESSENCE.get());
    }
}
