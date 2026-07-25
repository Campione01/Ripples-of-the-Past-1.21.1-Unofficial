package com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

@Mixin(Gui.class)
public interface GuiAccessor {
	@Accessor("tickCount") int getTickCount();
	@Accessor("random") RandomSource getRandom();

	@Accessor("ARMOR_FULL_SPRITE") public static ResourceLocation getARMOR_FULL_SPRITE() { throw new AssertionError(); }
	@Accessor("ARMOR_HALF_SPRITE") public static ResourceLocation getARMOR_HALF_SPRITE() { throw new AssertionError(); }
	@Accessor("ARMOR_EMPTY_SPRITE") public static ResourceLocation getARMOR_EMPTY_SPRITE() { throw new AssertionError(); }
	@Accessor("HOTBAR_SPRITE") public static ResourceLocation getHOTBAR_SPRITE() { throw new AssertionError(); }
	@Accessor("HOTBAR_SELECTION_SPRITE") public static ResourceLocation getHOTBAR_SELECTION_SPRITE() { throw new AssertionError(); }
	@Accessor("HOTBAR_OFFHAND_LEFT_SPRITE") public static ResourceLocation getHOTBAR_OFFHAND_LEFT_SPRITE() { throw new AssertionError(); }
	@Accessor("HOTBAR_OFFHAND_RIGHT_SPRITE") public static ResourceLocation getHOTBAR_OFFHAND_RIGHT_SPRITE() { throw new AssertionError(); }
	@Accessor("EXPERIENCE_BAR_BACKGROUND_SPRITE") public static ResourceLocation getEXPERIENCE_BAR_BACKGROUND_SPRITE() { throw new AssertionError(); }
	@Accessor("EXPERIENCE_BAR_PROGRESS_SPRITE") public static ResourceLocation getEXPERIENCE_BAR_PROGRESS_SPRITE() { throw new AssertionError(); }
	@Accessor("EFFECT_BACKGROUND_AMBIENT_SPRITE") public static ResourceLocation getEFFECT_BACKGROUND_AMBIENT_SPRITE() { throw new AssertionError(); }
	@Accessor("EFFECT_BACKGROUND_SPRITE") public static ResourceLocation getEFFECT_BACKGROUND_SPRITE() { throw new AssertionError(); }
	@Accessor("AIR_SPRITE") public static ResourceLocation getAIR_SPRITE() { throw new AssertionError(); }
	@Accessor("AIR_BURSTING_SPRITE") public static ResourceLocation getAIR_BURSTING_SPRITE() { throw new AssertionError(); }
	@Accessor("HEART_VEHICLE_CONTAINER_SPRITE") public static ResourceLocation getHEART_VEHICLE_CONTAINER_SPRITE() { throw new AssertionError(); }
	@Accessor("HEART_VEHICLE_FULL_SPRITE") public static ResourceLocation getHEART_VEHICLE_FULL_SPRITE() { throw new AssertionError(); }
	@Accessor("HEART_VEHICLE_HALF_SPRITE") public static ResourceLocation getHEART_VEHICLE_HALF_SPRITE() { throw new AssertionError(); }

	@Invoker("renderHeart") void invokeRenderHeart(GuiGraphics guiGraphics, Gui.HeartType heartType, int x, int y, boolean hardcore, boolean halfHeart, boolean blinking);
	@Invoker("renderFood") void invokeRenderFood(GuiGraphics guiGraphics, Player player, int y, int x);
//	@Invoker("renderAirBubbles") void invokeRenderAirBubbles(GuiGraphics guiGraphics, Player player, int vehicleMaxHealth, int y, int x);
}
