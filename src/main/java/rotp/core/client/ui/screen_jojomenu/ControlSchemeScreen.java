package rotp.core.client.ui.screen_jojomenu;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.client.input.controlscheme.AllControlSchemes;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.client.input.controlscheme.ClientControlScheme.Bind;
import rotp.core.client.input.controlscheme.ClientControlScheme.Hotbar;
import rotp.core.client.input.controlscheme.ClientControlScheme.HotbarSlot;
import rotp.core.client.input.controlscheme.ClientControlScheme.MoveGroup;
import rotp.core.client.input.controlscheme.ClientKey;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.client.standskin.sprites.AbilityIconSprites;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.ui.utils.GuiIcon;
import rotp.core.client.ui.utils.tooltip.TooltipParams;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings.KeyActiveType;
import rotp.core.powersystem.ability.controls.ControlSchemeSettings.OnKeyPress;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * Controls editor (1.16 HudLayoutEditingScreen): hotbar order (LMB drag), hidden slots (RMB),
 * extra per-ability keys with press and HUD modes, reset, hotbars on/off and Minecraft controls.
 * Changes are saved per power type by {@link AllControlSchemes}.
 */
public class ControlSchemeScreen extends Screen implements IJojoMenuScreen {
	protected ResourceLocation texture;
	protected GuiIcon abilitySlot;
	protected TabCategory category;
	protected Tab tab;

	protected List<PowerClassName> powerClasses = new ArrayList<>();
	protected List<AbilityEntry> mainAbilities = new ArrayList<>();

	// the edited power, kept between openings
	protected static PowerClass<?> editedPowerClass;
	@Nullable protected Power<?> editedPower;
	@Nullable protected ClientControlScheme scheme;
	@Nullable protected AbilityEntry selectedAbility;
	@Nullable protected Bind capturingBind;
	protected final ControlSchemeEditorInput.KeyCapture keyCapture = new ControlSchemeEditorInput.KeyCapture();
	// last mouse position, for keys pressed over a hotbar slot
	protected int lastMouseX = -1;
	protected int lastMouseY = -1;
	protected int keybindScroll;
	@Nullable protected Hotbar dragHotbar;
	protected int dragFrom = -1;
	protected List<SlotPos> slotPositions = new ArrayList<>();
	protected List<GroupBind> customBinds = new ArrayList<>();
	protected int keybindsY;
	protected int keybindRowsY;
	protected int keybindRowsShown;

	public ControlSchemeScreen(Component title, TabCategory category, Tab tab) {
		super(title);
		this.category = category;
		this.tab = tab;
		this.texture = JojoMod.resLoc("textures/gui/paper_style/control_scheme.png");
		this.abilitySlot = new GuiIcon(texture, 0, 227, 18, 18, 512, 512);
	}

	@Override
	public void init() {
		super.init();

		// Create the list off all abilities the player can add a keybind to
		this.powerClasses.clear();
		this.mainAbilities.clear();
		int powerClassI = 1;
		int row = 0;
		PowerClass<?> firstPowerClass = null;
		boolean editedStillPresent = false;
		for (PowerClass<?> powerClass : PowerClass.values()) {
			Power<?> power =
					JojoMenuTabs.getPowerForMenu(powerClass);
			if (power != null && power.hasPower()) {
				if (firstPowerClass == null) firstPowerClass = powerClass;
				if (powerClass == editedPowerClass) editedStillPresent = true;
				powerClasses.add(new PowerClassName(powerClass, powerClassI * POWER_NAME_HEIGHT + (row - 1) * SLOT_HEIGHT));
				var abilities = power.getMoveset().abilities.values();
				int column = 0;
				for (Ability ability : abilities) {
					if (ability.addToControlSchemeEditing()) {
						int x = SLOTS_X_OFFSET + column * SLOT_WIDTH;
						int y = SLOTS_Y_OFFSET + row * SLOT_HEIGHT + powerClassI * POWER_NAME_HEIGHT;
						mainAbilities.add(new AbilityEntry(ability, x, y));

						column++;
						if (column == MAX_ICONS_IN_ROW) {
							row++;
							column = 0;
						}
					}
				}
				row++;
			}
			powerClassI++;
		}

		if (!editedStillPresent) {
			editedPowerClass = firstPowerClass;
		}
		editedPower = editedPowerClass != null ? JojoMenuTabs.getPowerForMenu(editedPowerClass) : null;
		scheme = editedPower != null ? AllControlSchemes.getForPowerType(editedPower.getPowerType()) : null;
		if (selectedAbility != null && selectedAbility.ability.abilityId.powerClass() != editedPowerClass) {
			selectedAbility = null;
		}
		layoutEditor();
		addEditorWidgets();
	}

	protected int SLOT_WIDTH = 18;
	protected int SLOT_HEIGHT = 18;
	protected int ICON_WIDTH = 16;
	protected int ICON_HEIGHT = 16;
	protected int SLOTS_X_OFFSET = 235;
	protected int SLOTS_Y_OFFSET = 4;
	protected int POWER_NAME_HEIGHT = 20;
	protected int MAX_ICONS_IN_ROW = 4;

	protected static final int EDITOR_X = 8;
	protected static final int HOTBARS_Y = 26;
	protected static final int HOTBAR_SLOTS_IN_ROW = 12;
	protected static final int KEYBIND_ROW_HEIGHT = 20;
	protected static final int BOTTOM_BUTTONS_Y = 205;

	protected static record PowerClassName(PowerClass<?> powerClass, int y) {}
	protected static record AbilityEntry(Ability ability, int x, int y) {}
	protected static record SlotPos(MoveGroup group, Hotbar hotbar, int index, int x, int y) {}
	protected static record GroupBind(MoveGroup group, Bind bind) {}

	protected void layoutEditor() {
		slotPositions.clear();
		customBinds.clear();
		int y = HOTBARS_Y;
		if (scheme != null) {
			boolean groupNames = scheme.moveGroups.size() > 1;
			for (MoveGroup group : scheme.moveGroups.values()) {
				if (groupNames) y += 10;
				for (Hotbar hotbar : group.hotbars) {
					for (int i = 0; i < hotbar.layout.size(); i++) {
						slotPositions.add(new SlotPos(group, hotbar, i,
								EDITOR_X + (i % HOTBAR_SLOTS_IN_ROW) * SLOT_WIDTH, y + (i / HOTBAR_SLOTS_IN_ROW) * SLOT_HEIGHT));
					}
					int rows = Math.max(1, (hotbar.layout.size() + HOTBAR_SLOTS_IN_ROW - 1) / HOTBAR_SLOTS_IN_ROW);
					y += rows * SLOT_HEIGHT + 4;
				}
				for (Bind bind : group.binds) {
					if (bind.custom) customBinds.add(new GroupBind(group, bind));
				}
			}
		}
		keybindsY = y + 2;
		keybindRowsY = keybindsY + 11;
		keybindRowsShown = Math.max(0, (BOTTOM_BUTTONS_Y - 2 - keybindRowsY) / KEYBIND_ROW_HEIGHT);
		keybindScroll = Math.max(0, Math.min(keybindScroll, customBinds.size() - keybindRowsShown));
	}

	protected void addEditorWidgets() {
		int x = getWindowX(this);
		int y = getWindowY(this);

		if (scheme != null) {
			for (int row = 0; row < keybindRowsShown && keybindScroll + row < customBinds.size(); row++) {
				GroupBind entry = customBinds.get(keybindScroll + row);
				Bind bind = entry.bind;
				int rowY = y + keybindRowsY + row * KEYBIND_ROW_HEIGHT;

				addRenderableWidget(Button.builder(keyLabel(bind), button -> {
					capturingBind = bind;
					keyCapture.reset();
					rebuildWidgets();
				}).bounds(x + 26, rowY, 60, 18).build());

				addRenderableWidget(Button.builder(onKeyPressLabel(bind.onKeyPress), button -> {
					bind.onKeyPress = bind.onKeyPress == OnKeyPress.PERFORM ? OnKeyPress.SELECT : OnKeyPress.PERFORM;
					onEdited();
				}).bounds(x + 88, rowY, 40, 18).tooltip(Tooltip.create(Component.translatable("jojo.keybind_mode.key_press.title")
						.append("\n").append(onKeyPressText(bind.onKeyPress)))).build());

				addRenderableWidget(Button.builder(activeTypeLabel(bind.activeType), button -> {
					KeyActiveType[] types = KeyActiveType.values();
					bind.activeType = types[(bind.activeType.ordinal() + 1) % types.length];
					onEdited();
				}).bounds(x + 130, rowY, 62, 18).tooltip(Tooltip.create(Component.translatable("jojo.keybind_mode.is_active.title")
						.append("\n").append(activeTypeText(bind.activeType)))).build());

				addRenderableWidget(Button.builder(Component.literal(bind.visibleInHud ? "+" : "-"), button -> {
					bind.visibleInHud = !bind.visibleInHud;
					onEdited();
				}).bounds(x + 194, rowY, 14, 18).tooltip(Tooltip.create(Component.translatable(
						"jojo.keybind_mode.hud_visibility." + bind.visibleInHud))).build());

				addRenderableWidget(Button.builder(Component.literal("x"), button -> {
					entry.group.binds.remove(bind);
					if (capturingBind == bind) capturingBind = null;
					onEdited();
				}).bounds(x + 210, rowY, 16, 18).tooltip(Tooltip.create(Component.translatable("jojo.screen.edit_hud_layout.remove"))).build());
			}

			addRenderableWidget(Button.builder(Component.translatable("jojo.screen.edit_hud_layout.reset"), button -> {
				scheme = AllControlSchemes.reset(editedPower.getPowerType());
				capturingBind = null;
				rebuildWidgets();
			}).bounds(x + 6, y + BOTTOM_BUTTONS_Y, 56, 18).build());

			addRenderableWidget(Button.builder(CommonComponents.optionStatus(
					Component.translatable("jojo.screen.edit_hud_layout.hotbars"), scheme.hotbarsEnabled), button -> {
				scheme.hotbarsEnabled = !scheme.hotbarsEnabled;
				onEdited();
			}).bounds(x + 64, y + BOTTOM_BUTTONS_Y, 84, 18).tooltip(Tooltip.create(Component.translatable(scheme.hotbarsEnabled
					? "jojo.screen.edit_hud_layout.hotbars_on" : "jojo.screen.edit_hud_layout.hotbars_off"))).build());

			Button addKey = addRenderableWidget(Button.builder(Component.translatable("jojo.screen.edit_hud_layout.add_keybind"), button -> {
				if (selectedAbility != null) {
					String abilityName = selectedAbility.ability.abilityId.nameInMoveset();
					capturingBind = AllControlSchemes.addCustomBind(scheme, groupFor(abilityName), abilityName);
					keyCapture.reset();
					AllControlSchemes.save(scheme);
					layoutEditor();
					keybindScroll = Math.max(0, customBinds.size() - keybindRowsShown);
					rebuildWidgets();
				}
			}).bounds(x + 234, y + BOTTOM_BUTTONS_Y, 80, 18).tooltip(Tooltip.create(Component.translatable("jojo.screen.edit_hud_layout.hint.keybinds"))).build());
			addKey.active = selectedAbility != null;
		}

		addRenderableWidget(Button.builder(Component.translatable("options.controls"), button -> {
			minecraft.setScreen(new KeyBindsScreen(this, minecraft.options));
		}).bounds(x + 150, y + BOTTOM_BUTTONS_Y, 76, 18).tooltip(Tooltip.create(Component.translatable("jojo.screen.edit_hud_layout.mc_controls"))).build());
	}

	protected MoveGroup groupFor(String abilityName) {
		for (MoveGroup group : scheme.moveGroups.values()) {
			for (Hotbar hotbar : group.hotbars) {
				for (HotbarSlot slot : hotbar.layout) {
					if (slot.hasAbility(abilityName)) return group;
				}
			}
			for (Bind bind : group.binds) {
				if (bind.ability.abilityName().equals(abilityName)) return group;
			}
		}
		return scheme.getCurGroup();
	}

	/** Refreshes the HUD hotbars, saves and redraws the editor. */
	protected void onEdited() {
		if (scheme != null) {
			AllControlSchemes.applyLayouts(scheme);
			AllControlSchemes.save(scheme);
		}
		rebuildWidgets();
	}

	protected Component keyLabel(Bind bind) {
		ClientKey key = bind.input != null ? bind.input.getKey() : null;
		MutableComponent label;
		if (key == null) {
			label = Component.translatable("key.keyboard.unknown");
		}
		else {
			KeyModifier modifier = bind.input.getKeyModifier();
			label = switch (modifier) {
				case CONTROL -> Component.literal("Ctrl + ").append(key.keyName());
				case SHIFT -> Component.literal("Shift + ").append(key.keyName());
				case ALT -> Component.literal("Alt + ").append(key.keyName());
				default -> key.keyName().copy();
			};
		}
		if (capturingBind == bind) {
			return Component.literal("> ").append(label.withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)).append(" <")
					.withStyle(ChatFormatting.YELLOW);
		}
		return label;
	}

	protected Component onKeyPressLabel(OnKeyPress onKeyPress) {
		return Component.translatable(onKeyPress == OnKeyPress.PERFORM
				? "jojo.keybind_mode.key_press.perform.short" : "jojo.keybind_mode.key_press.select.short");
	}

	protected Component onKeyPressText(OnKeyPress onKeyPress) {
		return Component.translatable(onKeyPress == OnKeyPress.PERFORM
				? "jojo.keybind_mode.key_press.perform" : "jojo.keybind_mode.key_press.select");
	}

	protected Component activeTypeLabel(KeyActiveType activeType) {
		return Component.translatable(switch (activeType) {
			case ALWAYS -> "jojo.keybind_mode.is_active.always";
			case INSIDE_HUD -> "jojo.keybind_mode.is_active.inside_hud.short";
			case OUTSIDE_HUD -> "jojo.keybind_mode.is_active.outside_hud.short";
		});
	}

	protected Component activeTypeText(KeyActiveType activeType) {
		Component powerName = editedPower != null ? editedPower.getPowerType().getName(editedPower) : Component.empty();
		return switch (activeType) {
			case ALWAYS -> Component.translatable("jojo.keybind_mode.is_active.always");
			case INSIDE_HUD -> Component.translatable("jojo.keybind_mode.is_active.inside_hud", powerName);
			case OUTSIDE_HUD -> Component.translatable("jojo.keybind_mode.is_active.outside_hud", powerName);
		};
	}

	@Override
	public TabCategory getTabCategory() {
		return category;
	}

	@Override
	public Tab getTab() {
		return tab;
	}

	@Override public int getWindowWidth() { return 319; }
	@Override public boolean rightSideTabsEnabled() { return false; }

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		// the paper goes under the editor buttons
		int x = getWindowX(this);
		int y = getWindowY(this);
		int width = getWindowWidth();
		int height = getWindowHeight();
		BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), texture,
				x, y, width, height, 0,
				0, 0, width, height, 512, 512,
				BlitFloat.NO_TINT);

		renderTabs(guiGraphics, this);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float p_283123_) {
		super.render(guiGraphics, mouseX, mouseY, p_283123_);
		lastMouseX = mouseX;
		lastMouseY = mouseY;

		int x = getWindowX(this);
		int y = getWindowY(this);

		RenderSystem.enableBlend();
		// Sidebar with all the abilities

			// Power names
		for (var powerClassLine : powerClasses) {
			int powerIconX = SLOTS_X_OFFSET + x;
			int powerIconY = SLOTS_Y_OFFSET + y + powerClassLine.y;
			Power<?> power = powerClassLine.powerClass.get(minecraft.player);

			if (powerClassLine.powerClass == PowerClass.STAND) {
				PowerHud.renderClientStandIcon(guiGraphics.pose(), powerIconX, powerIconY);
			}
			else {
				ResourceLocation powerTypeId = power.getPowerType().getId();
				ResourceLocation powerIcon = ResourceLocation.fromNamespaceAndPath(powerTypeId.getNamespace(), "textures/power/" + powerTypeId.getPath() + ".png");
				BlitFloat.blit(guiGraphics.pose(), minecraft, powerIcon, powerIconX, powerIconY, 16, 16, 0, BlitFloat.NO_TINT);
			}
			if (powerClassLine.powerClass == editedPowerClass) {
				guiGraphics.renderOutline(powerIconX - 1, powerIconY - 1, 18, 18, 0xFF404040);
			}
		}

			// Ability icons
		AbilityIconSprites abilityIconSprites = StandSkinsLoader.getInstance().abilityIcons;
		PoseStack pose = guiGraphics.pose();
		StandSkin standSkin = StandSkinsLoader.getCurSkin();

		for (AbilityEntry ability : mainAbilities) {
			Power<?> abilityCtx = minecraft.player != null ? ability.ability.getUserPower(minecraft.player) : null;
			TextureAtlasSprite abilitySprite = abilityIconSprites.getAbilityIcon(ability.ability, abilityCtx, standSkin);
			int abilityX = ability.x + x;
			int abilityY = ability.y + y;

			abilitySlot.render(pose, abilityX, abilityY);
			BlitFloat.blit(pose, minecraft, abilitySprite,
					abilityX + (SLOT_WIDTH - ICON_WIDTH) / 2,
					abilityY + (SLOT_HEIGHT - ICON_HEIGHT) / 2, ICON_WIDTH, ICON_HEIGHT, 0, BlitFloat.NO_TINT);
			if (ability.equals(selectedAbility)) {
				guiGraphics.renderOutline(abilityX, abilityY, SLOT_WIDTH, SLOT_HEIGHT, 0xFFD0A000);
			}
		}

		// Editor: hints, hotbars and custom keys
		HotbarSlot hoveredSlot = null;
		SlotPos hoveredPos = getHotbarSlotAt(mouseX, mouseY);
		if (scheme == null) {
			guiGraphics.drawString(font, Component.translatable("jojo.screen.edit_hud_layout.no_power"), x + EDITOR_X, y + 6, 0x404040, false);
		}
		else {
			guiGraphics.drawString(font, Component.translatable("jojo.screen.edit_hud_layout.hint.lmb"), x + EDITOR_X, y + 5, 0x404040, false);
			guiGraphics.drawString(font, Component.translatable("jojo.screen.edit_hud_layout.hint.rmb"), x + EDITOR_X, y + 14, 0x404040, false);

			MoveGroup lastGroup = null;
			for (SlotPos pos : slotPositions) {
				if (pos.group != lastGroup && scheme.moveGroups.size() > 1) {
					guiGraphics.drawString(font, pos.group.name, x + pos.x, y + pos.y - 10, 0x404040, false);
				}
				lastGroup = pos.group;
				HotbarSlot slot = pos.hotbar.layout.get(pos.index);
				if (pos.hotbar == dragHotbar && pos.index == dragFrom) {
					abilitySlot.render(pose, x + pos.x, y + pos.y);
					continue;
				}
				renderHotbarSlot(guiGraphics, slot, x + pos.x, y + pos.y, abilityIconSprites, standSkin);
				if (dragHotbar != null && pos == hoveredPos && pos.hotbar == dragHotbar) {
					guiGraphics.renderOutline(x + pos.x, y + pos.y, SLOT_WIDTH, SLOT_HEIGHT, 0xFF00A000);
				}
			}
			if (dragHotbar != null && dragFrom >= 0 && dragFrom < dragHotbar.layout.size()) {
				pose.pushPose();
				pose.translate(0, 0, 200);
				renderHotbarSlot(guiGraphics, dragHotbar.layout.get(dragFrom), mouseX - SLOT_WIDTH / 2, mouseY - SLOT_HEIGHT / 2, abilityIconSprites, standSkin);
				pose.popPose();
			}
			else if (hoveredPos != null) {
				hoveredSlot = hoveredPos.hotbar.layout.get(hoveredPos.index);
			}

			guiGraphics.drawString(font, Component.translatable("jojo.screen.edit_hud_layout.keybinds"), x + EDITOR_X, y + keybindsY, 0x404040, false);
			for (int row = 0; row < keybindRowsShown && keybindScroll + row < customBinds.size(); row++) {
				Bind bind = customBinds.get(keybindScroll + row).bind;
				int rowY = y + keybindRowsY + row * KEYBIND_ROW_HEIGHT;
				abilitySlot.render(pose, x + 6, rowY);
				Ability ability = editedPower.getMoveset().getAbility(bind.ability.abilityName());
				if (ability != null) {
					BlitFloat.blit(pose, minecraft, abilityIconSprites.getAbilityIcon(ability, editedPower, standSkin),
							x + 7, rowY + 1, ICON_WIDTH, ICON_HEIGHT, 0, BlitFloat.NO_TINT);
				}
			}
		}


		AbilityEntry hoveredMovesetAbility = getMovesetAbilitySlotAt(mouseX, mouseY);
		if (hoveredMovesetAbility != null) {
			Ability ability = hoveredMovesetAbility.ability;
			TooltipParams.set(TooltipParams.paperStyle());
			setTooltipForNextRenderPass(ability.getName(ability.getUserPower(minecraft.player)).copy().withStyle(ChatFormatting.BLACK));
		}
		else if (hoveredSlot != null) {
			Ability ability = slotAbility(hoveredSlot);
			List<FormattedCharSequence> lines = new ArrayList<>();
			if (ability != null) {
				lines.add(ability.getName(editedPower).copy().withStyle(ChatFormatting.BLACK).getVisualOrderText());
			}
			if (hoveredSlot.hidden) {
				lines.add(Component.translatable("jojo.screen.edit_hud_layout.hidden").withStyle(ChatFormatting.DARK_RED).getVisualOrderText());
			}
			if (!lines.isEmpty()) {
				TooltipParams.set(TooltipParams.paperStyle());
				setTooltipForNextRenderPass(lines);
			}
		}
		else if (scheme != null && isInKeybindsHeader(mouseX, mouseY)) {
			setTooltipForNextRenderPass(font.split(Component.translatable("jojo.screen.edit_hud_layout.hint.keybinds"), 200));
		}
		else if (scheme != null && mouseY - y >= 4 && mouseY - y < HOTBARS_Y - 2 && mouseX - x >= EDITOR_X && mouseX - x < 226) {
			setTooltipForNextRenderPass(font.split(Component.translatable("jojo.screen.edit_hud_layout.hint.hotbars"), 200));
		}
		else {
			renderTabTooltip(guiGraphics, this, mouseX, mouseY);
		}
	}

	protected void renderHotbarSlot(GuiGraphics guiGraphics, HotbarSlot slot, int slotX, int slotY,
			AbilityIconSprites abilityIconSprites, StandSkin standSkin) {
		PoseStack pose = guiGraphics.pose();
		abilitySlot.render(pose, slotX, slotY);
		Ability ability = slotAbility(slot);
		if (ability != null) {
			BlitFloat.blit(pose, minecraft, abilityIconSprites.getAbilityIcon(ability, editedPower, standSkin),
					slotX + (SLOT_WIDTH - ICON_WIDTH) / 2, slotY + (SLOT_HEIGHT - ICON_HEIGHT) / 2,
					ICON_WIDTH, ICON_HEIGHT, 0, BlitFloat.NO_TINT);
		}
		if (slot.hidden || !scheme.hotbarsEnabled) {
			// disabled slots are greyed out, hidden ones also crossed
			guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_WIDTH - 1, slotY + SLOT_HEIGHT - 1, 0xA0505050);
			if (slot.hidden) {
				guiGraphics.fill(slotX + 2, slotY + SLOT_HEIGHT / 2 - 1, slotX + SLOT_WIDTH - 2, slotY + SLOT_HEIGHT / 2 + 1, 0xFFB02020);
			}
		}
	}

	@Nullable
	protected Ability slotAbility(HotbarSlot slot) {
		String abilityName = slot.layoutKey();
		return abilityName != null && editedPower != null ? editedPower.getMoveset().getAbility(abilityName) : null;
	}

	@Nullable
	protected AbilityEntry getMovesetAbilitySlotAt(int mouseX, int mouseY) {
		mouseX -= getWindowX(this);
		mouseY -= getWindowY(this);
		for (AbilityEntry abilitySlot : mainAbilities) {
			if (abilitySlot.x <= mouseX && abilitySlot.x + SLOT_WIDTH > mouseX
					&& abilitySlot.y <= mouseY && abilitySlot.y + SLOT_HEIGHT > mouseY) {
				return abilitySlot;
			}
		}

		return null;
	}

	@Nullable
	protected SlotPos getHotbarSlotAt(double mouseX, double mouseY) {
		mouseX -= getWindowX(this);
		mouseY -= getWindowY(this);
		for (SlotPos pos : slotPositions) {
			if (pos.x <= mouseX && pos.x + SLOT_WIDTH > mouseX
					&& pos.y <= mouseY && pos.y + SLOT_HEIGHT > mouseY) {
				return pos;
			}
		}
		return null;
	}

	@Nullable
	protected PowerClassName getPowerIconAt(double mouseX, double mouseY) {
		mouseX -= getWindowX(this) + SLOTS_X_OFFSET;
		mouseY -= getWindowY(this) + SLOTS_Y_OFFSET;
		for (PowerClassName line : powerClasses) {
			if (mouseX >= 0 && mouseX < 16 && mouseY >= line.y && mouseY < line.y + 16) {
				return line;
			}
		}
		return null;
	}

	protected boolean isInKeybindsHeader(double mouseX, double mouseY) {
		double relX = mouseX - getWindowX(this);
		double relY = mouseY - getWindowY(this);
		return relX >= EDITOR_X && relX < 226 && relY >= keybindsY && relY < keybindsY + 10;
	}

	protected void selectPower(PowerClass<?> powerClass) {
		if (powerClass != editedPowerClass) {
			editedPowerClass = powerClass;
			capturingBind = null;
			keybindScroll = 0;
			dragHotbar = null;
		}
		rebuildWidgets();
	}

	protected void setCapturedKey(InputConstants.Key key, KeyModifier modifier) {
		capturingBind.input = AllControlSchemes.toInput(key, modifier);
		capturingBind = null;
		keyCapture.reset();
		onEdited();
	}

	/** Modifier of a captured key: the one pressed during this capture, else whichever is held. */
	protected KeyModifier capturedModifier() {
		return keyCapture.modifierKey() != ControlSchemeEditorInput.KeyCapture.NO_KEY
				? KeyModifier.getKeyModifier(InputConstants.getKey(keyCapture.modifierKey(), keyCapture.modifierScan()))
				: KeyModifier.getActiveModifier();
	}

	/** 1.16 setCustomKeybind: the slot's ability gets the key, reusing its first custom key in that group. */
	protected void bindSlotAbility(SlotPos pos, InputConstants.Key key) {
		HotbarSlot slot = pos.hotbar.layout.get(pos.index);
		String abilityName = slot.layoutKey();
		if (abilityName == null || slotAbility(slot) == null) return;
		Bind bind = null;
		for (Bind groupBind : pos.group.binds) {
			if (groupBind.custom && groupBind.ability.abilityName().equals(abilityName)) {
				bind = groupBind;
				break;
			}
		}
		if (bind == null) {
			bind = AllControlSchemes.addCustomBind(scheme, pos.group, abilityName);
		}
		bind.input = AllControlSchemes.toInput(key, KeyModifier.getActiveModifier());
		if (capturingBind == bind) capturingBind = null;
		// scroll the key list to the edited row
		layoutEditor();
		for (int row = 0; row < customBinds.size(); row++) {
			if (customBinds.get(row).bind == bind) {
				if (row < keybindScroll || row >= keybindScroll + keybindRowsShown) {
					keybindScroll = Math.max(0, Math.min(row, customBinds.size() - keybindRowsShown));
				}
				break;
			}
		}
		onEdited();
	}

	/** Hotbar number key (1.16 getNumKey), -1 for other keys. */
	protected int hotbarNumKey(int keyCode, int scanCode) {
		InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
		for (int i = 0; i < minecraft.options.keyHotbarSlots.length; i++) {
			if (minecraft.options.keyHotbarSlots[i].isActiveAndMatches(key)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (capturingBind != null) {
			// 1.16 key capture also took mouse buttons
			setCapturedKey(InputConstants.Type.MOUSE.getOrCreate(button), capturedModifier());
			return true;
		}
		if (clickTab(mouseX, mouseY, button, this)) return true;
		if (super.mouseClicked(mouseX, mouseY, button)) return true;

		if (scheme != null) {
			SlotPos slotPos = getHotbarSlotAt(mouseX, mouseY);
			if (slotPos != null) {
				if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
					dragHotbar = slotPos.hotbar;
					dragFrom = slotPos.index;
					return true;
				}
				if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					HotbarSlot slot = slotPos.hotbar.layout.get(slotPos.index);
					slot.hidden = !slot.hidden;
					onEdited();
					return true;
				}
				if (ControlSchemeEditorInput.bindsMouseButton(button)) {
					bindSlotAbility(slotPos, InputConstants.Type.MOUSE.getOrCreate(button));
					return true;
				}
			}
		}

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			AbilityEntry ability = getMovesetAbilitySlotAt((int) mouseX, (int) mouseY);
			if (ability != null) {
				selectedAbility = ability;
				selectPower(ability.ability.abilityId.powerClass());
				return true;
			}
			PowerClassName powerIcon = getPowerIconAt(mouseX, mouseY);
			if (powerIcon != null) {
				selectPower(powerIcon.powerClass);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragHotbar != null) {
			SlotPos target = getHotbarSlotAt(mouseX, mouseY);
			Hotbar hotbar = dragHotbar;
			int from = dragFrom;
			dragHotbar = null;
			dragFrom = -1;
			if (target != null && target.hotbar == hotbar && target.index != from
					&& from >= 0 && from < hotbar.layout.size()) {
				HotbarSlot moved = hotbar.layout.remove(from);
				hotbar.layout.add(target.index, moved);
				onEdited();
			}
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		SlotPos hovered = scheme != null && scrollY != 0 ? getHotbarSlotAt(mouseX, mouseY) : null;
		if (hovered != null) {
			// 1.16: scrolling over a slot moves the HUD selection of its hotbar
			Hotbar hotbar = hovered.hotbar;
			boolean[] shown = new boolean[hotbar.slots.size()];
			for (int i = 0; i < shown.length; i++) {
				shown[i] = hotbar.slots.get(i).showAbility() != null;
			}
			int index = ControlSchemeEditorInput.cycleSlot(hotbar.slotIndex, shown, scrollY > 0);
			if (index >= 0) hotbar.slotIndex = index;
			return true;
		}
		double relY = mouseY - getWindowY(this);
		if (scheme != null && scrollY != 0 && relY >= keybindsY && relY < BOTTOM_BUTTONS_Y) {
			int maxScroll = Math.max(0, customBinds.size() - keybindRowsShown);
			int newScroll = Math.max(0, Math.min(maxScroll, keybindScroll + (scrollY > 0 ? -1 : 1)));
			if (newScroll != keybindScroll) {
				keybindScroll = newScroll;
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (capturingBind != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				setCapturedKey(InputConstants.UNKNOWN, KeyModifier.NONE);
			}
			else {
				InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
				// a modifier keeps the capture open for the key it modifies (1.16)
				if (!keyCapture.waitsAfterPress(keyCode, scanCode, KeyModifier.isKeyCodeModifier(key))) {
					setCapturedKey(key, capturedModifier());
				}
			}
			return true;
		}
		if (scheme != null && keySlotAction(keyCode, scanCode)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** 1.16 keys over the editor hotbars: number keys move the dragged or hovered slot, other keys bind to it. */
	protected boolean keySlotAction(int keyCode, int scanCode) {
		SlotPos hovered = getHotbarSlotAt(lastMouseX, lastMouseY);
		Hotbar moveHotbar = dragHotbar != null ? dragHotbar : hovered != null ? hovered.hotbar : null;
		int moveFrom = dragHotbar != null ? dragFrom : hovered != null ? hovered.index : -1;
		int numKey = hotbarNumKey(keyCode, scanCode);
		InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
		boolean hoveredHasAbility = hovered != null && slotAbility(hovered.hotbar.layout.get(hovered.index)) != null;
		switch (ControlSchemeEditorInput.slotKey(numKey, moveHotbar != null ? moveHotbar.layout.size() : -1,
				hoveredHasAbility, keyCode, KeyModifier.isKeyCodeModifier(key))) {
		case MOVE -> {
			boolean moved = ControlSchemeEditorInput.moveSlot(moveHotbar.layout, moveFrom, numKey);
			dragHotbar = null;
			dragFrom = -1;
			if (moved) onEdited();
			return true;
		}
		case BIND -> {
			bindSlotAbility(hovered, key);
			return true;
		}
		default -> {
			return false;
		}
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (capturingBind != null && keyCapture.bindsModifierOnRelease(keyCode)) {
			// a modifier released on its own is the key
			setCapturedKey(InputConstants.getKey(keyCode, scanCode), KeyModifier.NONE);
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

}
