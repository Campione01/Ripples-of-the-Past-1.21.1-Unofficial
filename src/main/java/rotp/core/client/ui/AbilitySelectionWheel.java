package rotp.core.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import javax.annotation.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import rotp.core.api.client.render.AbilitySelectionSurface;
import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.client.ClientPowerCache;
import rotp.core.client.input.InputHandler;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.client.input.controlscheme.ClientControlScheme.AbilityControlsEntry;
import rotp.core.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.client.standskin.sprites.AbilityIconSprites;
import rotp.core.client.ui.hud_power.PowerHudControlsElement;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.ui.utils.tooltip.TooltipParams;
import rotp.core.client.util.ClientCursorUtil;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.compat.v1_21_4.missingmethods.ARGB;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
public class AbilitySelectionWheel extends Screen implements ScreenLetsUseWASD {
	protected static final ResourceLocation DEFAULT_TEXTURE = JojoMod.resLoc("textures/ability_wheel.png");
	private static final int TOOLTIP_MARGIN = 4;
	private static final ClientTooltipPositioner TOOLTIP_POSITIONER = (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
		Vector2ic pos = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
		return new Vector2i(
				Mth.clamp(pos.x(), TOOLTIP_MARGIN, Math.max(TOOLTIP_MARGIN, screenWidth - tooltipWidth - TOOLTIP_MARGIN)),
				Mth.clamp(pos.y(), TOOLTIP_MARGIN, Math.max(TOOLTIP_MARGIN, screenHeight - tooltipHeight - TOOLTIP_MARGIN)));
	};

	private static Vector2ic positionTooltip(int screenWidth, int screenHeight,
			int mouseX, int mouseY, int tooltipWidth, int tooltipHeight,
			List<ScreenRectangle> occupied) {
		Vector2ic preferred = TOOLTIP_POSITIONER.positionTooltip(
				screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
		if (tooltipFits(preferred.x(), preferred.y(), tooltipWidth, tooltipHeight,
				screenWidth, screenHeight, occupied)) {
			return preferred;
		}
		int maxX = screenWidth - tooltipWidth - TOOLTIP_MARGIN;
		int maxY = screenHeight - tooltipHeight - TOOLTIP_MARGIN;
		if (maxX < TOOLTIP_MARGIN || maxY < TOOLTIP_MARGIN) return preferred;

		List<Integer> xs = new ArrayList<>(List.of(preferred.x(), TOOLTIP_MARGIN, maxX));
		List<Integer> ys = new ArrayList<>(List.of(preferred.y(), TOOLTIP_MARGIN, maxY));
		// A nearest clear position lies at the preferred coordinate or a label edge.
		for (ScreenRectangle bounds : occupied) {
			xs.add(bounds.left() - tooltipWidth - TOOLTIP_MARGIN);
			xs.add(bounds.right() + TOOLTIP_MARGIN);
			ys.add(bounds.top() - tooltipHeight - TOOLTIP_MARGIN);
			ys.add(bounds.bottom() + TOOLTIP_MARGIN);
		}
		Vector2ic best = preferred;
		long bestDistance = Long.MAX_VALUE;
		for (int x : xs) {
			if (x < TOOLTIP_MARGIN || x > maxX) continue;
			for (int y : ys) {
				if (y < TOOLTIP_MARGIN || y > maxY
						|| !tooltipFits(x, y, tooltipWidth, tooltipHeight,
								screenWidth, screenHeight, occupied)) continue;
				long dx = (long) x - preferred.x();
				long dy = (long) y - preferred.y();
				long distance = dx * dx + dy * dy;
				if (distance < bestDistance) {
					best = new Vector2i(x, y);
					bestDistance = distance;
				}
			}
		}
		// No clear placement: keep the full tooltip and labels at the legacy position.
		return best;
	}

	private static boolean tooltipFits(int x, int y, int tooltipWidth, int tooltipHeight,
			int screenWidth, int screenHeight, List<ScreenRectangle> occupied) {
		// Vanilla's three-pixel padding plus its one-pixel outer edge.
		ScreenRectangle tooltip = new ScreenRectangle(x - TOOLTIP_MARGIN, y - TOOLTIP_MARGIN,
				tooltipWidth + TOOLTIP_MARGIN * 2, tooltipHeight + TOOLTIP_MARGIN * 2);
		return tooltip.left() >= 0 && tooltip.top() >= 0
				&& tooltip.right() <= screenWidth && tooltip.bottom() <= screenHeight
				&& occupied.stream().noneMatch(tooltip::overlaps);
	}

	protected ResourceLocation texture;
	public ClientControlScheme.Hotbar abilities;
	protected StandSkin standSkin;

	public AbilitySelectionWheel(ClientControlScheme.Hotbar abilities) {
		super(Component.translatable("jojo_ripples.screen.ability_selection_wheel"));
		this.abilities = abilities;
	}
	
	public void init() {
		StandPower standPower = ClientPowerCache.getPower(PowerClass.STAND);
		if (standPower != null) {
			StandSkinsLoader skinLoader = StandSkinsLoader.getInstance();
			standSkin = skinLoader.getSkin(standPower);
			if (standSkin != null) {
				texture = standSkin.getTexture(DEFAULT_TEXTURE);
			}
		}
		if (texture == null) {
			texture = DEFAULT_TEXTURE;
		}
	}
	
	@Nullable protected int[] mouseIgnorePos = null;
	public void setIgnoreMouseUntilMove(OptionalInt newSelectedSlot) {
		Window window = minecraft.getWindow();
		long windowHandle = window.getWindow();
//		if (mouseIgnorePos == null) {
//			GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
//		}
		if (newSelectedSlot.isPresent()) {
			double width = (double)window.getScreenWidth();
			double height = (double)window.getScreenHeight();
			int[] pos = posAtSector(newSelectedSlot.getAsInt(), abilities.slots.size(), 50);
			double xpos = pos[0] / (double)window.getGuiScaledWidth() * width;
			double ypos = pos[1] / (double)window.getGuiScaledHeight() * height;
			ClientCursorUtil.moveCursor(minecraft, xpos, ypos);
		}
		mouseIgnorePos = new int[] { ClientUtil.getScreenMouseX(), ClientUtil.getScreenMouseY() };
	}
	
	protected int[] posAtSector(int index, int count, double distFromCenter) {
		Window window = minecraft.getWindow();
		double angle = (index + 0.5) * 2 * Math.PI / count;
		if (COUNTER_CLOCKWISE) {
			angle = 2 * Math.PI - angle;
		}
		double width = (double)window.getGuiScaledWidth();
		double height = (double)window.getGuiScaledHeight();
		double xpos = width / 2 + Math.sin(angle) * distFromCenter;
		double ypos = height / 2 - Math.cos(angle) * distFromCenter;
		return new int[] { (int) xpos, (int) ypos };
	}
	
	public boolean checkIsIgnoringMouse(int mouseX, int mouseY) {
//		long window = minecraft.getWindow().getWindow();
		if (mouseIgnorePos != null && (mouseIgnorePos[0] != mouseX || mouseIgnorePos[1] != mouseY)) {
//			GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
			mouseIgnorePos = null;
		}
		return mouseIgnorePos != null;
	}
	
	protected static final boolean COUNTER_CLOCKWISE = true;

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		ScreenLetsUseWASD.syncMovementKeys(minecraft);
		if (abilities == null || !InputHandler.getInstance().isSelectingAbility(abilities)) {
			onClose();
			return;
		}
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		float width = 256;
		float height = 256;
		float x = (this.width - width) / 2f;
		float y = (this.height - height) / 2f;
		PoseStack pose = guiGraphics.pose();
		ResourceLocation texture = DEFAULT_TEXTURE;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		int textColor = standSkin != null ? standSkin.getColor() : 0xFFFFFFFF;
		
		boolean ignoreMouse = checkIsIgnoringMouse(mouseX, mouseY);
		hoveredSlotIndex = ignoreMouse ? abilities.slotIndex : getSlotIndexAt(mouseX, mouseY);
		
		int n = abilities.slots.size();
		float angle0 = 0;
		float fill = 1f / n;
		float angleStep = 2 * (float) Math.PI * fill;
		if (COUNTER_CLOCKWISE) {
			angleStep = -angleStep;
			fill = -fill;
		}
		
		AbilityIconSprites abilityIconSprites = StandSkinsLoader.getInstance().abilityIcons;
		List<ScreenRectangle> tooltipObstacles = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			HotbarSlot slot = abilities.slots.get(i);
			AbilityConditionCheck ability = slot.showAbility();
			boolean showAbility = ability != null;
			Power<?> abilityCtx = ability != null && minecraft.player != null ? ability.ability.getUserPower(minecraft.player) : null;
			TextureAtlasSprite abilitySprite = ability != null ? abilityIconSprites.getAbilityIcon(ability.ability, abilityCtx, standSkin) : null;
			
			if (i == hoveredSlotIndex && !showAbility) { 
				hoveredSlotIndex = -1;
			}
			boolean highlight = i == hoveredSlotIndex;
			float alpha = highlight ? 0.5f : 0.25f;
			int sectorTint = ARGB.white(alpha);
			if (highlight && ability != null) {
				sectorTint = AbilitySelectionVisualPolicies.selectionTint(
						ability.ability,
						abilityCtx,
						ability.conditionCheck,
						AbilitySelectionSurface.SELECTION_WHEEL_HOVERED,
						sectorTint);
			}
			if (highlight) {
				pose.pushPose();
				pose.translate(this.width / 2, this.height / 2, 0);
				pose.scale(1.1f, 1.1f, 1);
				pose.translate(-this.width / 2, -this.height / 2, 10);
			}
			BlitFloat.blitRadial(pose, Minecraft.getInstance(), texture, 
					x, y, width, height, 0, 
					angle0, fill, sectorTint);
			angle0 += angleStep;
			if (highlight) {
				pose.popPose();
			}
			
			if (abilitySprite != null) {
				int[] iconPos = posAtSector(i, n, 75);
				float iconWidth = 16;
				float iconHeight = 16;
				int iconColor = PowerHudControlsElement.abilityColor(BlitFloat.NO_TINT, ability);
				BlitFloat.blit(pose, minecraft, abilitySprite, 
						iconPos[0] - iconWidth / 2, iconPos[1] - iconHeight / 2, iconWidth, iconHeight, 0, iconColor);
				tooltipObstacles.add(new ScreenRectangle(
						iconPos[0] - (int) iconWidth / 2, iconPos[1] - (int) iconHeight / 2,
						(int) iconWidth, (int) iconHeight));
			}
			

			if (showAbility) {
				int numberKey = HotbarSlot.numberKey(slot.index);
				if (numberKey != -1) {
					int[] digitPos = posAtSector(i, n, 90);
					String digit = String.valueOf(numberKey);
					int digitWidth = minecraft.font.width(digit);
					tooltipObstacles.add(new ScreenRectangle(
							digitPos[0] - digitWidth / 2, digitPos[1] - minecraft.font.lineHeight / 2,
							digitWidth + 1, minecraft.font.lineHeight + 1));
					guiGraphics.drawCenteredString(minecraft.font, digit,
							digitPos[0], digitPos[1] - minecraft.font.lineHeight / 2, textColor);
					RenderSystem.enableBlend();
					RenderSystem.defaultBlendFunc();
				}
			}
		}
		RenderSystem.disableBlend();
		
		abilityNames.clear();
		if (hoveredSlotIndex != -1 && !abilities.slots.isEmpty()) {
			if (!ignoreMouse) {
				abilities.slotIndex = hoveredSlotIndex;
			}
			
			hoveredSlot = abilities.slots.get(hoveredSlotIndex);
			for (InputMethod inputMethod : InputMethod.values()) {
				AbilityControlsEntry abilityPath = hoveredSlot.getBaseBind(inputMethod);
				if (abilityPath == null) continue;
				Power<?> power = ClientPowerCache.getPower(abilityPath.powerClass());	if (power == null) continue;
				Moveset moveset = power.getMoveset();									if (moveset == null) continue;
				Ability ability = moveset.getAbility(abilityPath.abilityName());		if (ability == null) continue;
				
				abilityNames.add(ability.getName(power).copy().withStyle(ChatFormatting.BLACK));
			}
		}
		else {
			hoveredSlot = null;
		}
		if (!abilityNames.isEmpty()) {
			TooltipParams.set(TooltipParams.paperStyle());
			List<FormattedCharSequence> tooltipLines = new ArrayList<>();
			for (Component name : abilityNames) {
				tooltipLines.addAll(font.split(name, Math.max(1, this.width - TOOLTIP_MARGIN * 2)));
			}
			ClientTooltipPositioner positioner = (viewportWidth, viewportHeight,
					anchorX, anchorY, tooltipWidth, tooltipHeight) -> positionTooltip(
							viewportWidth, viewportHeight, anchorX, anchorY,
							tooltipWidth, tooltipHeight, tooltipObstacles);
			guiGraphics.renderTooltip(font, tooltipLines, positioner, mouseX, mouseY);
		}
	}

	protected int hoveredSlotIndex;
	protected HotbarSlot hoveredSlot;
	protected List<Component> abilityNames = new ArrayList<>(2);

	public int getSlotIndexAt(int mouseX, int mouseY) {
		if (abilities == null) return -1;
		int xCenter = this.width / 2;
		int yCenter = this.height / 2;
		if (xCenter == mouseX && yCenter == mouseY) {
			return -1;
//			return abilities.slots.size() > abilities.slotIndex ? abilities.slotIndex : -1;
		}
		int xOffs = mouseX - xCenter;
		int yOffs = mouseY - yCenter;
		double angle = Mth.atan2(xOffs, -yOffs); // [0; 2*PI)
		if (COUNTER_CLOCKWISE) {
			angle = 2 * Math.PI - angle;
		}
		if (angle < 0) angle += 2 * Math.PI;
		if (angle >= 2 * Math.PI) angle -= 2 * Math.PI;
		int index = Mth.floor(abilities.slots.size() * angle / (2 * Math.PI));
		return index;
	}
	
	protected boolean pickAbilityAt(int mouseX, int mouseY) {
		if (abilities != null) {
			int clickedIndex = getSlotIndexAt(mouseX, mouseY);
			if (clickedIndex >= 0 && clickedIndex < abilities.slots.size()) {
				abilities.slotIndex = clickedIndex;
				return true;
			}
		}
		return false;
	}

	public void commitHoveredSelection() {
		if (abilities != null && hoveredSlotIndex >= 0 && hoveredSlotIndex < abilities.slots.size()) {
			abilities.slotIndex = hoveredSlotIndex;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		
		if (pickAbilityAt((int) mouseX, (int) mouseY)) {
			onClose();
			return true;
		}
		
		return false;
	}
	
	@Override
	public void onClose() {
		commitHoveredSelection();
		InputHandler.getInstance().setSelectingAbility(abilities, null, false);
		super.onClose();
	}




	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

}
