package rotp.core.subsystems.itemtracking;

import java.util.List;
import java.util.function.BiConsumer;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ClientPowerCache;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.powersystem.PowerClass;
import rotp.core.subsystems.itemtracking.ItemTrackDebugMarker.ItemMarkerInstance;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class OriginalItemPosMarker extends MarkerRenderer {

	public OriginalItemPosMarker(Minecraft mc) {
		super((String) null, mc);
		renderThroughBlocks = true;
	}

	@Override
	protected boolean shouldRender() {
		return true;
	}

	@Override
	protected void renderIcon(PoseStack poseStack, MarkerInstance marker, float partialTick, StandSkin standSkin) {
		ItemStack item = ((ItemMarkerInstance) marker).item;
		if (item != null && !item.isEmpty()) {
			renderItem(poseStack, item, partialTick);
		}
	}

	@Override
	protected void updatePositions(List<MarkerInstance> list, float partialTick) {
		// outlined: the item Crazy Diamond's block anchor would move (1.16)
		OriginalItemPositions.forHeldItems(ClientPowerCache.getPower(PowerClass.STAND),
				(blockPos, item, usedAsAnchor) -> list.add(new ItemMarkerInstance(blockMarkerPos(blockPos), usedAsAnchor, item)),
				mc.player, ClientGlobals.playerStandEntity);
	}

	public static void iterateHeldItemsOriginalPos(BiConsumer<BlockPos, ItemStack> action) {
		 forEntityHeldItem(action, Minecraft.getInstance().player);
		 forEntityHeldItem(action, ClientGlobals.playerStandEntity);
	}

	public static void forEntityHeldItem(BiConsumer<BlockPos, ItemStack> action, LivingEntity entity) {
		OriginalItemPositions.forHeldItems(null, (blockPos, item, usedAsAnchor) -> action.accept(blockPos, item), entity);
	}

}
