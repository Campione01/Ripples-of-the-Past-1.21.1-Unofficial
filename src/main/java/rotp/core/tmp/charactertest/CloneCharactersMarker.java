package rotp.core.tmp.charactertest;

import java.util.List;

import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.init.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class CloneCharactersMarker extends MarkerRenderer {

	public CloneCharactersMarker(Minecraft mc) {
		super(0xFF60F5FC, null, mc);
	}

	@Override
	protected boolean shouldRender() {
		if (mc.player == null) return false;
		ItemStack heldItem = mc.player.getMainHandItem();
		return !heldItem.isEmpty() && heldItem.getItem() == ModItems.CHARACTER_TEST.get();
	}

	@Override
	protected void updatePositions(List<MarkerInstance> list, float partialTick) {
		List<Entity> clones = CharacterTestItem.availableCharacters(mc.player);
		if (!clones.isEmpty()) {
			Entity hovered = CharacterTestItem.getHovered(mc.player, clones);
			for (Entity entity : clones) {
				list.add(new MarkerInstance(entity.getPosition(partialTick).add(0, entity.getBbHeight() + 0.6, 0), 
						!mc.player.isShiftKeyDown() && entity == hovered));
			}
		}
	}

}
