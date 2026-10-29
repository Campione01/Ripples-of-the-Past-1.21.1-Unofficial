package rotp.addons.ultimate_lifeform.client;

import java.util.OptionalInt;

import rotp.core.client.firstperson.FirstPersonRender;
import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.client.sound.HamonSparksLoopSound;
import rotp.core.client.ui.hud_power.PowerHud.AbilityHud;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.impl.powers.hamon.client.particle.custom.HamonSparkEntityOffsetParticle;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.entityglow.EntityGlowChannel;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.UtilFunctions;

import rotp.addons.ultimate_lifeform.UltimateHamonEntityCharge;
import rotp.addons.ultimate_lifeform.UltimateHamonProjectileCharge;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.network.UltimateDoubleShiftPayload;
import rotp.addons.ultimate_lifeform.network.UltimateWallClimbInputPayload;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client game-bus half of the donor's client-only behaviours: the Enhanced Senses
 * tinted glow sweep, the per-tick directional wall-climb input the server needs as
 * its truth, the wall-climb head clamp (the local climber every tick and every
 * rendered frame, every other climbing player before it is drawn), and the spark
 * loop of Ultimate-charged projectiles and entities. The server side of the glow and
 * the climb input is deliberately inert without this class -
 * UltimateLifeformCombat leaves ENHANCED_SENSES to the client and
 * UltimateWallClimbInputPayload is registered but never sent.
 */
@EventBusSubscriber(
        modid = UltimateLifeformAddon.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class UltimateLifeformClientGameEvents {
    private static final OptionalInt ENHANCED_SENSES_COLOR =
            OptionalInt.of(7_693_155);
    private static final int GLOW_DURATION_TICKS = 160;
    private static final double MAX_SCAN_RADIUS = 64.0D;
    private static final DoubleShiftDetector DOUBLE_SHIFT =
            new DoubleShiftDetector();
    private static boolean renderingExtraArm;

    private UltimateLifeformClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        UltimateLifeformDownstreamSubmissions.observeClientLevel(
                minecraft.level);
        UltimateLifeformHud.ensureAdded();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.isPaused()) {
            tickUltimateHamonCharges(minecraft.level);
        }
        UltimateLifeformData data = PlayerPower.getPowerData(
                        player,
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
        if (data == null) {
            return;
        }
        tickEnhancedSenses(player, data);
        if (data.isWallClimbing()) {
            UltimateLifeformData.climbLimitHeadRot(player, data.getWallFace());
        }
        tickWallInput(minecraft, player, data);
    }

    // 1.16 ClientEventHandler.onRenderTick (START): the local climber is held
    // within the wall limits every rendered frame, not only every tick.
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isAlive()) {
            return;
        }
        limitClimbHeadRot(player);
    }

    // 1.16 ClientEventHandler.onRenderPlayer: every other climbing player is
    // drawn facing the wall too.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player != Minecraft.getInstance().player) {
            limitClimbHeadRot(player);
        }
    }

    /*
     * 1.16 ClientEvents.onRenderHand: no first-person hands while gliding
     * without an elytra on; with the Ultimate Barrage selected in the HUD and
     * both hands free the empty off-hand arm is drawn too.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || minecraft.getCameraEntity() != player
                || PlayerPower.getPowerData(player,
                                UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                        .isEmpty()) {
            return;
        }
        if (hidesHandsWhileGliding(player)) {
            event.setCanceled(true);
            return;
        }
        if (event.isCanceled()
                || renderingExtraArm
                || event.getHand() != InteractionHand.MAIN_HAND
                || player.isInvisible()
                || !drawsBarrageOffHand(player, isHudAbilitySelected(
                        UltimateLifeformSpec.ULTIMATE_BARRAGE.id))) {
            return;
        }
        renderingExtraArm = true;
        try {
            FirstPersonRender.renderExtraPlayerArm(
                    event, player, InteractionHand.OFF_HAND);
        }
        finally {
            renderingExtraArm = false;
        }
    }

    private static boolean hidesHandsWhileGliding(Player player) {
        return player.isFallFlying()
                && !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private static boolean drawsBarrageOffHand(
            Player player, boolean barrageSelected) {
        return barrageSelected
                && UtilFunctions.isHandFree(player, InteractionHand.MAIN_HAND)
                && UtilFunctions.isHandFree(player, InteractionHand.OFF_HAND);
    }

    private static boolean isHudAbilitySelected(String abilityName) {
        AbilityHud hud = PowerHud.abilityHUDInstance;
        return hud != null && hud.isAbilitySelected(abilityName);
    }

    private static void limitClimbHeadRot(Player player) {
        PlayerPower.getPowerData(
                        player,
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .filter(UltimateLifeformData::isWallClimbing)
                .ifPresent(data -> UltimateLifeformData.climbLimitHeadRot(
                        player, data.getWallFace()));
    }

    // 1.16 ProjectileUltimateHamonChargeCap.tick and EntityUltimateHamonChargeCap.tick,
    // client side: the spark loop and one Ultimate Hamon spark per tick at a random
    // point of the charged projectile's or entity's box.
    private static void tickUltimateHamonCharges(ClientLevel level) {
        RandomSource random = level.random;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Projectile projectile
                    && UltimateHamonProjectileCharge.hasClientCharge(projectile)
                    && !TimeStopState.shouldFreezeClientEntity(projectile)) {
                HamonSparksLoopSound.playSparkSound(
                        projectile, projectile.position(), 1.0F);
                addUltimateSpark(level, random, projectile);
            }
            if (UltimateHamonEntityCharge.getExisting(entity)
                            .map(UltimateHamonEntityCharge::clientHasCharge)
                            .orElse(false)
                    && !TimeStopState.shouldFreezeClientEntity(entity)) {
                HamonSparksLoopSound.playSparkSound(
                        entity, entity.getBoundingBox().getCenter(), 1.0F, true);
                addUltimateSpark(level, random, entity);
            }
        }
    }

    // 1.16 CustomParticles.createUltimateHamonSparkParticles with an entity to
    // follow: an entity offset spark that moves with it.
    private static void addUltimateSpark(
            ClientLevel level, RandomSource random, Entity entity) {
        Vec3 at = new Vec3(
                entity.getRandomX(0.5D),
                entity.getRandomY(),
                entity.getRandomZ(0.5D));
        CustomParticlesHelper.addParticle(
                new HamonSparkEntityOffsetParticle(
                        level,
                        entity,
                        at.x,
                        at.y,
                        at.z,
                        random.nextGaussian() * 0.25D,
                        random.nextGaussian() * 0.25D,
                        random.nextGaussian() * 0.25D,
                        UltimateLifeformAddon.RAINBOW_SPARK.get()),
                at,
                false,
                false);
    }

    private static void tickEnhancedSenses(
            LocalPlayer player, UltimateLifeformData data) {
        if (data.getHeldAbility() != UltimateLifeformSpec.ENHANCED_SENSES) {
            return;
        }
        int ticks = data.getHeldTicks();
        if (ticks >= GLOW_DURATION_TICKS && ticks % 20 != 0) {
            return;
        }
        double radius = Math.min((double) ticks, MAX_SCAN_RADIUS);
        Vec3 center = player.getBoundingBox().getCenter();
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : player.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != player && candidate.isAlive())) {
            EntityGlowChannel.PILLARMAN_ENHANCED_SENSES.apply(
                    target, ENHANCED_SENSES_COLOR, GLOW_DURATION_TICKS);
            UltimateLifeformClientAcceptance.recordGlobalVisual(
                    target,
                    ClientReceipt.ENHANCED_SENSES_GLOW,
                    UltimateLifeformSpec.ENHANCED_SENSES.id);
        }
    }

    // 1.16 InputHandler.updateMovementInput with ClDoubleShiftPressPacketMixin:
    // a double shift press on the ground lets an Ultimate Lifeform sink into a
    // fluid for as long as shift stays held.
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        if (DOUBLE_SHIFT.inputUpdate(event.getInput()) == 2
                && player.isAlive()
                && player.onGround()) {
            PlayerPower.getPowerData(
                            player,
                            UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                    .ifPresent(data -> {
                        data.setDoubleShiftPress(player);
                        PacketDistributor.sendToServer(
                                new UltimateDoubleShiftPayload());
                    });
        }
    }

    // 1.16 InputHandler.DoubleShiftDetector
    private static final class DoubleShiftDetector {
        private boolean hasPrevInput;
        private boolean prevUp;
        private boolean prevDown;
        private boolean prevLeft;
        private boolean prevRight;
        private int shiftPresses;
        private int triggerTime;
        private boolean triggerGap;

        private int inputUpdate(Input input) {
            boolean shiftPressed = input.shiftKeyDown;
            boolean trigger = false;
            if (shiftPresses > 0
                    && (input.jumping || !sameMovementKeys(input))) {
                reset();
            }
            if (triggerTime > 0 && --triggerTime == 0) {
                reset();
            }
            if (shiftPressed) {
                trigger = triggerGap
                        && (shiftPresses == 0 || triggerTime > 0);
                if (trigger) {
                    triggerTime = 7;
                    if (shiftPresses++ == 0) {
                        saveInputState(input);
                    }
                }
            }
            triggerGap = !shiftPressed;
            return trigger ? shiftPresses : 0;
        }

        private boolean sameMovementKeys(Input input) {
            if (!hasPrevInput) {
                saveInputState(input);
                return true;
            }
            return prevUp == input.up && prevDown == input.down
                    && prevLeft == input.left && prevRight == input.right;
        }

        private void saveInputState(Input input) {
            hasPrevInput = true;
            prevUp = input.up;
            prevDown = input.down;
            prevLeft = input.left;
            prevRight = input.right;
        }

        private void reset() {
            hasPrevInput = false;
            shiftPresses = 0;
            triggerTime = 0;
        }
    }

    private static void tickWallInput(
            Minecraft minecraft,
            LocalPlayer player,
            UltimateLifeformData data) {
        if (!data.isWallClimbing()) {
            return;
        }
        boolean jump = minecraft.options.keyJump.isDown();
        PacketDistributor.sendToServer(
                new UltimateWallClimbInputPayload(
                        player.xxa, player.zza, jump));
    }
}
