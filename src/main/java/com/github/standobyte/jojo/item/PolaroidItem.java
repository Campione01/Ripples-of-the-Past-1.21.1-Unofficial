package com.github.standobyte.jojo.item;

import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.github.standobyte.jojo.client.polaroid.PolaroidHelper;
import com.github.standobyte.jojo.item.polaroid.PhotosHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PolaroidItem extends Item {
	public PolaroidItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player cameraPlayer, InteractionHand hand) {
		ItemStack stack = cameraPlayer.getItemInHand(hand);

		if (!cameraPlayer.getAbilities().instabuild) {
			ItemStack paperItem = findPaper(cameraPlayer);
			if (paperItem.isEmpty()) {
				if (!level.isClientSide() && cameraPlayer instanceof ServerPlayer serverPlayer) {
					serverPlayer.displayClientMessage(Component.translatable("jojo.polaroid.paper"), true);
				}
				return InteractionResultHolder.fail(stack);
			}
			if (!level.isClientSide()) {
				paperItem.shrink(1);
			}
		}

		if (level.isClientSide()) {
			if (getHandSide(cameraPlayer, hand) == HumanoidArm.RIGHT) {
				PolaroidHelper.takePicture(null, null, false, cameraPlayer.getId());
			}
			else {
				float xRot = Math.max(-cameraPlayer.getXRot(), -82.5F);
				float yRot = cameraPlayer.getYRot();
				float xRotAmount = Math.abs(xRot / 90.0F);
				cameraPlayer.setYBodyRot(yRot + 45.0F * (1.0F - xRotAmount));
				cameraPlayer.yBodyRotO = cameraPlayer.yBodyRot;
				Vec3 lookVec = cameraPlayer.getLookAngle();
				Vec3 headUpVec = lookVec.xRot(90.0F);
				Vec3 cameraPos = new Vec3(cameraPlayer.getX(), cameraPlayer.getEyeY() - 0.1D, cameraPlayer.getZ());
				cameraPos = cameraPos.add(headUpVec.scale(xRotAmount * 0.2D));
				cameraPos = cameraPos.add(lookVec.scale(1.0D - xRotAmount * 0.2D));
				PolaroidHelper.takePicture(cameraPos,
						angle -> new Vector3f(xRot, yRot - 165.0F, 0.0F), false, cameraPlayer.getId());
			}
		}
		else {
			cameraPlayer.getCooldowns().addCooldown(this, 60);
			if (cameraPlayer instanceof ServerPlayer serverPlayer) {
				PhotosHandler.get(serverPlayer.server).authorizePhotoUpload(serverPlayer);
			}
		}

		return InteractionResultHolder.consume(stack);
	}

	private static ItemStack findPaper(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack item = player.getInventory().getItem(i);
			if (!item.isEmpty() && item.is(Items.PAPER)) {
				return item;
			}
		}
		return ItemStack.EMPTY;
	}

	private static HumanoidArm getHandSide(Player player, InteractionHand hand) {
		HumanoidArm mainArm = player.getMainArm();
		return hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.jojo_ripples.polaroid.reference_quote")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
