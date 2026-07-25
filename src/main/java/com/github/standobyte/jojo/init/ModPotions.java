package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class ModPotions {
	public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, JojoMod.MOD_ID);
	
	public static final DeferredHolder<Potion, Potion> FREEZE_POTION = POTIONS.register("freeze",
			() -> new Potion(new MobEffectInstance(ModStatusEffects.FREEZE, 900, 0)));
	
	public static final DeferredHolder<Potion, Potion> FREEZE_LONG_POTION = POTIONS.register("long_freeze",
			() -> new Potion(new MobEffectInstance(ModStatusEffects.FREEZE, 2400, 0)));
	
	public static final DeferredHolder<Potion, Potion> FREEZE_STRONG_POTION = POTIONS.register("strong_freeze",
			() -> new Potion(new MobEffectInstance(ModStatusEffects.FREEZE, 450, 1)));
	
	@SubscribeEvent
	public static void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
		event.getBuilder().addMix(Potions.AWKWARD, Items.BLUE_ICE, FREEZE_POTION);
		event.getBuilder().addMix(FREEZE_POTION, Items.REDSTONE, FREEZE_LONG_POTION);
		event.getBuilder().addMix(FREEZE_POTION, Items.GLOWSTONE_DUST, FREEZE_STRONG_POTION);
	}
}
