package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.util.OptionalInt;

import com.github.standobyte.jojo.client.ClientGlobals;
import com.github.standobyte.jojo.client.VisualPipelineDiagnostics;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.entityglow.EntityGlowChannel;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceHealAbility;

import net.minecraft.client.Minecraft;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

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
}
