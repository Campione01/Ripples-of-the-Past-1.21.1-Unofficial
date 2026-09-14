package rotp.core.mechanics.clothes.client.layer;

import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.ModelUtil;
import rotp.core.mechanics.clothes.EntityClothesInventory;
import rotp.core.mechanics.clothes.itemdata.ClothesSlotType;
import rotp.core.util.functions.EnumUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class HumanoidClothesRSExtension {
	public boolean hasClothesComponent;
	public final Map<ClothesSlotType, ItemStack> items = EnumUtil.makeEnumMap(ClothesSlotType.class, slot -> ItemStack.EMPTY);
	public boolean slimModel;
	
	public final boolean extract(LivingEntity entity) {
		EntityClothesInventory entityClothes = EntityClothesInventory.getExisting(entity);
		hasClothesComponent = entityClothes != null;
		if (!hasClothesComponent) return false;
		
		for (ClothesSlotType slot : ClothesSlotType.values()) {
			items.put(slot, entityClothes.getClothingPiece(slot));
		}
		slimModel = ModelUtil.isSlimModel(entity);
		return true;
	}
	
	public static final HumanoidClothesRSExtension reusedInstance = new HumanoidClothesRSExtension();
	
	@Nullable 
	public static HumanoidClothesRSExtension getCurRenderData() {
		return reusedInstance.hasClothesComponent ? reusedInstance : null;
	}
	
}