package rotp.core.subsystems.itemtracking;

import javax.annotation.Nullable;

import rotp.core.impl.stands.crazydiamond.CrazyDAnchorBlockAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorBlockAbility.FoundAnchor;
import rotp.core.init.ModItemDataComponents;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Server-safe walk over held items that remember their original block position. */
public final class OriginalItemPositions {
	private OriginalItemPositions() {}

	@FunctionalInterface
	public interface Visitor {
		void accept(BlockPos blockPos, ItemStack item, boolean usedAsAnchor);
	}

	/**
	 * Visits every held item of the holders whose original pos is in the holder's dimension.
	 * usedAsAnchor flags the item Crazy Diamond's block anchor would move (1.16 outlined it).
	 */
	public static void forHeldItems(@Nullable StandPower stand, Visitor action, LivingEntity... holders) {
		ItemStack anchorItem = anchorItem(stand);
		for (LivingEntity holder : holders) {
			if (holder == null) continue;
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack item = holder.getItemInHand(hand);
				if (item.isEmpty()) continue;
				OriginalItemPosComponent originalPos = item.get(ModItemDataComponents.ORIGINAL_POS);
				if (originalPos != null && originalPos.matchesDimension(holder.level())) {
					action.accept(originalPos.blockPos(), item, item == anchorItem);
				}
			}
		}
	}

	// only Crazy Diamond can move a block back with its anchor
	@Nullable
	private static ItemStack anchorItem(@Nullable StandPower stand) {
		if (stand == null || stand.getPowerType() != ModStands.CRAZY_DIAMOND.get()) return null;
		FoundAnchor anchor = CrazyDAnchorBlockAbility.getItemToUseAsAnchor(stand);
		return anchor != null ? anchor.item() : null;
	}
}
