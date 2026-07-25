package com.github.standobyte.jojo.item;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.item.cassette.CassetteData;
import com.github.standobyte.jojo.item.cassette.CassetteSide;
import com.github.standobyte.jojo.item.cassette.CassetteTrackSource;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class CassetteRecordedItem extends Item {
	public CassetteRecordedItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		CassetteData cassette = getOrBroken(stack);
		if (cassette.isBroken()) {
			tooltip.add(Component.translatable("jojo_ripples.cassette.bad_recording").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return;
		}

		int lastSideATrack = (cassette.tracks().size() - 1) / 2;
		addSideTooltip(tooltip, context, CassetteSide.SIDE_A, cassette.tracks().subList(0, lastSideATrack + 1));
		addSideTooltip(tooltip, context, CassetteSide.SIDE_B, cassette.tracks().subList(lastSideATrack + 1, cassette.tracks().size()));

		if (cassette.dyeCraftHint()) {
			cassette.dye().ifPresent(dye -> tooltip.add(Component.translatable("item.jojo_ripples.cassette.dye_hint",
					Component.translatable(ModItems.CASSETTE_BLANK.get().getDescriptionId()),
					Component.translatable(DyeItem.byColor(dye).getDescriptionId())).withStyle(ChatFormatting.GRAY)));
		}

		tooltip.add(Component.translatable("jojo_ripples.cassette.generation." + Math.min(cassette.generation(), 2))
				.withStyle(ChatFormatting.GRAY));
	}

	private static void addSideTooltip(List<Component> tooltip, Item.TooltipContext context, CassetteSide side, List<CassetteTrackSource> tracks) {
		if (!tracks.isEmpty()) {
			tooltip.add(Component.translatable("jojo_ripples.cassette." + side.getSerializedName())
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			tracks.forEach(track -> tooltip.add(track.displayName(context.registries()).copy().withStyle(ChatFormatting.GRAY)));
			tooltip.add(Component.empty());
		}
	}

	public static ItemStack withDyeRecording(DyeColor dye) {
		ItemStack cassette = new ItemStack(ModItems.CASSETTE_RECORDED.get());
		CassetteTrackSource source = new CassetteTrackSource(CassetteTrackSource.Type.DYE_COLOR,
				com.github.standobyte.jojo.core.JojoMod.resLoc("empty"), dye.getId());
		cassette.set(ModItemDataComponents.CASSETTE_DATA.get(),
				new CassetteData(List.of(source), 0, Optional.of(dye), true, CassetteSide.SIDE_A, 0));
		return cassette;
	}

	public static Optional<CassetteData> getCassetteData(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(stack.get(ModItemDataComponents.CASSETTE_DATA.get()));
	}

	public static CassetteData getOrBroken(ItemStack stack) {
		return getCassetteData(stack).orElse(CassetteData.BROKEN);
	}

	public static void editCassetteData(ItemStack stack, UnaryOperator<CassetteData> action) {
		if (!stack.isEmpty()) {
			stack.set(ModItemDataComponents.CASSETTE_DATA.get(), action.apply(getOrBroken(stack)));
		}
	}
}
