package rotp.core.impl.stands.goldexperience.client;

import java.util.List;
import java.util.OptionalInt;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.core.client.ClientGlobals;
import rotp.core.client.VisualPipelineDiagnostics;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.ui.utils.GuiIcon;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entityglow.EntityGlowChannel;
import rotp.core.subsystems.soul.SoulEntity;
import rotp.core.impl.stands.goldexperience.GoldExperienceHealAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorReadout;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorReadout.ReadoutKind;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorReadout.ReadoutLine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class GoldExperienceLifeDetectorClient {
	private static final String LIFE_DETECTOR_ID = "life_detector";
	private static final double MAX_SCAN_RADIUS = 32.0;
	private static final int GLOW_TICKS = 80;
	private static final int FALLBACK_LIFE_COLOR = 0xFFE600;

	private static int activeTicks;
	private static int activeActionId = -1;

	private GoldExperienceLifeDetectorClient() {
	}

	public static void tick(Minecraft mc) {
		if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
			resetActionState();
			return;
		}
		StandEntity stand = ClientGlobals.playerStandEntity;
		if (stand == null || !stand.isAlive()) {
			resetActionState();
			return;
		}
		EntityActionInstance action = stand.getCurStandAction();
		if (!isLifeDetectorPerform(action)) {
			resetActionState();
			return;
		}
		if (activeActionId != action.id) {
			activeActionId = action.id;
			activeTicks = Math.max(0, (int) action.getPhaseTick());
		}
		activeTicks = Math.max(activeTicks + 1, (int) Math.ceil(action.getPhaseTick()));
		scan(mc.level, mc.player, stand, Math.min(Math.max(activeTicks, action.getPhaseTick()), MAX_SCAN_RADIUS));
	}

	private static void resetActionState() {
		activeTicks = 0;
		activeActionId = -1;
	}

	private static boolean isLifeDetectorPerform(EntityActionInstance action) {
		if (action == null || action.getPhase() != ActionPhase.PERFORM) {
			return false;
		}
		AbilityId abilityId = action.ability.getAbilityId();
		return abilityId != null && LIFE_DETECTOR_ID.equals(abilityId.nameInMoveset());
	}

	private static void scan(Level level, LivingEntity user, StandEntity stand, double radius) {
		if (radius <= 0) {
			return;
		}
		AABB area = new AABB(
				stand.getX() - radius, stand.getY() - radius, stand.getZ() - radius,
				stand.getX() + radius, stand.getY() + radius, stand.getZ() + radius);
		OptionalInt color = OptionalInt.of(lifeDetectorColor(user));
		int livingTargets = 0;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
			if (target == user || target == stand || !GoldExperienceHealAbility.isLivingHealingTarget(target)) {
				continue;
			}
			if (target.isDeadOrDying()) {
				EntityGlowChannel.GE_LIFE_DETECTOR.clear(target);
			}
			else {
				EntityGlowChannel.GE_LIFE_DETECTOR.apply(target, color, GLOW_TICKS);
				++livingTargets;
			}
		}
		int soulTargets = 0;
		for (SoulEntity soul : level.getEntitiesOfClass(SoulEntity.class, area)) {
			EntityGlowChannel.GE_LIFE_DETECTOR.apply(soul, color, GLOW_TICKS);
			++soulTargets;
		}
		if (livingTargets + soulTargets > 0) {
			VisualPipelineDiagnostics.logOnce("ge_life_detector_scan_hit",
					"GE life detector client scan applied glow: radius={}, livingTargets={}, soulTargets={}, standId={}, standPos={}.",
					radius, livingTargets, soulTargets, stand.getId(), stand.position());
		}
		else if (radius >= 8.0D) {
			VisualPipelineDiagnostics.logOnce("ge_life_detector_scan_empty",
					"GE life detector client scan active but found no glow targets: radius={}, standId={}, standPos={}.",
					radius, stand.getId(), stand.position());
		}
	}

	private static int lifeDetectorColor(LivingEntity user) {
		StandPower standPower = StandPower.get(user);
		if (standPower != null && StandSkinsLoader.getInstance() != null) {
			StandSkin skin = StandSkinsLoader.getInstance().getSkin(standPower);
			if (skin != null) {
				return highVisibilityColor(skin.getColor());
			}
		}
		return FALLBACK_LIFE_COLOR;
	}

	private static int highVisibilityColor(int color) {
		int red = FastColor.ARGB32.red(color);
		int green = FastColor.ARGB32.green(color);
		int blue = FastColor.ARGB32.blue(color);
		int max = Math.max(red, Math.max(green, blue));
		if (max > 0 && max < 255) {
			float scale = 255.0F / max;
			red = Math.min(255, Math.round(red * scale));
			green = Math.min(255, Math.round(green * scale));
			blue = Math.min(255, Math.round(blue * scale));
		}
		return FastColor.ARGB32.color(0, red, green, Math.max(blue, 64));
	}


	// 1.16 ClientEventHandler.hudRenderEntityGEDetectorData: the readout next to the detected entity looked at.
	private static final int ICON_WIDTH = 17;
	private static final int LINE_HEIGHT = 10;
	private static final GuiIcon HEALTH_ICON = new GuiIcon(JojoMod.resLoc("textures/gui/sprites/health.png"), 9, 9);

	private static Entity readoutTarget;
	private static ClientUtil.PosOnScreen readoutTargetPos;

	@SubscribeEvent
	public static void findReadoutTarget(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		readoutTarget = null;
		readoutTargetPos = null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		Entity target = EntityGlowChannel.GE_LIFE_DETECTOR.mostLookedAt(
				mc.level, event.getCamera().getPosition(), mc.player.getLookAngle());
		if (target == null) {
			return;
		}
		float partialTick = ClientUtil.partialTick(event.getPartialTick(), target);
		Vec3 pos = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5F, 0);
		readoutTarget = target;
		readoutTargetPos = ClientUtil.posOnScreen(pos, event.getCamera(), event.getModelViewMatrix(), event.getProjectionMatrix());
	}

	public static void renderHud(GuiGraphics guiGraphics) {
		Entity entity = readoutTarget;
		ClientUtil.PosOnScreen entityPos = readoutTargetPos;
		Minecraft mc = Minecraft.getInstance();
		if (entity == null || entityPos == null || !entityPos.isOnScreen() || entity.level() != mc.level) {
			return;
		}
		List<ReadoutLine> lines = GoldExperienceLifeDetectorReadout.readout(entity);
		if (lines.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int screenWidth = guiGraphics.guiWidth();
		int screenHeight = guiGraphics.guiHeight();
		int anchorX = (int) (screenWidth * entityPos.pos().x);
		int anchorY = (int) (screenHeight * (1 - entityPos.pos().y));

		int width = 0;
		for (ReadoutLine line : lines) {
			width = Math.max(width, ICON_WIDTH + font.width(line.percent() + "%"));
		}
		int height = lines.size() * LINE_HEIGHT - 2;
		// 1.16 GuiUtils.drawHoveringText placement
		int x = anchorX + 12;
		if (x + width + 4 > screenWidth) {
			x = anchorX - 16 - width;
		}
		int y = anchorY - 12;
		if (y < 4) {
			y = 4;
		}
		else if (y + height + 4 > screenHeight) {
			y = screenHeight - height - 4;
		}

		// Stand UI colour border, as 1.16
		int uiColor = standUiColor(mc.player);
		int background = 0xF0000000
				| ((FastColor.ARGB32.red(uiColor) / 5) << 16) | ((FastColor.ARGB32.green(uiColor) / 5) << 8) | (FastColor.ARGB32.blue(uiColor) / 5);
		int borderStart = 0x50000000 | uiColor;
		int borderEnd = (borderStart & 0xFEFEFE) >> 1 | borderStart & 0xFF000000;
		TooltipRenderUtil.renderTooltipBackground(guiGraphics, x, y, width, height, 0,
				background, background, borderStart, borderEnd);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		int lineY = y;
		for (ReadoutLine line : lines) {
			renderIcon(guiGraphics, line.kind(), entity, x, lineY);
			guiGraphics.drawString(font, line.percent() + "%", x + ICON_WIDTH, lineY, 0xFFFFFFFF);
			lineY += LINE_HEIGHT;
		}
		RenderSystem.disableBlend();
	}

	private static void renderIcon(GuiGraphics guiGraphics, ReadoutKind kind, Entity entity, int x, int y) {
		switch (kind) {
		case HEALTH -> HEALTH_ICON.render(guiGraphics.pose(), x, y - 1, 9, 9, BlitFloat.NO_TINT);
		case ENERGY -> {
			GuiIcon icon = energyIcon(entity);
			if (icon != null) {
				icon.render(guiGraphics.pose(), x - 1, y - 2, 10, 10, BlitFloat.NO_TINT);
			}
		}
		case STAMINA -> PowerHud.Stamina.ICON.render(guiGraphics.pose(), x - 1, y - 2, 10, 10, BlitFloat.NO_TINT);
		case RESOLVE -> PowerHud.Resolve.HORIZONTAL_FULL.render(guiGraphics.pose(), x, y - 1, 16, 8, BlitFloat.NO_TINT);
		}
	}

	private static GuiIcon energyIcon(Entity entity) {
		PlayerPower power = entity instanceof LivingEntity living ? PlayerPower.get(living) : null;
		var type = power != null ? power.getPowerType() : null;
		if (type == null) return null;
		if (type == ModPlayerPowers.HAMON.get()) return PowerHud.HamonEnergy.ICON;
		if (type == ModPlayerPowers.VAMPIRISM.get()) return PowerHud.VampireEnergy.ICON;
		if (type == ModPlayerPowers.ZOMBIE.get()) return PowerHud.ZombieEnergy.ICON;
		if (type == ModPlayerPowers.PILLAR_MAN.get()) return PowerHud.PillarmanEnergy.ICON;
		return null;
	}

	// 1.16 ActionsOverlayGui.getPowerUiColor(STAND): the Stand skin colour, white without one
	private static int standUiColor(LivingEntity user) {
		StandPower standPower = user != null ? StandPower.get(user) : null;
		if (standPower != null && StandSkinsLoader.getInstance() != null) {
			StandSkin skin = StandSkinsLoader.getInstance().getSkin(standPower);
			if (skin != null) {
				return skin.getColor() & 0xFFFFFF;
			}
		}
		return 0xFFFFFF;
	}
}
