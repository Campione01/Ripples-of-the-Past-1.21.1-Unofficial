package com.github.standobyte.jojo.modcompat;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.neoforged.fml.ModList;

public class ModInteractionUtil {
	private static Map<String, Boolean> checked = new HashMap<>();
	
	public static boolean isModLoaded(String modId) {
		return checked.computeIfAbsent(modId, ModList.get()::isLoaded);
	}

	private static final ResourceLocation MUTANT_ENDERMAN_ID =
			ResourceLocation.fromNamespaceAndPath("mutantbeasts", "mutant_enderman");
	private static final ResourceLocation MUTANT_ENDERMAN_ID_2 =
			ResourceLocation.fromNamespaceAndPath("mutantbeasts", "endersoul_clone");
	private static final ResourceLocation MUTANT_ENDERMAN_ID_3 =
			ResourceLocation.fromNamespaceAndPath("mutantbeasts", "endersoul_fragment");

	public static boolean isEntityEnderman(Entity entity) {
		if (entity == null) {
			return false;
		}
		if (entity instanceof EnderMan) {
			return true;
		}
		EntityType<?> type = entity.getType();
		ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return MUTANT_ENDERMAN_ID.equals(typeId)
				|| MUTANT_ENDERMAN_ID_2.equals(typeId)
				|| MUTANT_ENDERMAN_ID_3.equals(typeId);
	}
	
	public static void clientTickPre() {
		JojoModsInteraction.WingsOfRequiem._cacheWoRClientPlayerData();
	}
}
