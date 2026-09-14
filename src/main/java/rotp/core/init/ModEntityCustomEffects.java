package rotp.core.init;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.mechanics.standarrow.StandVirusActualEffect;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntityCustomEffects {
	public static final DeferredRegister<EntityCustomEffectType<?>> CUSTOM_EFFECTS = DeferredRegister.create(JojoRegistries.ENTITY_CUSTOM_EFFECTS_REG, JojoMod.MOD_ID);


	public static final DeferredHolder<EntityCustomEffectType<?>, EntityCustomEffectType<StandVirusActualEffect>> STAND_VIRUS = CUSTOM_EFFECTS.register(
			"stand_virus", key -> new EntityCustomEffectType<>(key, StandVirusActualEffect::new));
}
