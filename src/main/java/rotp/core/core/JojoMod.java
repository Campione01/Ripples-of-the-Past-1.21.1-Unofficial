package rotp.core.core;

import org.slf4j.Logger;

import rotp.core.JojoModConfig;
import rotp.core.PacketsRegister;
import rotp.core.command.argument.ModCommandArguments;
import rotp.core.init.ModArmorMaterials;
import rotp.core.init.ModBlockEntities;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModContainers;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModCustomStats;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModEntityCustomEffects;
import rotp.core.init.ModEntityDataSerializers;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModFluids;
import rotp.core.init.ModGamerules;
import rotp.core.init.ModItemDataComponents;
import rotp.core.init.ModItems;
import rotp.core.init.ModLootModifiers;
import rotp.core.init.ModMapDecorationTypes;
import rotp.core.init.ModParticles;
import rotp.core.init.ModPotions;
import rotp.core.init.ModRecipeSerializers;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModSpecialActions;
import rotp.core.init.ModStructures;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.init.power.ModStands;
import rotp.core.mechanics.TempleMapTradeHandler;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// TODO (!) reorganize the packages after merging the two branches (+ don't forget to update the readme)
// XXX allow PowerType to override controls/HUD rendering
@Mod(JojoMod.MOD_ID)
public class JojoMod {
	public static final String MOD_ID = "jojo_ripples";
	@Deprecated
	public static final Logger LOGGER = LogUtils.getLogger();

	public JojoMod(IEventBus modEventBus, ModContainer modContainer) {
		String implementationVersion = JojoMod.class.getPackage()
				.getImplementationVersion();
		LOGGER.info("ROTP build identity: modVersion={}, artifact={}",
				modContainer.getModInfo().getVersion(),
				implementationVersion != null
						? implementationVersion
						: "development");
		modEventBus.register(this);
		modContainer.registerConfig(ModConfig.Type.COMMON, JojoModConfig.COMMON_SPEC);

		ModBlocks.BLOCKS.register(modEventBus);
		ModFluids.FLUIDS.register(modEventBus);
		ModFluids.FLUID_TYPES.register(modEventBus);
		ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
		ModItems.ITEMS.register(modEventBus);
		ModItems.CREATIVE_MODE_TABS.register(modEventBus);
		ModEntityTypes.ENTITY_TYPES.register(modEventBus);
		ModDataAttachmentTypes.ATTACHMENT_TYPES.register(modEventBus);
		ModCommandArguments.ARGUMENT_TYPES.register(modEventBus);
		ModSoundEvents.SOUNDS.register(modEventBus);
		ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
		ModParticles.PARTICLES.register(modEventBus);
		ModItemDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
		ModMapDecorationTypes.MAP_DECORATION_TYPES.register(modEventBus);
		ModEntityAttributes.ATTRIBUTES.register(modEventBus);
		ModEntityDataSerializers.SERIALIZERS.register(modEventBus);
		ModStatusEffects.STATUS_EFFECTS.register(modEventBus);
		ModPotions.POTIONS.register(modEventBus);
		ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
		ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
		ModContainers.CONTAINERS.register(modEventBus);
		ModCriteriaTriggers.TRIGGER_TYPES.register(modEventBus);
		modEventBus.addListener(ModCustomStats::registerCustomStats);
		ModStructures.register(modEventBus);

		ModStandAbilities.ABILITY_TYPES.register(modEventBus);
		ModEntityCustomEffects.CUSTOM_EFFECTS.register(modEventBus);
		ModPlayerPowers.PLAYER_POWERS.register(modEventBus);
		ModHamonSkills.HAMON_SKILLS.register(modEventBus);
		ModHamonSkills.HAMON_CHARACTER_TECHNIQUES.register(modEventBus);
		ModStands.DEFAULT_STANDS.register(modEventBus);
		ModSpecialActions.ACTIONS.register(modEventBus);
	}
	
	public static ResourceLocation resLoc(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
	
	@SubscribeEvent
	private void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			ModGamerules.load();
			TempleMapTradeHandler
					.registerContextualTrades();
		});
	}
	
	@SubscribeEvent
	private void registerNetwork(RegisterPayloadHandlersEvent event) {
		PacketsRegister.register(event);
	}
	
	public static Logger getLogger() {
		return LOGGER;
	}
	
	
	@Deprecated
	public static boolean disableDevStuff() {
		return FMLLoader.isProduction();
	}

}
