package com.github.standobyte.jojo.client.ui;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.network.c2s.ClAngeloRockButtonPacket;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.entity_possessionv2.LivingComponentPossession;
import com.github.standobyte.jojo.subsystems.itemtracking.OriginalItemPosComponent;
import com.github.standobyte.jojoimpl.stands.goldexperience.GEItemMarkEffect;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class GuiEventHandler {

	@SubscribeEvent
	public static void addAngeloRockButtons(ScreenEvent.Init.Post event) {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = event.getScreen();
		if (mc.player == null || !(screen instanceof ChatScreen)) {
			return;
		}

		Entity possessed = LivingComponentPossession.getEntityPossessedBy(mc.player);
		if (possessed != null && possessed.getType() == ModEntityTypes.ANGELO_ROCK.get()) {
			int x = screen.width / 2 - 100;
			int y = screen.height - 40;
			Button respawnButton = Button.builder(
					Component.translatable(mc.level != null && mc.level.getLevelData().isHardcore()
							? "deathScreen.spectate"
							: "deathScreen.respawn"),
					button -> PacketDistributor.sendToServer(ClAngeloRockButtonPacket.respawn()))
					.bounds(x, y, 200, 20)
					.build();
			event.addListener(respawnButton);

			Button gruntButton = Button.builder(
					Component.translatable("jojo_ripples.subtitle.angelo_rock_grunt"),
					button -> PacketDistributor.sendToServer(ClAngeloRockButtonPacket.grunt()))
					.bounds(x - 64, y, 60, 20)
					.build();
			event.addListener(gruntButton);
		}
	}

	@SubscribeEvent
	public static void addTooltipLines(ItemTooltipEvent event) {
		Player player = event.getEntity();
		ItemStack item = event.getItemStack();
		if (player == null || item.isEmpty()) {
			return;
		}

		StandPower standPower = StandPower.get(player);
		OriginalItemPosComponent originalPos = item.get(ModItemDataComponents.ORIGINAL_POS.get());
		if (originalPos != null && originalPos.matchesDimension(player.level())
				&& standPower != null && standPower.getPowerType() == ModStands.CRAZY_DIAMOND.get()) {
			BlockPos pos = originalPos.blockPos();
			event.getToolTip().add(Component.translatable("jojo.crazy_diamond.block_checkpoint.tooltip",
					pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.RED));
		}

		if (GEItemMarkEffect.isItemMarked(item, player)) {
			event.getToolTip().add(Component.translatable("jojo.ge_item_marked")
					.withStyle(style -> style.withColor(standUiColor(standPower))));
			event.getToolTip().add(Component.translatable("jojo.ge_item_marked.2")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static int standUiColor(StandPower standPower) {
		StandSkinsLoader loader = StandSkinsLoader.getInstance();
		StandSkin skin = loader != null ? loader.getSkin(standPower) : null;
		return skin != null ? skin.getColor() & 0xFFFFFF : 0xFFFFFF;
	}

//	@SubscribeEvent
//	public static void afterScreenRender(DrawScreenEvent.Post event) {
//		Screen screen = event.getGui();
//		float partialTick = screen.getMinecraft().getFrameTime();
//		if (screen instanceof DeathScreen) {
//			Component title = screen.getTitle();
//			if (title instanceof TranslationTextComponent && ((TranslationTextComponent) title).getKey().endsWith(".hardcore")) {
//				return;
//			}
//			renderToBeContinuedArrow(event.getMatrixStack(), screen, screen.width, screen.height, partialTick);
//		}
//	}
//
//	private static void renderToBeContinuedArrow(MatrixStack matrixStack, AbstractGui ui, int screenWidth, int screenHeight, float partialTick) {
//		int x = screenWidth - 5 - (int) ((screenWidth - 10) * Math.min(deathScreenTick + partialTick, 20F) / 20F);
//		int y = screenHeight - 29;
//		mc.textureManager.bind(ClientUtil.ADDITIONAL_UI);
//		ui.blit(matrixStack, x, y, 0, 231, 130, 25);
//		AbstractGui.drawCenteredString(matrixStack, mc.font, Component.translatable("jojo.to_be_continued"), x + 61, y + 8, 0x525544);
//	}
//
//	@SubscribeEvent(priority = EventPriority.LOW)
//	public static void addToScreen(ScreenEvent.Init.Post event) {
//		Screen screen = event.getScreen();
//		if (screen instanceof ControlsScreen) {
//			KeyBindingList controlList = ClientReflection.getControlList((ControlsScreen) screen);
//			List<KeyBindingList.Entry> keyEntries = controlList.children();
//
//			ListIterator<KeyBindingList.Entry> entriesIter = keyEntries.listIterator();
//			ClientModSettings modSettings = ClientModSettings.getInstance();
//			ClientModSettings.Settings modSettingsRead = ClientModSettings.getSettingsReadOnly();
//
//			boolean addHudScreenButtons;
//			LazyOptional<IStandPower> spOptional;
//			LazyOptional<INonStandPower> nspOptional;
//			if (mc.player != null) {
//				spOptional = IStandPower.getStandPowerOptional(mc.player);
//				nspOptional = INonStandPower.getNonStandPowerOptional(mc.player);
//				addHudScreenButtons = spOptional.map(IPower::hasPower).orElse(false) || nspOptional.map(IPower::hasPower).orElse(false);
//			}
//			else {
//				addHudScreenButtons = false;
//				spOptional = LazyOptional.empty();
//				nspOptional = LazyOptional.empty();
//			}
//
//			while (entriesIter.hasNext()) {
//				KeyBindingList.Entry entry = entriesIter.next();
//				if (entry instanceof KeyBindingList.KeyEntry) {
//					KeyBindingList.KeyEntry keyEntry = (KeyBindingList.KeyEntry) entry;
//					KeyBinding key = ClientReflection.getKey(keyEntry);
//					if (key == InputHandler.getInstance().attackHotbar) {
//						entriesIter.set(new HoldToggleKeyEntry(keyEntry, ClientReflection.getChangeButton(keyEntry), new ControlSettingToggleButton(40, 20, 
//								button -> {
//									modSettings.editSettings(s -> s.toggleLmbHotbar = !s.toggleLmbHotbar);
//									InputHandler.getInstance().setToggledHotbarControls(ControlScheme.Hotbar.LEFT_CLICK, false);
//								},
//								() -> modSettingsRead.toggleLmbHotbar)));
//					}
//					else if (key == InputHandler.getInstance().abilityHotbar) {
//						entriesIter.set(new HoldToggleKeyEntry(keyEntry, ClientReflection.getChangeButton(keyEntry), new ControlSettingToggleButton(40, 20, 
//								button -> {
//									modSettings.editSettings(s -> s.toggleRmbHotbar = !s.toggleRmbHotbar);
//									InputHandler.getInstance().setToggledHotbarControls(ControlScheme.Hotbar.RIGHT_CLICK, false);
//								},
//								() -> modSettingsRead.toggleRmbHotbar)));
//					}
//					else if (key == InputHandler.getInstance().disableHotbars) {
//						entriesIter.set(new HoldToggleKeyEntry(keyEntry, ClientReflection.getChangeButton(keyEntry), new ControlSettingToggleButton(40, 20, 
//								button -> {
//									modSettings.editSettings(s -> s.toggleDisableHotbars = !s.toggleDisableHotbars);
//									InputHandler.getInstance().setToggleHotbarsDisabled(false);
//								},
//								() -> modSettingsRead.toggleDisableHotbars)));
//					}
//				}
//				else if (addHudScreenButtons && entry instanceof KeyBindingList.CategoryEntry) {
//					KeyBindingList.CategoryEntry categoryEntry = (KeyBindingList.CategoryEntry) entry;
//					Component categoryName = ClientReflection.getName(categoryEntry);
//
//					IStandPower standPower = spOptional.resolve().get();
//					INonStandPower nonStandPower = nspOptional.resolve().get();
//					Button[] hudScreenButtons = new Button[standPower.hasPower() && nonStandPower.hasPower() ? 2 : 1];
//					int i = 0;
//					if (standPower.hasPower()) {
//						Component tooltip = Component.translatable("jojo.key.edit_hud.power_name", standPower.getName());
//						hudScreenButtons[i++] = new ImageVanillaButton((screen.width + mc.font.width(categoryName) + 10) / 2, -21, 
//								20, 20, 
//								0, 0, 16, 16, standPower.clGetPowerTypeIcon(), 16, 16, 
//								button -> {
//									HudLayoutEditingScreen hudScreen = new HudLayoutEditingScreen(PowerClassification.STAND);
//									mc.setScreen(hudScreen);
//								}, 
//								(button, matrixStack, mouseX, mouseY) -> screen.renderTooltip(matrixStack, tooltip, mouseX, mouseY),
//								tooltip);
//					}
//					if (nonStandPower.hasPower()) {
//						Component tooltip = Component.translatable("jojo.key.edit_hud.power_name", nonStandPower.getName());
//						hudScreenButtons[i] = new ImageVanillaButton((screen.width + mc.font.width(categoryName) + 10) / 2 + (i++) * 24, -21, 
//								20, 20, 
//								0, 0, 16, 16, nonStandPower.clGetPowerTypeIcon(), 16, 16, 
//								button -> {
//									HudLayoutEditingScreen hudScreen = new HudLayoutEditingScreen(PowerClassification.NON_STAND);
//									mc.setScreen(hudScreen);
//								}, 
//								(button, matrixStack, mouseX, mouseY) -> screen.renderTooltip(matrixStack, tooltip, mouseX, mouseY),
//								tooltip);
//					}
//
//					if (InputHandler.HUD_CATEGORY.equals(((TranslationTextComponent) categoryName).getKey())) {
//						entriesIter.set(new CategoryWithButtonsEntry(controlList, categoryName, hudScreenButtons));
//					}
//				}
//			}
//
//			if (HudLayoutEditingScreen.scrollCtrlListTo != null) {
//				Predicate<KeyBindingList.Entry> scrollTo = HudLayoutEditingScreen.scrollCtrlListTo;
//				HudLayoutEditingScreen.scrollCtrlListTo = null;
//				OptionalInt index = IntStream.range(0, controlList.children().size())
//						.filter(i -> {
//							KeyBindingList.Entry entry = controlList.children().get(i);
//							return scrollTo.test(entry);
//						})
//						.findFirst();
//				index.ifPresent(i -> {
//					controlList.setScrollAmount(ClientReflection.getRowTop(controlList, i) - controlList.getTop());
//				});
//			}
//		}
//
//		else if (screen instanceof ChatScreen) {
//			Entity possessed = IPlayerPossess.getPossessedEntity(mc.player);
//			if (possessed != null && possessed.getType() == ModEntityTypes.ANGELO_ROCK.get()) {
//				int x = screen.width / 2 - 100;
//				int y = screen.height - 40;
//				Button angeloRockDieButton = new Button(x, y, 200, 20, 
//						Component.translatable(mc.level.getLevelData().isHardcore() ? "deathScreen.spectate" : "deathScreen.respawn"), 
//						button -> PacketManager.sendToServer(ClAngeloRockButtonPacket.respawn()));
//				event.addWidget(angeloRockDieButton);
//
//				Button angeloRockGruntButton = new ImageVanillaButton(x - 24, y, 20, 20, 
//						238, 150, 
//						ClientUtil.ADDITIONAL_UI, 256, 256,
//						button -> PacketManager.sendToServer(ClAngeloRockButtonPacket.grunt())) {
//					@Override public void playDownSound(SoundHandler pHandler) {}
//				};
//				event.addWidget(angeloRockGruntButton);
//			}
//		}
//	}
//
//	@SubscribeEvent
//	public static void onScreenOpened(GuiOpenEvent event) {
//		Screen screen = event.getGui();
//		if (screen instanceof MainMenuScreen) {
//			String splash = CustomResources.getModSplashes().overrideSplash();
//			if (splash != null) {
//				ClientReflection.setSplash((MainMenuScreen) screen, splash);
//			}
//		}
//	}
//
//	@SubscribeEvent(priority = EventPriority.LOWEST)
//	public static void onScreenOpened2(GuiOpenEvent event) {
//		IJojoScreen.rememberScreenTab(event.getGui());
//	}
}
