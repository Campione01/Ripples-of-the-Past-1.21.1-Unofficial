package com.github.standobyte.jojo.client.shader;

import java.util.Optional;

import javax.annotation.Nullable;

import org.joml.Vector2f;

import com.github.standobyte.jojo.client.ClientPowerCache;
import com.github.standobyte.jojo.client.ResourcePathChecker;
import com.github.standobyte.jojo.client.ClientTimeStopHandler;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopClientAwareness;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class TimeStopShaderManager {
    private static final ResourceLocation TIME_STOP_TW = JojoMod.resLoc("time_stop_tw");
    private static final ResourceLocation TIME_STOP_SP = JojoMod.resLoc("time_stop_sp");
    private static final ResourceLocation TIME_STOP_TW_OLD = JojoMod.resLoc("time_stop_tw_old");
    private static final ResourceLocation TIME_STOP_SP_OLD = JojoMod.resLoc("time_stop_sp_old");

    private ResourceLocation selectedShader;
    private ResourceLocation previousShader;
    private final Vector2f center = new Vector2f(0.5f, 0.5f);
    private float ticks;
    private float length = 100.0f;
    private float effectLength = 35.0f;
    private boolean active;
    private int activeInstanceId = Integer.MIN_VALUE;
    private int visualInstanceId = Integer.MIN_VALUE;
    private int visualUserId = -1;
    private String visualRoute = "";
    private boolean openingVisualPending;
    private boolean justActivated;
    private boolean justReset;
    private boolean openingEffectSuppressed;
    private boolean shaderRestartRequested;
    private int loggedActivationInstanceId = Integer.MIN_VALUE;
    private ResourceLocation loggedSelectedShader;

    public void setTimeStopVisuals(TimeStopState.Instance instance) {
        if (instance == null || !instance.isActive() || openingVisualPending) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        this.visualInstanceId = instance.id();
        this.visualUserId = instance.userId();
        this.visualRoute = instance.visualRoute();
        this.openingVisualPending = true;
        this.openingEffectSuppressed = false;
        JojoMod.getLogger().info("Time stop visual queued: instance={}, user={}, route={}, stand={}, ticksLeft={}, ticksPassed={}",
                instance.id(), instance.userId(), instance.visualRoute(),
                instance.standTypeId().map(ResourceLocation::toString).orElse("<unknown>"),
                instance.ticksLeft(), instance.ticksPassed());
    }

    public void update(RenderLevelStageEvent event) {
        justActivated = false;
        justReset = false;
        boolean restartRequested = shaderRestartRequested;
        shaderRestartRequested = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            active = false;
            return;
        }
        Optional<TimeStopState.Instance> activeInstance = TimeStopState.getClientDisplayInstance(new ChunkPos(mc.player.blockPosition()));
        if (activeInstance.isEmpty()) {
            reset();
            return;
        }
        if (shouldSuppressShaderForAwareness()) {
            reset();
            return;
        }
        TimeStopState.Instance instance = activeInstance.get();
        if (restartRequested) {
            restartShaderPipeline();
        }
        boolean animationConfig = ClientModSettings.getSettingsReadOnly().timeStopAnimation;
        if (!this.active || this.activeInstanceId != instance.id() || openingVisualPending) {
            justActivated = true;
            openingEffectSuppressed = false;
		}
		this.active = true;
        this.activeInstanceId = instance.id();
		float partialTick = ClientUtil.partialTick(event.getPartialTick(), true);
		int timelineLength = ClientTimeStopHandler.getTimeStopLength();
		this.length = timelineLength > 0 ? timelineLength : instance.ticksPassed() + instance.ticksLeft();
		this.ticks = Math.min(this.length, ClientTimeStopHandler.getTimeStopTicks() + partialTick);
		this.effectLength = animationConfig && !openingEffectSuppressed ? 35.0f : 0.0f;
		this.previousShader = this.selectedShader;
		this.selectedShader = selectShaderPath(instance, animationConfig);
        logSelectedShader(instance, animationConfig);
        updateCenter(event, instance);
        openingVisualPending = false;
    }

    private void logSelectedShader(TimeStopState.Instance instance, boolean animationConfig) {
        if (selectedShader == null) {
            return;
        }
        if (loggedActivationInstanceId != instance.id() || !selectedShader.equals(loggedSelectedShader)) {
            loggedActivationInstanceId = instance.id();
            loggedSelectedShader = selectedShader;
            JojoMod.getLogger().info("Time stop shader selected: instance={}, shader={}, animationConfig={}, route={}, effectLength={}",
                    instance.id(), selectedShader, animationConfig,
                    !visualRoute.isBlank() ? visualRoute : instance.visualRoute(), effectLength);
        }
    }

    private boolean shouldSuppressShaderForAwareness() {
        return TimeStopClientAwareness.isRestrictive() && !TimeStopClientAwareness.canSee();
    }

    private ResourceLocation selectShaderPath(TimeStopState.Instance instance, boolean animationConfig) {
        ResourceLocation fallback = selectFallbackShaderPath(instance, animationConfig);
        ResourceLocation skinShader = selectSkinShaderPath(instance, animationConfig, fallback);
        return skinShader != null ? skinShader : fallback;
    }

    private ResourceLocation selectFallbackShaderPath(TimeStopState.Instance instance, boolean animationConfig) {
        String route = !visualRoute.isBlank() ? visualRoute : instance.visualRoute();
        if ("star_platinum_time_stop".equals(route)) {
            return animationConfig ? TIME_STOP_SP : TIME_STOP_SP_OLD;
        }
        if ("the_world_time_stop".equals(route)) {
            return animationConfig ? TIME_STOP_TW : TIME_STOP_TW_OLD;
        }
        if (instance.standTypeId().filter(ModStands.STAR_PLATINUM.get().getId()::equals).isPresent()) {
            return animationConfig ? TIME_STOP_SP : TIME_STOP_SP_OLD;
        }
        Entity user = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(instance.userId()) : null;
        if (user != null) {
            StandPower stand = StandPower.get(user instanceof net.minecraft.world.entity.LivingEntity living ? living : null);
            if (stand != null && stand.hasPower() && stand.getPowerType() == ModStands.STAR_PLATINUM.get()) {
                return animationConfig ? TIME_STOP_SP : TIME_STOP_SP_OLD;
            }
        }
        StandPower localStand = ClientPowerCache.getPower(PowerClass.STAND);
        if (localStand != null && localStand.hasPower() && localStand.getPowerType() == ModStands.STAR_PLATINUM.get()) {
            return animationConfig ? TIME_STOP_SP : TIME_STOP_SP_OLD;
        }
        return animationConfig ? TIME_STOP_TW : TIME_STOP_TW_OLD;
    }

    @Nullable
    private ResourceLocation selectSkinShaderPath(TimeStopState.Instance instance, boolean animationConfig, ResourceLocation fallback) {
        StandSkin skin = getTimeStopSkin(instance);
        if (skin == null) {
            return null;
        }

        ResourceLocation currentLayoutPath = JojoMod.resLoc("shaders/" + (animationConfig ? "time_stop.json" : "time_stop_old.json"));
        ResourcePathChecker currentLayout = skin.remapAssetPath(currentLayoutPath);
        if (currentLayout.resourceExists()) {
            return currentLayout.path;
        }

        ResourceLocation legacyLayoutPath = fallback.withPath(path -> "shaders/post/" + path + ".json");
        ResourcePathChecker legacyLayout = skin.remapAssetPath(legacyLayoutPath);
        return legacyLayout.resourceExists() ? legacyLayout.path : null;
    }

    @Nullable
    private StandSkin getTimeStopSkin(TimeStopState.Instance instance) {
        StandSkinsLoader loader = StandSkinsLoader.getInstance();
        if (loader == null) {
            return null;
        }
        if (instance.standTypeId().isPresent()) {
            StandSkin skin = loader.getSkinFromId(instance.standTypeId().get(), instance.selectedSkin());
            if (skin != null) {
                return skin;
            }
        }
        StandPower power = getTimeStopStandPower(instance);
        if (power == null) {
            return null;
        }
        return power.getStandInstance().map(loader::getSkin).orElse(null);
    }

    @Nullable
    private StandPower getTimeStopStandPower(TimeStopState.Instance instance) {
        Minecraft mc = Minecraft.getInstance();
        Entity user = mc.level != null ? mc.level.getEntity(instance.userId()) : null;
        if (visualUserId >= 0 && mc.level != null) {
            user = mc.level.getEntity(visualUserId);
        }
        if (user instanceof LivingEntity living) {
            StandPower stand = StandPower.get(living);
            if (stand != null && stand.hasPower()) {
                return stand;
            }
        }
        StandPower localStand = ClientPowerCache.getPower(PowerClass.STAND);
        return localStand != null && localStand.hasPower() ? localStand : null;
    }

    private void updateCenter(RenderLevelStageEvent event, TimeStopState.Instance instance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            center.set(0.5f, 0.5f);
            return;
		}
		Entity user = mc.level.getEntity(instance.userId());
		if (visualInstanceId != Integer.MIN_VALUE) {
			int userId = visualUserId >= 0 ? visualUserId : instance.userId();
			user = userId >= 0 ? mc.level.getEntity(userId) : null;
		}
		if (user == null) {
			suppressOpeningEffect();
			return;
		}
		if (openingEffectSuppressed) {
			effectLength = 0.0f;
			center.set(0.5f, 0.5f);
			return;
		}
		if (user == mc.player) {
			center.set(0.5f, 0.5f);
			return;
		}
		float partialTick = ClientUtil.partialTick(event.getPartialTick(), true);
		Vec3 pos = user.getPosition(partialTick).add(0, user.getBbHeight() * 0.5f, 0);
		ClientUtil.PosOnScreen posOnScreen = ClientUtil.posOnScreen(pos, event.getCamera(),
				event.getModelViewMatrix(), event.getProjectionMatrix());
		if (!posOnScreen.isOnScreen()) {
			if (justActivated) {
				suppressOpeningEffect();
				return;
			}
		}
		center.set(posOnScreen.pos().x, posOnScreen.pos().y);
    }

	private void suppressOpeningEffect() {
		openingEffectSuppressed = true;
		effectLength = 0.0f;
		center.set(0.5f, 0.5f);
	}

    public void requestShaderRestart() {
        shaderRestartRequested = true;
    }

    private void restartShaderPipeline() {
        if (active || selectedShader != null) {
            justReset = true;
        }
        active = false;
        activeInstanceId = Integer.MIN_VALUE;
        previousShader = selectedShader;
        selectedShader = null;
        center.set(0.5f, 0.5f);
        ticks = 0.0f;
    }

    public void reset() {
        if (active || selectedShader != null) {
            justReset = true;
        }
        active = false;
        activeInstanceId = Integer.MIN_VALUE;
        previousShader = selectedShader;
        selectedShader = null;
        center.set(0.5f, 0.5f);
        ticks = 0.0f;
        visualInstanceId = Integer.MIN_VALUE;
        visualUserId = -1;
        visualRoute = "";
        openingVisualPending = false;
        openingEffectSuppressed = false;
        shaderRestartRequested = false;
        loggedActivationInstanceId = Integer.MIN_VALUE;
        loggedSelectedShader = null;
    }

    public boolean active() {
        return active;
    }

    public ResourceLocation selectedShader() {
        return selectedShader;
    }

    public ResourceLocation previousShader() {
        return previousShader;
    }

    public boolean justActivated() {
        return justActivated;
    }

    public boolean justReset() {
        return justReset;
    }

    public boolean shaderChanged() {
        return selectedShader != null && !selectedShader.equals(previousShader);
    }

    public Vector2f center() {
        return center;
    }

    public float ticks() {
        return ticks;
    }

    public float length() {
        return length;
    }

    public float effectLength() {
        return effectLength;
    }
}
