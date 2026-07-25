package com.github.standobyte.jojo.client.ui.hud_power;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.ClientPowerCache;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.ClientTickHandler;
import com.github.standobyte.jojo.client.input.InputHandler;
import com.github.standobyte.jojo.client.input.controlscheme.ClientControlScheme;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.client.ui.hud_power.PowerHudControlsElement.AbilityBindUI;
import com.github.standobyte.jojo.client.ui.hud_power.PowerHudControlsElement.BindUI;
import com.github.standobyte.jojo.client.ui.hud_power.PowerHudControlsElement.HotbarUILine;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.client.ui.utils.GuiIcon;
import com.github.standobyte.jojo.client.ui.utils.tooltip.MultiLineScreenTooltip;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.mechanics.resolve.ResolveModeEffect;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismPowerType;
import com.github.standobyte.jojoimpl.powers.zombie.ZombieData;
import com.github.standobyte.jojoimpl.powers.zombie.ZombiePowerType;
import com.github.standobyte.v1_21_4_stuff.missingmethods.ARGB;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class PowerHud {
	public static AbilityHud abilityHUDInstance;

	public static void triggerHamonNoEnergyFeedback() {
		if (abilityHUDInstance != null) {
			abilityHUDInstance.hamonEnergy.triggerNoEnergyFeedback();
		}
	}

	public static void setHamonOutOfBreath(boolean mask) {
		if (abilityHUDInstance != null) {
			abilityHUDInstance.setHamonOutOfBreath(mask);
		}
	}

	public static void tickHamonOutOfBreath() {
		if (abilityHUDInstance != null) {
			abilityHUDInstance.tickOutOfBreathEffect();
		}
	}

	public static void tickHamonNoEnergyFeedback() {
		if (abilityHUDInstance != null) {
			abilityHUDInstance.hamonEnergy.tickFeedback();
		}
	}

	@SubscribeEvent
	public static void addHud(RegisterGuiLayersEvent event) {
		event.registerBelow(VanillaGuiLayers.BOSS_OVERLAY, 
				JojoMod.resLoc("ability_hud"), abilityHUDInstance = new AbilityHud());
	}
	
	
	public static boolean hasElementTooltips(Screen screen) {
		return screen instanceof ChatScreen
				|| /*F3+Esc*/ screen instanceof PauseScreen pause && !pause.showsPauseMenu();
	}
	
	public static boolean canDragElementsOn(Screen screen) {
		return screen instanceof ChatScreen
				|| screen instanceof PauseScreen pause && !pause.showsPauseMenu();
	}
	
	public static boolean isInContainerScreen() {
		return Minecraft.getInstance().screen instanceof AbstractContainerScreen;
	}
	
	@SubscribeEvent
	public static void onContainerMenuRender(ContainerScreenEvent.Render.Foreground event) {
		GuiGraphics graphics = event.getGuiGraphics();
		AbstractContainerScreen<?> screen = event.getContainerScreen();
		graphics.pose().pushPose();
		graphics.pose().translate(-screen.getGuiLeft(), -screen.getGuiTop(), 0.0F);
		abilityHUDInstance.setupRender(TriState.TRUE);
		abilityHUDInstance.renderAbilitiesHUD(graphics, Minecraft.getInstance().getTimer()/*getDeltaTracker()*/);
		graphics.pose().popPose();
	}
	
	@SubscribeEvent
	public static void addDraggableToScreen(ScreenEvent.Init.Post event) {	
		Screen screen = event.getScreen();
		if (canDragElementsOn(screen)) {
			for (HudElement element : abilityHUDInstance.elements.values()) {
				event.addListener(element);
			}
		}
	}
	
	public static boolean canHaveHudOpen() {
		Player player = Minecraft.getInstance().player;
		return player != null && !player.isSpectator();
	}
	
	
	public static class AbilityHud implements LayeredDraw.Layer {
		private static final int OUT_OF_BREATH_SPRITE_TICKS = 15;
		private static final ResourceLocation ADDITIONAL_UI = JojoMod.resLoc("textures/gui/additional.png");
		private static final ResourceLocation VIGNETTE_LOCATION = JojoMod.resLoc("textures/vignette.png");
		public Map<String, HudElement> elements = new HashMap<>();
		private final WindupIndicator activeActionWindup = new WindupIndicator();
		private boolean outOfBreath;
		private boolean outOfBreathMaskSprite;
		private int outOfBreathSpriteTicks;
		private float prevAir;
		private float vignetteBeforeFadeAway = -1.0F;
		
		public <T extends HudElement> T addElement(T element) {
			element.hud = this;
			elements.put(element.name, element);
			return element;
		}
		
		
		public TriState forContainerMenu;
		private int mouseX;
		private int mouseY;
		
		public void setupRender(TriState forContainerMenu) {
			setupRender(forContainerMenu, -1, -1);
		}
		
		public void setupRender(TriState forContainerMenu, int mouseX, int mouseY) {
			this.forContainerMenu = forContainerMenu;
			this.mouseX = mouseX;
			this.mouseY = mouseY;
		}

	
		public PowerHudControlsElement controls = 	addElement(new PowerHudControlsElement("controls", 4, 44, -1, -1));
		public PowerIcon powerIcon = 			addElement(new PowerIcon("powerIcon", 11, 12, 16, 16));
		public Resolve resolveBar = 			addElement(new Resolve("resolve_bar", 31, 12, 32, 16));
		public Stamina staminaBar = 			addElement(new Stamina("stamina_bar", 81, 16, Bars.HORIZONTAL_LENGTH + 8, Bars.HORIZONTAL_WIDTH));
		public HamonEnergy hamonEnergy = 		addElement(new HamonEnergy("energy_hamon", 81, 28, Bars.HORIZONTAL_LENGTH + 8, Bars.HORIZONTAL_WIDTH));
		public VampireEnergy vampireEnergy = 	addElement(new VampireEnergy("energy_vampire", 81, 28, Bars.HORIZONTAL_LENGTH + 8, Bars.HORIZONTAL_WIDTH));
		public ZombieEnergy zombieEnergy = 		addElement(new ZombieEnergy("energy_zombie", 81, 28, Bars.HORIZONTAL_LENGTH + 8, Bars.HORIZONTAL_WIDTH));
		public PillarmanEnergy pillarmanEnergy = addElement(new PillarmanEnergy("energy_pillarman", 81, 28, Bars.HORIZONTAL_LENGTH + 8, Bars.HORIZONTAL_WIDTH));
		public StandRange standRange = 			addElement(new StandRange("stand_range", 
				(int) staminaBar.xOffsetL + staminaBar.getWidth() + 10, (int) staminaBar.yOffsetU, -1, -1));
		public Finisher finisherBar = 			addElement(new Finisher("stand_finisher", 
				HudElement.SnappingH.CENTER, HudElement.SnappingV.CENTER, -16, -16, 32, 32));
		
		@Override
		public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			Minecraft mc = Minecraft.getInstance();
			if (!canHaveHudOpen()) return;
			
			int mouseX = -1;
			int mouseY = -1;
			boolean isContainer = mc.screen instanceof AbstractContainerScreen;
			if (!isContainer) {
				if (mc.screen != null && hasElementTooltips(mc.screen)) {
					mouseX = (int)(mc.mouseHandler.xpos()
							* (double)mc.getWindow().getGuiScaledWidth()
							/ (double)mc.getWindow().getScreenWidth());
					mouseY = (int)(mc.mouseHandler.ypos()
							* (double)mc.getWindow().getGuiScaledHeight()
							/ (double)mc.getWindow().getScreenHeight());
				}
			}
			WindupAtCrosshair.setRender(null);
			setupRender(isContainer ? TriState.FALSE : TriState.DEFAULT, mouseX, mouseY);
			float partialTick = ClientUtil.partialTick(deltaTracker, false);
			renderAbilitiesHUD(guiGraphics, deltaTracker);
			updateActiveActionWindupAtCrosshair(mc, partialTick);
			WindupAtCrosshair.renderCrosshair(guiGraphics, deltaTracker, mc);
			if (!mc.options.hideGui) {
				renderOutOfBreathBurst(guiGraphics);
				renderOutOfBreathVignette(guiGraphics, partialTick);
			}
		}

		public void setHamonOutOfBreath(boolean mask) {
			outOfBreath = true;
			outOfBreathMaskSprite = mask;
			outOfBreathSpriteTicks = OUT_OF_BREATH_SPRITE_TICKS;
			vignetteBeforeFadeAway = -1.0F;
			prevAir = 0.0F;
			hamonEnergy.clearNoEnergyFeedback();
			ClientProxy.setOverlayMessage(Component.translatable("hamon.out_of_breath"), false);
		}

		public boolean isPlayerOutOfBreath() {
			return outOfBreath;
		}

		private void tickOutOfBreathEffect() {
			Player player = Minecraft.getInstance().player;
			if (player == null) {
				return;
			}
			if (outOfBreath && outOfBreathSpriteTicks == 0) {
				prevAir = player.getAirSupply();
				if (prevAir >= player.getMaxAirSupply()) {
					outOfBreath = false;
				}
			}
			if (outOfBreathSpriteTicks > 0) {
				outOfBreathSpriteTicks--;
			}
		}

		private void renderOutOfBreathBurst(GuiGraphics guiGraphics) {
			if (outOfBreathSpriteTicks <= 0) {
				return;
			}
			boolean bubblePopped = outOfBreathSpriteTicks < 11;
			int u = bubblePopped ? 160 : 128;
			int v = outOfBreathMaskSprite ? 32 : 0;
			int x = guiGraphics.guiWidth() / 2 - 16;
			int y = guiGraphics.guiHeight() / 2 - 16;

			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), ADDITIONAL_UI,
					x, y, 32, 32, 0.0F,
					u, v, 32, 32, 256, 256, BlitFloat.NO_TINT);
			RenderSystem.disableBlend();
		}

		private void renderOutOfBreathVignette(GuiGraphics guiGraphics, float partialTick) {
			if (!outOfBreath) {
				return;
			}
			Player player = Minecraft.getInstance().player;
			if (player == null) {
				return;
			}
			float airRatio = Mth.lerp(partialTick, prevAir, (float) player.getAirSupply())
					/ (float) player.getMaxAirSupply();
			float vignette;
			if (airRatio < 0.75F) {
				vignette = 0.8F + (Mth.sin((ClientTickHandler.tickCount + partialTick) * 0.2F) + 1.0F) * 0.1F;
			}
			else {
				if (vignetteBeforeFadeAway < 0.0F) {
					vignetteBeforeFadeAway = 0.8F + (Mth.sin((ClientTickHandler.tickCount + partialTick) * 0.2F) + 1.0F) * 0.1F;
				}
				vignette = 4.0F * (1.0F - airRatio) * vignetteBeforeFadeAway;
			}
			renderVignette(guiGraphics, vignette, vignette, vignette);
		}

		private void renderVignette(GuiGraphics guiGraphics, float r, float g, float b) {
			RenderSystem.disableDepthTest();
			RenderSystem.depthMask(false);
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(
					GlStateManager.SourceFactor.ZERO,
					GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
					GlStateManager.SourceFactor.ONE,
					GlStateManager.DestFactor.ZERO);
			RenderSystem.setShaderColor(r, g, b, 1.0F);
			BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), VIGNETTE_LOCATION,
					0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), -90.0F,
					0, 0, 256, 256, 256, 256, BlitFloat.NO_TINT);
			RenderSystem.depthMask(true);
			RenderSystem.enableDepthTest();
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableBlend();
		}

		private void updateActiveActionWindupAtCrosshair(Minecraft mc, float partialTick) {
			Player player = mc.player;
			if (player == null) {
				return;
			}
			if (setActiveActionWindupAtCrosshair(player, player, partialTick)) {
				return;
			}
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			StandEntity stand = standPower != null ? standPower.getSummonedStandEntity() : null;
			if (stand != null) {
				setActiveActionWindupAtCrosshair(player, stand, partialTick);
			}
		}

		private boolean setActiveActionWindupAtCrosshair(Player player, LivingEntity performer, float partialTick) {
			EntityActionInstance action = LivingComponentAction.getCurEntityAction(performer);
			if (action == null || !(action.ability instanceof Ability ability)) {
				return false;
			}
			WindupIndicator windup = ability.cl_windupIndicator(player, activeActionWindup, partialTick);
			if (windup == null || windup.maxValue <= 0 || windup.value <= 0) {
				return false;
			}
			WindupAtCrosshair.setRender(windup);
			return true;
		}

		public void renderAbilitiesHUD(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.options.hideGui) return;
			
			for (var element : elements.values()) {
				if (element.shouldRender()) {
					RenderSystem.enableBlend();
					RenderSystem.defaultBlendFunc();
					if (mouseX > -1 && mouseY > -1) {
						element.render(guiGraphics, deltaTracker, mouseX, mouseY);
					}
					else {
						element.render(guiGraphics, deltaTracker);
					}
				}
			}
			RenderSystem.disableBlend();
		}
		
		public boolean isAbilitySelected(String abilityName) {
			if (!canHaveHudOpen() || !controls.shouldRender()) return false;
			
			for (BindUI bind : controls.binds) {
				for (AbilityBindUI bindAbility : bind.abilities.values()) {
					if (abilityName.equals(bindAbility.ability.ability.name())) {
						return true;
					}
				}
			}
			for (HotbarUILine hotbar : controls.hotbars) {
				if (hotbar.selected != null) {
					for (AbilityBindUI bindAbility : hotbar.selected.abilities.values()) {
						if (abilityName.equals(bindAbility.ability.ability.name())) {
							return true;
						}
					}
				}
			}
			return false;
		}
		
	}
	
	
	public static class PowerIcon extends HudElement {
		protected PowerClass<?> powerClass;
		protected boolean standSummoned;

		public PowerIcon(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		public PowerIcon(String name, SnappingH snappingHorizontal, SnappingV snappingVertical, 
				int xOffset, int yOffset, int width, int height) {
			super(name, snappingHorizontal, snappingVertical, xOffset, yOffset, width, height);
		}
		
		@Override
		protected void initText() {
			super.initText();
			tooltipText.body.clear();
		}

		@Override
		public boolean shouldRender() {
			standSummoned = ClientPowerCache.getPower(PowerClass.STAND).isSummoned()
					&& !ClientGlobals.isPlayerStandFullBodyUnsummoning();
			powerClass = null;
			
			if (!hud.forContainerMenu.isTrue()) {
				var controlScheme = InputHandler.getInstance().getActiveControlScheme();
				if (controlScheme != null) {
					powerClass = controlScheme.powerClassCosmetic;
					if (powerClass == PowerClass.STAND && !standSummoned) {
						powerClass = null;
					}
				}
			}
			
			if (standSummoned) {
				if (powerClass == null) {
					powerClass = PowerClass.STAND;
				}
				else if (powerClass != PowerClass.STAND) {
					standSummoned = false;
				}
			}
			
			return powerClass != null;
		}

		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			if (powerClass != null) {
				if (powerClass == PowerClass.STAND) {
					renderClientStandIcon(guiGraphics.pose(), getX(), getY());
				}
				else {
					Power<?> power = ClientPowerCache.getPower(powerClass);
					if (power != null && power.hasPower()) {
						GuiIcon icon = getPowerIcon(power.getPowerType());
						icon.render(guiGraphics.pose(), getX(), getY());
					}
				}
			}
		}
		
		@Override
		protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
			if (standSummoned) {
				Power<?> power = ClientPowerCache.getPower(PowerClass.STAND);
				if (power != null && power.hasPower()) {
					Component powerName = power.getName();
					tooltipText.setTitle(Component.translatable("ripples_hud.stand_summoned", powerName.copy())
							.withStyle(ChatFormatting.BLACK));
				}
			}
			else {
				Power<?> power = ClientPowerCache.getPower(powerClass);
				if (power != null && power.hasPower()) {
					Component powerName = power.getName();
					tooltipText.setTitle(powerName.copy()
							.withStyle(ChatFormatting.BLACK));
				}
			}
			super.checkTooltip(mouseX, mouseY, deltaTracker);
		}
	}
	
	public static void renderClientStandIcon(PoseStack pose, int x, int y) {
		renderStandIcon(ClientPowerCache.getPower(PowerClass.STAND), pose, x, y);
	}
	
	public static void renderStandIcon(StandPower standPower, PoseStack pose, int x, int y) {
		if (standPower != null) {
			StandSkin skin = StandSkinsLoader.getInstance().getSkin(standPower);
			if (skin != null) {
				GuiIcon icon = skin.getStandIcon();
				if (icon != null) {
					RenderSystem.enableBlend();
					RenderSystem.defaultBlendFunc();
					icon.render(pose, x, y);
					RenderSystem.disableBlend();
				}
			}
		}
	}
	
	protected static final Map<ResourceLocation, GuiIcon> POWER_ICONS = new HashMap<>();
	public static GuiIcon getPowerIcon(PowerType powerType) {
		return POWER_ICONS.computeIfAbsent(powerType.getId(), 
				id -> new GuiIcon(id.withPath(path -> "textures/power/" + path + ".png"), 16, 16));
	}
	public static GuiIcon getPowerIcon(Supplier<? extends PowerType> powerType) { return getPowerIcon(powerType.get()); }
		
		
	public static class Resolve extends HudElement {
		public static final GuiIcon RESOLVE_MODE = new GuiIcon(JojoMod.resLoc("textures/hud/stand_resolve_mode_bar.png"), 40, 40);
		public static final GuiIcon HORIZONTAL_EMPTY = new GuiIcon(JojoMod.resLoc("textures/hud/stand_resolve_horizontal_empty.png"), 32, 16);
		public static final GuiIcon HORIZONTAL_FULL = new GuiIcon(JojoMod.resLoc("textures/hud/stand_resolve_horizontal_full.png"), 32, 16);
		public static final GuiIcon VERTICAL_EMPTY = new GuiIcon(JojoMod.resLoc("textures/hud/stand_resolve_vertical_empty.png"), 16, 32);
		public static final GuiIcon VERTICAL_FULL = new GuiIcon(JojoMod.resLoc("textures/hud/stand_resolve_vertical_full.png"), 16, 32);

		public Resolve(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		public Resolve(String name, SnappingH snappingHorizontal, SnappingV snappingVertical, 
				int xOffset, int yOffset, int width, int height) {
			super(name, snappingHorizontal, snappingVertical, xOffset, yOffset, width, height);
		}
		
		public MultiLineScreenTooltip tooltipVampire;
		
		@Override
		protected void initText() {
			this.tooltipText = new MultiLineScreenTooltip(
					Component.translatable("ripples_hud." + name).withStyle(ChatFormatting.BLACK), 
					Component.translatable("ripples_hud." + name + ".desc", 
							Component.translatable("ripples_hud." + name + ".desc1.regular"))
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
			this.tooltipVampire = new MultiLineScreenTooltip(
					Component.translatable("ripples_hud." + name).withStyle(ChatFormatting.BLACK), 
					Component.translatable("ripples_hud." + name + ".desc", 
							Component.translatable("ripples_hud." + name + ".desc1.vamp"))
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
			this.tooltip.set(this.tooltipText);
		}

		@Override
		public boolean shouldRender() {
			if (hud.forContainerMenu.isTrue()) return false;
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			if (standPower != null && standPower.usesResolve()) {
				ClientControlScheme controlScheme = InputHandler.getInstance().getActiveControlScheme();
				return controlScheme != null && controlScheme.hasAbility(ability -> ability.powerClass() == PowerClass.STAND);
			}
			
			return false;
		}
		
		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			float partialTick = ClientUtil.partialTick(deltaTracker, false);
			Minecraft mc = Minecraft.getInstance();
			
			int x = getX();
			int y = getY();
			int width = getWidth();
			int height = getHeight();
			
			GuiIcon emptySprite = HORIZONTAL_EMPTY;
			GuiIcon fullSprite = HORIZONTAL_FULL;
			
			float resolveMode = standPower.resolveCounter.getResolveModeTimerRatio(standPower, partialTick);
			if (resolveMode > 0) {
				BlitFloat.blitRadial(guiGraphics.pose(), mc, RESOLVE_MODE.file, 
						x + (width - RESOLVE_MODE.width) / 2, y + (height - RESOLVE_MODE.height) / 2, RESOLVE_MODE.width, RESOLVE_MODE.height, 0, 
						0, resolveMode, BlitFloat.NO_TINT);
			}
			
			float resolveRatio = standPower.resolveCounter.getResolveRatio(standPower, partialTick);
			BlitFloat.blit(guiGraphics.pose(), mc, emptySprite.file, 
					x, y, width, height, 0, 
					BlitFloat.NO_TINT);
			float fillWidth = resolveRatio >= 1 ? width : Math.min(width * resolveRatio, width - 5);
			BlitFloat.blit(guiGraphics.pose(), mc, fullSprite.file, 
					x, y, fillWidth, height, 0, 
					0, 0, fillWidth, height, width, height, 
					BlitFloat.NO_TINT);
			
			if (resolveMode < 0) {
				float multiplier = standPower.resolveCounter.getTotalBoostVisible(standPower.getUser());
				if (multiplier > 1) {
					Component multiplierText = Component.literal("x" + String.format("%.2f", multiplier));
					StandSkin skin = StandSkinsLoader.getCurSkin();
					guiGraphics.drawCenteredString(mc.font, multiplierText, x + width / 2, y + 20, skin != null ? skin.getColor() : 0xFFFFFFFF);
				}
			}
		}
		
		@Override
		protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			
			PlayerPower playerPower = ClientPowerCache.getPower(PowerClass.PLAYER_POWER);
			if (playerPower != null && playerPower.getPowerType() == ModPlayerPowers.VAMPIRISM.get()) {
				this.tooltip.set(this.tooltipVampire);
			}
			else {
				this.tooltip.set(this.tooltipText);
			}
			
			MultiLineScreenTooltip tooltipText = (MultiLineScreenTooltip) this.tooltip.get();
			int resolveModeTimer = standPower.resolveCounter.resolveModeTimer.value;
			if (resolveModeTimer > 0) {
				tooltipText.setTitle(Component.translatable("ripples_hud.resolve_mode",
						Component.literal(StringUtil.formatTickDuration(resolveModeTimer, Minecraft.getInstance().level.tickRateManager().tickrate()))
						).withStyle(ChatFormatting.BOLD).withStyle(style -> style.withColor(0xFFC6151F)));
			}
			else {
				tooltipText.setTitle(Component.translatable("ripples_hud.resolve_bar",
						Component.literal(String.valueOf((int) (standPower.resolveCounter.getResolveRatio(standPower) * 100)))
						).withStyle(ChatFormatting.BLACK));
			}
			super.checkTooltip(mouseX, mouseY, deltaTracker);
		}
	}
	
	
	public static class Stamina extends HudElement {
		public static final GuiIcon ICON = new GuiIcon(JojoMod.resLoc("textures/hud/stand_stamina.png"), 20, 20);
		public static final ResourceLocation BAR_HORIZONTAL_FILL = JojoMod.resLoc("textures/hud/bars/bar_horizontal_stamina.png");
		public static final ResourceLocation BAR_HORIZONTAL_MINI_FILL = JojoMod.resLoc("textures/hud/bars/bar_horizontal_mini_stamina.png");
		public static final ResourceLocation BAR_VERTICAL_FILL = JojoMod.resLoc("textures/hud/bars/bar_vertical_stamina.png");
		public static final ResourceLocation BAR_VERTICAL_MINI_FILL = JojoMod.resLoc("textures/hud/bars/bar_vertical_mini_stamina.png");

		public Stamina(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		public Stamina(String name, SnappingH snappingHorizontal, SnappingV snappingVertical, 
				int xOffset, int yOffset, int width, int height) {
			super(name, snappingHorizontal, snappingVertical, xOffset, yOffset, width, height);
		}
		
		public MultiLineScreenTooltip tooltipResolve;
		
		@Override
		protected void initText() {
			this.tooltipText = new MultiLineScreenTooltip(
					Component.translatable("ripples_hud." + name).withStyle(ChatFormatting.BLACK), 
					Component.translatable("ripples_hud." + name + ".desc", 
							Component.translatable("ripples_hud." + name + ".desc1.regular"))
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
			this.tooltipResolve = new MultiLineScreenTooltip(
					Component.translatable("ripples_hud." + name).withStyle(ChatFormatting.BLACK), 
					Component.translatable("ripples_hud." + name + ".desc", 
							Component.translatable("ripples_hud." + name + ".desc1.resolve"))
					.withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
			this.tooltip.set(this.tooltipText);
		}

		@Override
		public boolean shouldRender() {
			if (hud.forContainerMenu.isTrue()) return false;
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			if (standPower != null && !standPower.isUserCreative() && standPower.usesStamina()) {
				ClientControlScheme controlScheme = InputHandler.getInstance().getActiveControlScheme();
				return controlScheme != null && controlScheme.hasAbility(ability -> ability.powerClass() == PowerClass.STAND);
			}
			
			return false;
		}
		
		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			float staminaRatio = standPower.getStaminaRatio(ClientUtil.partialTick(deltaTracker, false));
			int x = getX() + 8;
			int y = getY();
			float alpha = ResolveModeEffect.getResolveEffectLvl(Minecraft.getInstance().player) >= 0 ? 0.5f : 1;
			Bars.renderHorizontalBar(guiGraphics.pose(), x, y, staminaRatio, BAR_HORIZONTAL_FILL, BlitFloat.NO_TINT, alpha);
			ICON.render(guiGraphics.pose(), x - 12, y - 6, ARGB.white(alpha));
		}
		
		@Override
		protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
			StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
			
			if (ResolveModeEffect.getResolveEffectLvl(Minecraft.getInstance().player) >= 0) {
				this.tooltip.set(this.tooltipResolve);
			}
			else {
				this.tooltip.set(this.tooltipText);
			}
			
			MultiLineScreenTooltip tooltipText = (MultiLineScreenTooltip) this.tooltip.get();
			float value = standPower.getStamina();
			float maxValue = standPower.getMaxStamina();
			float ratio = standPower.getStaminaRatio();
			tooltipText.setTitle(Component.translatable("ripples_hud.stamina_bar",
					Component.literal(String.valueOf((int) value)).withStyle(style -> style.withColor(color(ratio))),
					Component.literal(String.valueOf((int) maxValue))
					).withStyle(ChatFormatting.BLACK));
			super.checkTooltip(mouseX, mouseY, deltaTracker);
		}
		
		public static int color(float ratio) {
			return FastColor.ARGB32.colorFromFloat(1, (1 - ratio) * 0.6f, ratio * 0.6f, 0f);
		}
	}
	
	
	public static class HamonEnergy extends HudElement {
		private static final int HAMON_COLOR = 0xFFFF00;
		public static final GuiIcon ICON = new GuiIcon(JojoMod.resLoc("textures/hud/energy_hamon.png"), 20, 20);
		private int noEnergyHighlightTicks;

		public HamonEnergy(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		public void triggerNoEnergyFeedback() {
			int cycles = 4;
			if (noEnergyHighlightTicks % 10 > 0) {
				cycles--;
			}
			noEnergyHighlightTicks = noEnergyHighlightTicks % 10 + cycles * 10;
		}

		public void clearNoEnergyFeedback() {
			if (noEnergyHighlightTicks > 10) {
				noEnergyHighlightTicks %= 10;
			}
		}

		private void tickFeedback() {
			if (noEnergyHighlightTicks > 0) {
				noEnergyHighlightTicks--;
			}
		}

		@Override
		public boolean shouldRender() {
			if (hud.forContainerMenu.isTrue()) return false;
			HamonData hamon = getHamonData();
			if (hamon == null) {
				return false;
			}
			float maxStability = hamon.getMaxBreathStability();
			return maxStability > 0.0F
					&& (noEnergyHighlightTicks > 0 || hamon.getEnergy() > 0.0F || hamon.getBreathStability() + 0.001F < maxStability
							|| shouldRenderForHamonBreathMode());
		}

		private boolean shouldRenderForHamonBreathMode() {
			InputHandler input = InputHandler.getInstance();
			if (input == null) {
				return false;
			}
			if (input.isHamonBreathInputHeld()) {
				return true;
			}
			ClientControlScheme controlScheme = input.getActiveControlScheme();
			if (controlScheme == null || controlScheme.powerClassCosmetic != PowerClass.PLAYER_POWER) {
				return false;
			}
			for (ClientControlScheme.Hotbar hotbar : controlScheme.getCurGroup().hotbars) {
				ClientControlScheme.HotbarSlot slot = hotbar.getSelected();
				if (slot != null && isHamonBreathSlot(slot)) {
					return true;
				}
			}
			return false;
		}

		private static boolean isHamonBreathSlot(ClientControlScheme.HotbarSlot slot) {
			for (InputMethod inputMethod : InputMethod.values()) {
				ClientControlScheme.AbilityControlsEntry ability = slot.getBaseBind(inputMethod);
				if (ability != null && ability.powerClass() == PowerClass.PLAYER_POWER
						&& "hamon_breath".equals(ability.abilityName())) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			HamonData hamon = getHamonData();
			if (hamon == null) {
				return;
			}
			float maxStability = hamon.getMaxBreathStability();
			if (maxStability <= 0.0F) {
				return;
			}
			float energyRatio = Mth.clamp(hamon.getEnergy() / maxStability, 0.0F, 1.0F);
			float stabilityRatio = Mth.clamp(hamon.getBreathStability() / maxStability, 0.0F, 1.0F);
			int x = getX() + 8;
			int y = getY();
			Bars.renderHorizontalBarWithTranslucent(guiGraphics.pose(), x, y,
					energyRatio, stabilityRatio, Bars.BAR_HORIZONTAL_FILL, HAMON_COLOR, 1.0F);
			if (noEnergyHighlightTicks > 0) {
				float tick = noEnergyHighlightTicks - ClientUtil.partialTick(deltaTracker, false);
				float alpha = ClientUtil.getHighlightAlpha(tick, 10.0F, 8.0F,
						tick > 5.0F ? 0.25F : 0.0F, 0.75F);
				guiGraphics.fill(x + 1, y + 1, x + Bars.HORIZONTAL_LENGTH - 1,
						y + Bars.HORIZONTAL_WIDTH - 1, ARGB.color(alpha, 0xFF0000));
				BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), Bars.BAR_HORIZONTAL_SCALE,
						x, y, Bars.HORIZONTAL_LENGTH, Bars.HORIZONTAL_WIDTH, 0.0F, BlitFloat.NO_TINT);
			}
			ICON.render(guiGraphics.pose(), x - 12, y - 6, BlitFloat.NO_TINT);
		}

		@Override
		protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
			HamonData hamon = getHamonData();
			if (hamon != null) {
				float maxStability = hamon.getMaxBreathStability();
				float energyRatio = maxStability > 0.0F ? Mth.clamp(hamon.getEnergy() / maxStability, 0.0F, 1.0F) : 0.0F;
				MultiLineScreenTooltip tooltipText = (MultiLineScreenTooltip) this.tooltip.get();
				tooltipText.setTitle(Component.translatable("ripples_hud.energy_hamon")
						.append(Component.literal(": "))
						.append(Component.literal(String.valueOf((int) hamon.getEnergy())).withStyle(style -> style.withColor(Stamina.color(energyRatio))))
						.append(Component.literal("/" + (int) maxStability))
						.withStyle(ChatFormatting.BLACK));
			}
			super.checkTooltip(mouseX, mouseY, deltaTracker);
		}

		private HamonData getHamonData() {
			PlayerPower playerPower = ClientPowerCache.getPower(PowerClass.PLAYER_POWER);
			if (playerPower == null || playerPower.getPowerType() != ModPlayerPowers.HAMON.get()) {
				return null;
			}
			return playerPower.getCurTypeData(ModPlayerPowers.HAMON).orElse(null);
		}
	}
	
	public static class VampireEnergy extends HudElement {
		public static final GuiIcon ICON = new GuiIcon(JojoMod.resLoc("textures/hud/energy_vampire.png"), 20, 20);

		public VampireEnergy(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		@Override
		public boolean shouldRender() {
			if (hud.forContainerMenu.isTrue()) return false;
			VampirismData vampirism = getVampirismData();
			Player player = Minecraft.getInstance().player;
			return vampirism != null && player != null && vampirism.getMaxBlood(player) > 0.0F;
		}

		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			VampirismData vampirism = getVampirismData();
			Player player = Minecraft.getInstance().player;
			if (vampirism == null || player == null) {
				return;
			}
			float maxBlood = vampirism.getMaxBlood(player);
			if (maxBlood <= 0.0F) {
				return;
			}
			float ratio = Mth.clamp(vampirism.getBloodLevel() / maxBlood, 0.0F, 1.0F);
			int x = getX() + 8;
			int y = getY();
			Bars.renderHorizontalBar(guiGraphics.pose(), x, y, ratio, Bars.BAR_HORIZONTAL_FILL, VampirismPowerType.COLOR, 1.0F);
			ICON.render(guiGraphics.pose(), x - 12, y - 6, BlitFloat.NO_TINT);
		}

		@Override
		protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
			VampirismData vampirism = getVampirismData();
			Player player = Minecraft.getInstance().player;
			if (vampirism != null && player != null) {
				float maxBlood = vampirism.getMaxBlood(player);
				float ratio = maxBlood > 0.0F ? Mth.clamp(vampirism.getBloodLevel() / maxBlood, 0.0F, 1.0F) : 0.0F;
				MultiLineScreenTooltip tooltipText = (MultiLineScreenTooltip) this.tooltip.get();
				tooltipText.setTitle(Component.translatable("ripples_hud.energy_vampire")
						.append(Component.literal(": "))
						.append(Component.literal(String.valueOf((int) vampirism.getBloodLevel())).withStyle(style -> style.withColor(Stamina.color(ratio))))
						.append(Component.literal("/" + (int) maxBlood))
						.withStyle(ChatFormatting.BLACK));
			}
			super.checkTooltip(mouseX, mouseY, deltaTracker);
		}

		private VampirismData getVampirismData() {
			PlayerPower playerPower = ClientPowerCache.getPower(PowerClass.PLAYER_POWER);
			if (playerPower == null || playerPower.getPowerType() != ModPlayerPowers.VAMPIRISM.get()) {
				return null;
			}
			return playerPower.getCurTypeData(ModPlayerPowers.VAMPIRISM).orElse(null);
		}
	}

	public static class ZombieEnergy extends HudElement {
		public static final GuiIcon ICON = new GuiIcon(JojoMod.resLoc("textures/hud/energy_vampire.png"), 20, 20);

		public ZombieEnergy(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		@Override
		public boolean shouldRender() {
			if (hud.forContainerMenu.isTrue()) return false;
			ZombieData zombie = getZombieData();
			Player player = Minecraft.getInstance().player;
			return zombie != null && player != null && zombie.getMaxEnergy(player) > 0.0F;
		}

		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			ZombieData zombie = getZombieData();
			Player player = Minecraft.getInstance().player;
			if (zombie == null || player == null) {
				return;
			}
			float maxEnergy = zombie.getMaxEnergy(player);
			if (maxEnergy <= 0.0F) {
				return;
			}
			float ratio = Mth.clamp(zombie.getEnergy() / maxEnergy, 0.0F, 1.0F);
			int x = getX() + 8;
			int y = getY();
			Bars.renderHorizontalBar(guiGraphics.pose(), x, y, ratio, Bars.BAR_HORIZONTAL_FILL, ZombiePowerType.COLOR, 1.0F);
			ICON.render(guiGraphics.pose(), x - 12, y - 6, BlitFloat.NO_TINT);
		}

		@Override
		protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
			ZombieData zombie = getZombieData();
			Player player = Minecraft.getInstance().player;
			if (zombie != null && player != null) {
				float maxEnergy = zombie.getMaxEnergy(player);
				float ratio = maxEnergy > 0.0F ? Mth.clamp(zombie.getEnergy() / maxEnergy, 0.0F, 1.0F) : 0.0F;
				MultiLineScreenTooltip tooltipText = (MultiLineScreenTooltip) this.tooltip.get();
				tooltipText.setTitle(Component.translatable("ripples_hud.energy_zombie")
						.append(Component.literal(": "))
						.append(Component.literal(String.valueOf((int) zombie.getEnergy())).withStyle(style -> style.withColor(Stamina.color(ratio))))
						.append(Component.literal("/" + (int) maxEnergy))
						.withStyle(ChatFormatting.BLACK));
			}
			super.checkTooltip(mouseX, mouseY, deltaTracker);
		}

		private ZombieData getZombieData() {
			PlayerPower playerPower = ClientPowerCache.getPower(PowerClass.PLAYER_POWER);
			if (playerPower == null || playerPower.getPowerType() != ModPlayerPowers.ZOMBIE.get()) {
				return null;
			}
			return playerPower.getCurTypeData(ModPlayerPowers.ZOMBIE).orElse(null);
		}
	}

	public static class PillarmanEnergy extends HudElement {
		public static final GuiIcon ICON = new GuiIcon(JojoMod.resLoc("textures/hud/energy_pillarman.png"), 20, 20);

		public PillarmanEnergy(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		@Override
		public boolean shouldRender() {
			if (hud.forContainerMenu.isTrue()) return false;
			PillarmanData pillarman = getPillarmanData();
			Player player = Minecraft.getInstance().player;
			return pillarman != null && player != null && pillarman.getMaxEnergy(player) > 0.0F;
		}

		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			PillarmanData pillarman = getPillarmanData();
			if (pillarman == null) {
				return;
			}
			Player player = Minecraft.getInstance().player;
			if (player == null) {
				return;
			}
			float maxEnergy = pillarman.getMaxEnergy(player);
			if (maxEnergy <= 0.0F) {
				return;
			}
			float ratio = Mth.clamp(pillarman.getEnergy() / maxEnergy, 0.0F, 1.0F);
			int x = getX() + 8;
			int y = getY();
			Bars.renderHorizontalBar(guiGraphics.pose(), x, y, ratio, Bars.BAR_HORIZONTAL_FILL, PillarmanPowerType.COLOR, 1.0F);
			ICON.render(guiGraphics.pose(), x - 12, y - 6, BlitFloat.NO_TINT);
		}

		@Override
		protected void checkTooltip(double mouseX, double mouseY, DeltaTracker deltaTracker) {
			PillarmanData pillarman = getPillarmanData();
			Player player = Minecraft.getInstance().player;
			if (pillarman != null && player != null) {
				float maxEnergy = pillarman.getMaxEnergy(player);
				float ratio = maxEnergy > 0.0F ? Mth.clamp(pillarman.getEnergy() / maxEnergy, 0.0F, 1.0F) : 0.0F;
				MultiLineScreenTooltip tooltipText = (MultiLineScreenTooltip) this.tooltip.get();
				tooltipText.setTitle(Component.translatable("ripples_hud.energy_pillarman")
						.append(Component.literal(": "))
						.append(Component.literal(String.valueOf((int) pillarman.getEnergy())).withStyle(style -> style.withColor(Stamina.color(ratio))))
						.append(Component.literal("/" + (int) maxEnergy))
						.withStyle(ChatFormatting.BLACK));
			}
			super.checkTooltip(mouseX, mouseY, deltaTracker);
		}

		private PillarmanData getPillarmanData() {
			PlayerPower playerPower = ClientPowerCache.getPower(PowerClass.PLAYER_POWER);
			if (playerPower == null || playerPower.getPowerType() != ModPlayerPowers.PILLAR_MAN.get()) {
				return null;
			}
			return playerPower.getCurTypeData(ModPlayerPowers.PILLAR_MAN).orElse(null);
		}
	}

	
	public static class Finisher extends HudElement {
		public static final ResourceLocation[] BARS = {
				JojoMod.resLoc("textures/hud/stand_finisher_1.png"),
				JojoMod.resLoc("textures/hud/stand_finisher_2.png"),
				JojoMod.resLoc("textures/hud/stand_finisher_3.png")
		};
		public static final ResourceLocation[] BARS_FULL = {
				JojoMod.resLoc("textures/hud/stand_finisher_1_full.png"),
				JojoMod.resLoc("textures/hud/stand_finisher_2_full.png")
		};

		public Finisher(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		public Finisher(String name, SnappingH snappingHorizontal, SnappingV snappingVertical, 
				int xOffset, int yOffset, int width, int height) {
			super(name, snappingHorizontal, snappingVertical, xOffset, yOffset, width, height);
		}

		@Override
		public boolean shouldRender() {
			if (hud.forContainerMenu.isTrue()) return false;
			StandEntity stand = ClientGlobals.playerStandEntity;
			return stand != null && !ClientGlobals.isPlayerStandFullBodyUnsummoning();
		}
		
		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			Minecraft mc = Minecraft.getInstance();
			StandEntity stand = ClientGlobals.playerStandEntity;
			float partialTick = ClientUtil.partialTick(deltaTracker, false);
			float finisher = stand.getFinisherMeter(partialTick);
			
			int crosshairX = (guiGraphics.guiWidth() - 15) / 2;
			int crosshairY = (guiGraphics.guiHeight() - 15) / 2;
			
			float width = getWidth();
			float height = getHeight();
			float x = crosshairX - width / 4;
			float y = crosshairY - height / 4;
			int color = ARGB.white(0.5f);

			int fullFinishers = Mth.floor(finisher);
			if (fullFinishers > 0) {
				BlitFloat.blit(guiGraphics.pose(), mc, BARS_FULL[Math.min(fullFinishers, BARS_FULL.length) - 1], 
						x, y, width, height, 0, 
						color);
			}
			
			float finisherFill = Mth.frac(finisher);
			BlitFloat.blitRadial(guiGraphics.pose(), mc, BARS[Math.min(fullFinishers, BARS.length - 1)], 
					x, y, width, height, 0, 
					0, finisherFill, color);
		}
	}
	
	
	public static class StandRange extends HudElement {

		public StandRange(String name, int x0, int y0, int width, int height) {
			super(name, x0, y0, width, height);
		}

		public StandRange(String name, SnappingH snappingHorizontal, SnappingV snappingVertical, 
				int xOffset, int yOffset, int width, int height) {
			super(name, snappingHorizontal, snappingVertical, xOffset, yOffset, width, height);
		}

		@Override
		public boolean shouldRender() {
			if (hud.forContainerMenu.isTrue()) return false;
			StandEntity stand = ClientGlobals.playerStandEntity;
			return stand != null && !ClientGlobals.isPlayerStandFullBodyUnsummoning() && stand.isManuallyControlled();
		}
		
		@Override
		public void renderElement(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
			StandEntity stand = ClientGlobals.playerStandEntity;
			double distance = MathUtil.getAABBDistance(stand.getBoundingBox(), stand.getUser().getBoundingBox());
			double damageFactor = stand.rangeEfficiency;
			Font font = Minecraft.getInstance().font;

			int x = this.getX();
			int y = this.getY();
			int width;
			int height;
			Component distanceString = Component.literal(String.format("%.2f m", distance));
			guiGraphics.drawString(font, distanceString, x, y, 0xFFFFFFFF);
			width = font.width(distanceString);
			height = font.lineHeight;
			if (damageFactor < 1) {
				y += 12;
				Component strength = Component.translatable("jojo_ripples.overlay.stand_strength", String.format("%.2f%%", damageFactor * 100F));
				guiGraphics.drawString(font, strength, x, y, 0xFF4040);
				width = Math.max(width, font.width(strength));
				height += 12;
			}
			updateRectangle(width, height);
		}
	}
	
}
