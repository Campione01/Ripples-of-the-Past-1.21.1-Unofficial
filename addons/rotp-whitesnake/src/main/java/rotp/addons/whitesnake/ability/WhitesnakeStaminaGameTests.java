package rotp.addons.whitesnake.ability;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disc.MemoryDiscItem;
import rotp.addons.whitesnake.entity.projectile.DiscProjectile;
import rotp.addons.whitesnake.entity.projectile.MeltHeartProjectile;
import rotp.addons.whitesnake.memory.MemoryDiscService;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStands;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 Whitesnake actions had no stamina condition except Melt Your Heart's silent 100 floor: StandEntityTask
 * performed them and then paid their cost with the result ignored, so a short bar is admitted and drained to 0.
 */
@GameTestHolder(WhitesnakeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WhitesnakeStaminaGameTests {
    private static final String NOT_ENOUGH_STAMINA = "jojo.message.action_condition.not_enough_stamina";
    private static final String TARGET_TOO_FAR = "jojo.message.action_condition.target_too_far";
    private static final short HOLD_KEY = 121;
    private static final short OTHER_KEY = 122;

    private WhitesnakeStaminaGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void blindnessTogglesOnAShortBar(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-st-blind");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability ability = power.getAbility("blindness");
            power.setStamina(10.0F);
            helper.assertTrue(AbilityInput.withConditionCheck(ability, caster),
                    "1.16 had no stamina condition: Blindness must be admitted on a 10 bar, messages="
                            + caster.actionBarKeys);
            perform(ability, stand, caster);
            helper.assertTrue(caster.hasEffect(MobEffects.BLINDNESS), "Blindness did not toggle on a short bar");
            helper.assertTrue(power.getStamina() == 0.0F,
                    "Blindness's 50 cost must drain a 10 bar to 0: " + power.getStamina());
            helper.assertTrue(caster.actionBarKeys.isEmpty(), "Blindness sent " + caster.actionBarKeys);
            helper.succeed();
        }
        finally {
            disconnect(caster);
        }
    }

    // 1.16 MeltYourHeart.checkStandConditions: stamina >= 100 or a bare NEGATIVE, then a 10 cost at perform start.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void meltYourHeartRefusesSilentlyBelowItsHundredFloor(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-st-melt-low");
        try {
            summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability ability = power.getAbility("melt_your_heart");
            power.setStamina(99.0F);
            helper.assertTrue(!AbilityInput.withConditionCheck(ability, caster),
                    "Melt Your Heart must keep the donor's 100 stamina floor");
            helper.assertTrue(caster.actionBarKeys.isEmpty(),
                    "1.16 refused Melt Your Heart silently, got " + caster.actionBarKeys);
            helper.succeed();
        }
        finally {
            disconnect(caster);
        }
    }

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void meltYourHeartPaysTenOnceAtItsFloor(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-st-melt");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability ability = power.getAbility("melt_your_heart");
            power.setStamina(100.0F);
            helper.assertTrue(AbilityInput.withConditionCheck(ability, caster),
                    "Melt Your Heart was refused at 100 stamina, messages=" + caster.actionBarKeys);
            perform(ability, stand, caster);
            helper.assertTrue(power.getStamina() == 90.0F,
                    "Melt Your Heart must pay 10 once at perform start: " + power.getStamina());
            helper.succeed();
        }
        finally {
            disconnect(caster);
        }
    }

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void throwDiscFiresOnAShortBar(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-st-throw");
        Recorder target = player(helper, "ws-st-throw-t");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability ability = power.getAbility("whitesnake_throw_disc");
            helper.assertTrue(MemoryDiscService.extractTo(target, caster).status() == MemoryDiscService.Status.APPLIED,
                    "Fixture: no memory disc to throw");
            ItemStack disc = memoryDisc(caster, target.getUUID()).copy();
            caster.getInventory().clearContent();
            caster.setItemInHand(InteractionHand.MAIN_HAND, disc);
            power.setStamina(1.0F);
            helper.assertTrue(AbilityInput.withConditionCheck(ability, caster),
                    "1.16 had no stamina condition: the disc throw must be admitted on a 1 bar, messages="
                            + caster.actionBarKeys);
            perform(ability, stand, caster);
            List<DiscProjectile> thrown = helper.getLevel().getEntitiesOfClass(DiscProjectile.class,
                    new AABB(caster.blockPosition()).inflate(8.0D));
            helper.assertTrue(thrown.size() == 1 && caster.getMainHandItem().isEmpty(),
                    "The disc was not thrown on a short bar: projectiles=" + thrown.size());
            helper.assertTrue(power.getStamina() == 0.0F,
                    "The throw's 3 cost must drain a 1 bar to 0: " + power.getStamina());
            thrown.forEach(DiscProjectile::discard);
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    /*
     * 1.16 standEntity.shootProjectile(disc, 2.0F, 0.25F) spread the disc by getProjectileInaccuracy(0.25) of the Stand's
     * precision: none at 12, where the port's own curve still spread it, 1.5 at 4.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void throwDiscSpreadsByTheStandsPrecisionAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-spread");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability ability = power.getAbility("whitesnake_throw_disc");
            double[][] rows = { { 12.0D, 0.0D }, { 4.0D, 1.5D } };
            for (double[] row : rows) {
                double precision = row[0];
                double spread = row[1];
                caster.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
                StandEntity.updateStandStatAttributes(stand, caster);
                stand.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
                helper.assertTrue(Math.abs(stand.getPrecision() - precision) < 1.0E-9D,
                        "Fixture: Whitesnake's precision is " + stand.getPrecision() + ", not " + precision);
                for (int shot = 0; shot < 3; shot++) {
                    caster.getInventory().clearContent();
                    caster.setItemInHand(InteractionHand.MAIN_HAND,
                            StandDiscItem.withStand(new StandInstance(ModStands.STAR_PLATINUM.get())));
                    power.setStamina(power.getMaxStamina());
                    stand.setYRot(-40.0F);
                    stand.setXRot(15.0F);
                    perform(ability, stand, caster);
                    List<DiscProjectile> thrown = helper.getLevel().getEntitiesOfClass(DiscProjectile.class,
                            new AABB(caster.blockPosition()).inflate(8.0D));
                    helper.assertTrue(thrown.size() == 1, "Fixture: " + thrown.size() + " discs were thrown");
                    DiscProjectile disc = thrown.get(0);
                    Vec3 along = Vec3.directionFromRotation(stand.getXRot(), stand.getYRot());
                    double off = disc.getDeltaMovement().normalize().cross(along).length();
                    disc.discard();
                    boolean asIn116 = spread == 0.0D ? off < 1.0E-6D : off > 1.0E-6D && off <= maxOffAim(spread);
                    helper.assertTrue(asIn116, "1.16 spread Whitesnake's thrown disc at a precision of " + precision
                            + " by " + spread + " (getProjectileInaccuracy(0.25)); it flies " + off + " off its aim");
                }
            }
            helper.succeed();
        }
        finally {
            disconnect(caster);
        }
    }

    /*
     * 1.16 MeltYourHeart.standTickPerform shot each drop with standEntity.shootProjectile(proj, 0.1F, 0.5F): turned along
     * the Stand's look with getProjectileInaccuracy(0.5) (none at precision 12, 2 at 4), then addProjectile scaled its
     * damage by the Stand's strength / 8 and its speed by its speed / 8.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void meltYourHeartShootsWithTheStandsStatsAsIn116(GameTestHelper helper) {
        whenEntitiesTick(helper, () -> meltYourHeartShootsWithTheStandsStatsAsIn116Rows(helper));
    }

    private static void meltYourHeartShootsWithTheStandsStatsAsIn116Rows(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-melt-stats");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            caster.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(12.0D);
            caster.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(10.0D);
            double[][] rows = { { 12.0D, 0.0D }, { 4.0D, 2.0D } };
            for (double[] row : rows) {
                double precision = row[0];
                double spread = row[1];
                caster.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
                StandEntity.updateStandStatAttributes(stand, caster);
                float damageFactor = (float) stand.getAttackDamage() / 8;
                double speedFactor = stand.getAttackSpeed() / 8;
                helper.assertTrue(Math.abs(damageFactor - 1.0F) > 0.1F && Math.abs(speedFactor - 1.0D) > 0.1D
                                && stand.getProjectileInaccuracy(0.5F) == (float) spread,
                        "Fixture: strength " + stand.getAttackDamage() + ", speed " + stand.getAttackSpeed()
                                + ", spread " + stand.getProjectileInaccuracy(0.5F));
                power.setStamina(power.getMaxStamina());
                stand.setYRot(-40.0F);
                stand.setXRot(15.0F);
                perform(power.getAbility("melt_your_heart"), stand, caster);
                List<MeltHeartProjectile> shot = helper.getLevel().getEntitiesOfClass(MeltHeartProjectile.class,
                        new AABB(caster.blockPosition()).inflate(8.0D));
                helper.assertTrue(shot.size() == 1, "Fixture: " + shot.size() + " drops were shot");
                MeltHeartProjectile drop = shot.get(0);
                float dropDamage = drop.getDamageFactor();
                double dropSpeed = drop.getSpeedFactor();
                Vec3 along = Vec3.directionFromRotation(stand.getXRot(), stand.getYRot());
                double off = Vec3.directionFromRotation(drop.getXRot(), drop.getYRot()).cross(along).length();
                drop.discard();
                helper.assertTrue(Math.abs(dropDamage - damageFactor) < 1.0E-5F
                                && Math.abs(dropSpeed - speedFactor) < 1.0E-9D,
                        "1.16 addProjectile scaled the drop by the Stand's strength / 8 (" + damageFactor
                                + ") and speed / 8 (" + speedFactor + "); it has " + dropDamage + " and " + dropSpeed);
                boolean asIn116 = spread == 0.0D ? off < 1.0E-4D : off > 1.0E-6D && off <= maxOffAim(spread);
                helper.assertTrue(asIn116, "1.16 shootProjectile turned the drop along the Stand's look with a spread of "
                        + spread + " at a precision of " + precision + "; it is turned " + off + " off it");
            }
            helper.succeed();
        }
        finally {
            disconnect(caster);
        }
    }

    // the sine of the most vanilla's shot noise (0.0172275 a spread point on each axis) turns a unit direction by
    private static double maxOffAim(double spread) {
        double noise = 0.0172275D * spread * Math.sqrt(3.0D);
        return noise / (1.0D - noise) + 1.0E-6D;
    }

    /*
     * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional): a Stand-bearing mob waited it
     * (+ 10) before its next pick, Resolve never shortened it (StandAction's multiplier 0 drops only the additional part)
     * and a Creative user kept it. 004 InitStands :72 split Melt Your Heart's cooldown(20, 100, 0.0f); the disc removals
     * (:75-76) registered none, their perform set both 30-tick timers directly (WhitesnakeRemovingMemoryDisk :94-95), in
     * Resolve and Creative alike; the other actions had none.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void whitesnakeCooldownsKeepTheDonorsTechnicalAndAdditionalPartAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-cooldown-split");
        Recorder target = player(helper, "ws-cooldown-split-t");
        List<String> wrong = new ArrayList<>();
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            String[] names = { "melt_your_heart", "removing_the_memory_disk", "whitesnake_remove_stand_disc",
                    "whitesnake_throw_disc", "blindness", "disguise", "punch", "barrage", "heavy_punch", "finisher_punch" };
            int[] technical = { 20, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
            int[] additional = { 100, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
            for (String mode : new String[] { "Survival", "Resolve", "Creative" }) {
                if (mode.equals("Resolve")) {
                    caster.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
                }
                else if (mode.equals("Creative")) {
                    caster.removeEffect(ModStatusEffects.RESOLVE);
                    caster.setGameMode(GameType.CREATIVE);
                }
                helper.assertTrue(caster.hasEffect(ModStatusEffects.RESOLVE) == mode.equals("Resolve")
                                && power.isUserCreative() == mode.equals("Creative"),
                        "Fixture: the caster is not in the " + mode + " setup");
                for (int row = 0; row < names.length; row++) {
                    Ability ability = power.getAbility(names[row]);
                    helper.assertTrue(ability != null, "Fixture: Whitesnake has no " + names[row]);
                    int full = ability.getCooldown(power, -1);
                    int expected = technical[row] + (mode.equals("Survival") ? additional[row] : 0);
                    if (mode.equals("Survival") && ability.getCooldownTechnical(power) != technical[row]) {
                        wrong.add(names[row] + " reports technical " + ability.getCooldownTechnical(power) + " of a full "
                                + full + ", the donor's " + technical[row] + " of " + (technical[row] + additional[row]));
                    }
                    else if (full != expected) {
                        wrong.add(names[row] + " takes " + full + " ticks in " + mode + ", the donor's " + expected);
                    }
                }
                caster.getInventory().clearContent();
                power.resetAbilityCooldowns();
                power.setStamina(power.getMaxStamina());
                aimAt(caster, target);
                perform(power.getAbility("removing_the_memory_disk"), stand, caster);
                ItemStack disc = memoryDisc(caster, target.getUUID());
                int memory = power.getAbilityCooldown("removing_the_memory_disk");
                int standDisc = power.getAbilityCooldown("whitesnake_remove_stand_disc");
                if (disc.isEmpty() || memory != WhitesnakeRemoveMemoryDiscAbility.COOLDOWN_TICKS
                        || standDisc != WhitesnakeRemoveMemoryDiscAbility.COOLDOWN_TICKS) {
                    wrong.add("a memory removal in " + mode + " (extracted " + !disc.isEmpty() + ") left the timers "
                            + memory + " / " + standDisc + ", the donor's 30 / 30");
                }
                if (!disc.isEmpty()) {
                    MemoryDiscService.restore(disc, target);
                }
            }
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Whitesnake cooldown(technical, additional): " + String.join("; ", wrong));
        helper.succeed();
    }

    /*
     * 1.16 WhitesnakeRemovingStandDisc had no disc condition (004 :53-110, TargetRequirement ENTITY only): pressed at a
     * Stand-less target with no Stand disc in hand it was let through, charged its 30 ticks, moved nothing and set both
     * 30-tick timers at the perform. The port's message for the missing disc is sent then, at the perform.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 60)
    public static void standDiscAtAStandlessTargetWithNoDiscChargesAsIn116(GameTestHelper helper) {
        whenEntitiesTick(helper, () -> standDiscNoDiscRows(helper));
    }

    private static void standDiscNoDiscRows(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-nodisc");
        Recorder target = player(helper, "ws-nodisc-t");
        List<String> wrong = new ArrayList<>();
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability standDisc = power.getAbility("whitesnake_remove_stand_disc");
            StandPower targetPower = StandPower.get(target);
            helper.assertTrue(standDisc != null && (targetPower == null || !targetPower.hasPower()),
                    "Fixture: no Stand disc removal, or the target has a Stand");
            caster.getInventory().clearContent();
            power.resetAbilityCooldowns();
            aimAt(caster, target);
            assertDiscCheck(helper, standDisc, power, null, "a Stand-less player with no disc in hand");
            int soundsBefore = caster.standSounds(stand);
            EntityActionInstance action = startPlacementAction(standDisc, stand, caster);
            boolean performed = false;
            int ticks = 0;
            while (!action.isOver() && ticks < 60) {
                if (action.getPhase() == ActionPhase.WINDUP && action.getPhaseTick() >= action.getCurPhaseLength()) {
                    action.onButtonStopHold();
                }
                performed |= action.getPhase() == ActionPhase.PERFORM;
                action._tickAction();
                ticks++;
            }
            if (!performed || ticks < 30) {
                wrong.add("the press did not charge and perform (performed " + performed + " after " + ticks + " ticks)");
            }
            int played = caster.standSounds(stand) - soundsBefore;
            if (played != 1) {
                wrong.add("the Stand disc removal played whitesnake_remove_disc " + played + " times at its perform, 1.16 once");
            }
            checkDiscTimers(power, wrong, "a Stand disc at a Stand-less target with no disc in hand", 30);
            if (!caster.getInventory().isEmpty() || StandPower.get(target) != null && StandPower.get(target).hasPower()) {
                wrong.add("a disc moved");
            }
            stopPlacementAction(stand, caster);
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 let a Stand disc removal at a Stand-less target with no disc charge: "
                + String.join("; ", wrong));
        helper.succeed();
    }

    /*
     * 1.16 standPerform set both 30-tick timers for any living target user (004 WhitesnakeRemovingStandDisc :65, :79-80;
     * WhitesnakeRemovingMemoryDisk :67, :94-95), whether or not a disc moved: a Stand disc pressed at a Stand-less target
     * with no disc in hand moved nothing and still set them. A target lost before the perform (the task's target emptied)
     * set none. The port's refusal messages stay.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalsSetBothTimersWhenNothingMovesAsIn116(GameTestHelper helper) {
        whenEntitiesTick(helper, () -> discRemovalsSetBothTimersWhenNothingMovesAsIn116Rows(helper));
    }

    private static void discRemovalsSetBothTimersWhenNothingMovesAsIn116Rows(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-timers-nothing");
        Recorder target = player(helper, "ws-timers-nothing-t");
        List<String> wrong = new ArrayList<>();
        ItemStack firstDisc = ItemStack.EMPTY;
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability standDisc = power.getAbility("whitesnake_remove_stand_disc");
            Ability memory = power.getAbility("removing_the_memory_disk");
            helper.assertTrue(standDisc != null && memory != null, "Fixture: Whitesnake has no disc removals");
            StandPower targetPower = StandPower.get(target);
            helper.assertTrue(targetPower == null || !targetPower.hasPower(), "Fixture: the target has a Stand");

            caster.getInventory().clearContent();
            power.resetAbilityCooldowns();
            aimAt(caster, target);
            int sounds = caster.standSounds(stand);
            perform(standDisc, stand, caster);
            checkRemoveDiscSound(wrong, "a Stand disc at a Stand-less target with no disc in hand", caster.standSounds(stand) - sounds);
            checkDiscTimers(power, wrong, "a Stand disc at a Stand-less target with no disc in hand", 30);
            if (caster.actionBarKeys.stream().noneMatch(key -> key.endsWith("whitesnake_stand_disc"))) {
                wrong.add("the port's message for the Stand disc with no disc in hand is gone: " + caster.actionBarKeys);
            }

            helper.assertTrue(MemoryDiscService.extractTo(target, caster).status() == MemoryDiscService.Status.APPLIED,
                    "Fixture: the first memory extraction failed");
            firstDisc = memoryDisc(caster, target.getUUID()).copy();
            caster.getInventory().clearContent();
            caster.actionBarKeys.clear();
            power.resetAbilityCooldowns();
            sounds = caster.standSounds(stand);
            perform(memory, stand, caster);
            checkRemoveDiscSound(wrong, "a memory disc at a target whose memory is already out", caster.standSounds(stand) - sounds);
            checkDiscTimers(power, wrong, "a memory disc at a target whose memory is already out", 30);
            if (caster.actionBarKeys.stream().noneMatch(key -> key.endsWith("whitesnake_memory_already_extracted"))) {
                wrong.add("the port's message for a memory already out is gone: " + caster.actionBarKeys);
            }

            power.resetAbilityCooldowns();
            Vec3 at = target.position();
            sounds = caster.standSounds(stand);
            perform(memory, stand, caster, () -> target.setPos(at.add(0.0D, 0.0D, 40.0D)));
            target.setPos(at);
            checkRemoveDiscSound(wrong, "a memory disc whose target left the Stand's reach before the perform", caster.standSounds(stand) - sounds);
            checkDiscTimers(power, wrong, "a memory disc whose target left the Stand's reach before the perform", 0);
            power.resetAbilityCooldowns();
            sounds = caster.standSounds(stand);
            perform(standDisc, stand, caster, () -> target.setPos(at.add(0.0D, 0.0D, 40.0D)));
            target.setPos(at);
            checkRemoveDiscSound(wrong, "a Stand disc whose target left the Stand's reach before the perform", caster.standSounds(stand) - sounds);
            checkDiscTimers(power, wrong, "a Stand disc whose target left the Stand's reach before the perform", 0);
        }
        finally {
            if (!firstDisc.isEmpty()) {
                MemoryDiscService.restore(firstDisc, target);
            }
            disconnect(target);
            disconnect(caster);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 disc removal timers: " + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 StandEntityTask played the PERFORM phase sound at every perform (004 InitStands :75, :76), whatever the perform did
    private static void checkRemoveDiscSound(List<String> wrong, String row, int played) {
        if (played != 1) {
            wrong.add(row + " played whitesnake_remove_disc " + played + " times at its perform, the donor once");
        }
    }

    private static void checkDiscTimers(StandPower power, List<String> wrong, String row, int expected) {
        int memory = power.getAbilityCooldown("removing_the_memory_disk");
        int standDisc = power.getAbilityCooldown("whitesnake_remove_stand_disc");
        if (memory != expected || standDisc != expected) {
            wrong.add(row + " left the timers " + memory + " / " + standDisc + ", the donor's " + expected + " / "
                    + expected);
        }
    }

    // 1.16 StandEntityTask paid the cost after standPerform even when the disc had gone in the windup.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void throwDiscPaysItsCostWhenTheDiscIsGone(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-st-throw-gone");
        Recorder target = player(helper, "ws-st-throw-gone-t");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability ability = power.getAbility("whitesnake_throw_disc");
            helper.assertTrue(MemoryDiscService.extractTo(target, caster).status() == MemoryDiscService.Status.APPLIED,
                    "Fixture: no memory disc to throw");
            ItemStack disc = memoryDisc(caster, target.getUUID()).copy();
            caster.getInventory().clearContent();
            caster.setItemInHand(InteractionHand.MAIN_HAND, disc);
            power.setStamina(10.0F);
            helper.assertTrue(AbilityInput.withConditionCheck(ability, caster),
                    "Fixture: the disc throw was refused, messages=" + caster.actionBarKeys);
            perform(ability, stand, caster, () -> caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
            List<DiscProjectile> thrown = helper.getLevel().getEntitiesOfClass(DiscProjectile.class,
                    new AABB(caster.blockPosition()).inflate(8.0D));
            helper.assertTrue(thrown.isEmpty(), "A disc was thrown without one in hand: " + thrown.size());
            helper.assertTrue(power.getStamina() == 7.0F,
                    "1.16 charged the throw's 3 even with the disc gone at perform time: " + power.getStamina());
            MemoryDiscService.restore(disc, target);
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 StandEntityTask paid 200 on every perform, also when standPerform found its target out of reach.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void memoryDiscRemovalPaysItsCostWhenTheTargetIsLost(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-st-memory-lost");
        Recorder target = player(helper, "ws-st-memory-lost-t");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability ability = power.getAbility("removing_the_memory_disk");
            aimAt(caster, target);
            power.setStamina(500.0F);
            helper.assertTrue(AbilityInput.withConditionCheck(ability, caster),
                    "Fixture: memory removal was refused, messages=" + caster.actionBarKeys);
            perform(ability, stand, caster, () -> target.setPos(target.position().add(0.0D, 0.0D, 40.0D)));
            helper.assertTrue(!WhitesnakeAddon.memoryState(target).isExtracted()
                            && memoryDisc(caster, target.getUUID()).isEmpty(),
                    "Fixture: a target out of reach was still extracted");
            helper.assertTrue(power.getStamina() == 300.0F,
                    "1.16 charged memory removal's 200 even when the perform lost its target: "
                            + power.getStamina());
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void memoryDiscRemovalExtractsOnAShortBar(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-st-memory");
        Recorder target = player(helper, "ws-st-memory-t");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability ability = power.getAbility("removing_the_memory_disk");
            aimAt(caster, target);
            power.setStamina(50.0F);
            helper.assertTrue(AbilityInput.withConditionCheck(ability, caster),
                    "1.16 had no stamina condition: memory removal must be admitted on a 50 bar, messages="
                            + caster.actionBarKeys);
            int sounds = caster.standSounds(stand);
            perform(ability, stand, caster);
            helper.assertTrue(caster.standSounds(stand) - sounds == 1,
                    "1.16 played whitesnake_remove_disc once at the perform of a memory extraction: "
                            + (caster.standSounds(stand) - sounds));
            helper.assertTrue(WhitesnakeAddon.memoryState(target).isExtracted()
                            && !memoryDisc(caster, target.getUUID()).isEmpty(),
                    "Memory removal did not extract on a short bar, messages=" + caster.actionBarKeys);
            helper.assertTrue(power.getStamina() == 0.0F,
                    "Memory removal's 200 cost must drain a 50 bar to 0: " + power.getStamina());
            helper.assertTrue(!caster.actionBarKeys.contains(NOT_ENOUGH_STAMINA),
                    "Memory removal still sent the stamina message");
            MemoryDiscService.restore(memoryDisc(caster, target.getUUID()), target);
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void standDiscRemovalExtractsOnAShortBar(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-st-stand");
        Recorder target = player(helper, "ws-st-stand-t");
        try {
            StandEntity stand = summon(helper, caster);
            summon(helper, target);
            StandPower power = StandPower.get(caster);
            StandPower targetPower = StandPower.get(target);
            Ability ability = power.getAbility("whitesnake_remove_stand_disc");
            aimAt(caster, target);
            power.setStamina(50.0F);
            helper.assertTrue(AbilityInput.withConditionCheck(ability, caster),
                    "1.16 had no stamina condition: Stand disc removal must be admitted on a 50 bar, messages="
                            + caster.actionBarKeys);
            int sounds = caster.standSounds(stand);
            perform(ability, stand, caster);
            helper.assertTrue(caster.standSounds(stand) - sounds == 1,
                    "1.16 played whitesnake_remove_disc once at the perform of a Stand extraction: "
                            + (caster.standSounds(stand) - sounds));
            helper.assertTrue(!targetPower.hasPower(),
                    "Stand disc removal did not take the Stand on a short bar, messages=" + caster.actionBarKeys);
            helper.assertTrue(power.getStamina() == 0.0F,
                    "The 300 extraction cost must drain a 50 bar to 0: " + power.getStamina());
            helper.assertTrue(!caster.actionBarKeys.contains(NOT_ENOUGH_STAMINA),
                    "Stand disc removal still sent the stamina message");
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 InitStands registered the Whitesnake barrage cry with the one-argument standSound (playInArmsOnly true), so
    // an arms-only Whitesnake cried too (StandEntityAction.getPhaseStandSounds).
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void barrageCriesInArmsOnlyMode(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-cry-arms");
        try {
            StandEntity stand = summon(helper, caster);
            Ability ability = StandPower.get(caster).getAbility("barrage");
            helper.assertTrue(ability instanceof StandEntityBarrageAbility, "Fixture: no Whitesnake barrage " + ability);
            EntityActionInstance barrage = ((EntityActionType) ability).initActionOnAbilityUse(
                    caster.level(), caster, stand, null);
            helper.assertTrue(playsBarrageCry(barrage, stand), "Fixture: a fully summoned Whitesnake does not cry");
            stand.setArmsOnlyMode(true, true);
            helper.assertTrue(stand.isArmsOnlyMode(), "Fixture: Whitesnake is not arms-only");
            helper.assertTrue(playsBarrageCry(barrage, stand),
                    "1.16 played Whitesnake's barrage cry in arms-only mode, the port's barrage stayed silent there");
            helper.succeed();
        }
        finally {
            disconnect(caster);
        }
    }

    // 1.16 whitesnake_throw_disc standOffsetFront(): (left, up, forward) (0, 0.2, 0.5) from the user's feet, turned by
    // the view yaw. The disc removals kept no offset (noAdheringToUserOffset) and onTaskSet put the Stand one block
    // before its target on the user's side as the hold started.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discActionsPutTheStandWhereTheDonorDid(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-place");
        Recorder target = player(helper, "ws-place-t");
        try {
            StandEntity stand = summon(helper, caster);
            summon(helper, target);
            StandPower power = StandPower.get(caster);
            caster.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            target.setPos(caster.position().add(0.0D, 0.0D, 3.0D));
            stand.updatePosition(caster);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Fixture: Whitesnake is not a full Stand following its user");

            startPlacementAction(power.getAbility("whitesnake_throw_disc"), stand, caster);
            // the body is turned away from the view: a body-yaw offset misses
            turnPlacementUser(caster, 0.0F, 50.0F);
            assertPlacedAt(helper, caster, stand, true, 0.0D, 0.2D, 0.5D, "the disc throw, view yaw 0, body yaw 50");
            // facing west the forward is -x
            turnPlacementUser(caster, 90.0F, 0.0F);
            assertPlacedAt(helper, caster, stand, true, -0.5D, 0.2D, 0.0D, "the disc throw, view yaw 90, body yaw 0");
            stopPlacementAction(stand, caster);

            turnPlacementUser(caster, 0.0F, 50.0F);
            aimAt(caster, target);
            for (String name : new String[] {"removing_the_memory_disk", "whitesnake_remove_stand_disc"}) {
                stand.setPos(caster.position());
                EntityActionInstance removal = startPlacementAction(power.getAbility(name), stand, caster);
                assertPlacedAt(helper, caster, stand, false, 0.0D, 0.0D, 2.0D,
                        name + " with its target 3 blocks south");
                // StandEntity.getOffsetFromUser returned null for the task: a retraction heads for the default offset
                helper.assertTrue(stand.offsetFromUser.isIdle() && removal.getSyncedStandOffset() == null,
                        "1.16 gave " + name + " no offset of its own (noAdheringToUserOffset), the port holds "
                                + stand.offsetFromUser.getRelativeOffset() + " " + stand.offsetFromUser.getRotations()
                                + ", synced " + removal.getSyncedStandOffset());
                stopPlacementAction(stand, caster);
            }
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 standKeepsTarget kept the removals' target (memory disc: any, Stand disc: getTargetRequirement ENTITY), and
    // a punch pressed at nothing took it over from the task it replaced (StandEntity.setTask).
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalsKeepTheirTargetForTheNextPunchAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-keep");
        Recorder target = player(helper, "ws-keep-t");
        try {
            StandEntity stand = summon(helper, caster);
            summon(helper, target);
            StandPower power = StandPower.get(caster);
            target.setPos(caster.position().add(0.0D, 0.0D, 3.0D));
            turnPlacementUser(caster, 0.0F, 0.0F);
            for (String name : new String[] {"removing_the_memory_disk", "whitesnake_remove_stand_disc"}) {
                stand.setPos(caster.position());
                aimAt(caster, target);
                EntityActionInstance removal = startPlacementAction(power.getAbility(name), stand, caster);
                helper.assertTrue(removal.getTaskTarget(helper.getLevel()).getEntity() == target,
                        "1.16 " + name + " kept the target it was pressed at, the task holds "
                                + removal.getTaskTarget(helper.getLevel()));
                // looking away with the Stand turned away, the punch is pressed at nothing
                aimAt(caster, null);
                turnPlacementUser(caster, 180.0F, 0.0F);
                stand.setYRot(180.0F);
                EntityActionInstance punch = startPlacementAction(power.getAbility("punch"), stand, caster);
                helper.assertTrue(punch != removal && punch.getTaskTarget(helper.getLevel()).getEntity() == target,
                        "1.16 StandEntity.setTask gave a punch pressed at nothing the target of " + name
                                + ", it holds " + punch.getTaskTarget(helper.getLevel()));
                stopPlacementAction(stand, caster);
                turnPlacementUser(caster, 0.0F, 0.0F);
            }
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    /*
     * 1.16 Action.checkRangeAndTarget: an entity target further than 8 blocks from the Stand, 4 without a line of
     * sight, was too far. The Stand disc (getTargetRequirement ENTITY) answered target_too_far; the memory disc (no
     * requirement) lost its target and checkStandConditions refused it without a message.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalsRefuseATargetBeyondTheStandsReachAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-range");
        Recorder target = player(helper, "ws-range-t");
        List<BlockPos> wall = new ArrayList<>();
        try {
            summon(helper, caster);
            StandEntity targetStand = summon(helper, target);
            StandPower power = StandPower.get(caster);
            Ability memory = power.getAbility("removing_the_memory_disk");
            Ability standDisc = power.getAbility("whitesnake_remove_stand_disc");
            Vec3 origin = caster.position();

            placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 6.0D));
            aimAt(caster, target);
            assertDiscCheck(helper, memory, power, null, "a target 6 blocks from the Stand in plain sight");
            assertDiscCheck(helper, standDisc, power, null, "a target 6 blocks from the Stand in plain sight");

            placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 12.0D));
            aimAt(caster, target);
            assertDiscCheck(helper, memory, power, "", "a target 12 blocks from the Stand");
            assertDiscCheck(helper, standDisc, power, TARGET_TOO_FAR, "a target 12 blocks from the Stand");

            placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 6.0D));
            aimAt(caster, target);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 3; dy++) {
                    BlockPos pos = BlockPos.containing(origin.x + dx, origin.y + dy, origin.z + 3.0D);
                    helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                    wall.add(pos);
                }
            }
            assertDiscCheck(helper, memory, power, "", "a target 6 blocks away behind a wall (4 without a line of sight)");
            assertDiscCheck(helper, standDisc, power, TARGET_TOO_FAR,
                    "a target 6 blocks away behind a wall (4 without a line of sight)");
            helper.succeed();
        }
        finally {
            wall.forEach(pos -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 PowerBaseImpl.tickHeldAction ran checkRequirements, range and target included, on every held tick.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalHoldsEndWhenTheTargetLeavesTheStandsReachAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-range-held");
        Recorder target = player(helper, "ws-range-held-t");
        try {
            StandEntity stand = summon(helper, caster);
            StandEntity targetStand = summon(helper, target);
            StandPower power = StandPower.get(caster);
            Vec3 origin = caster.position();
            for (String name : new String[] {"removing_the_memory_disk", "whitesnake_remove_stand_disc"}) {
                stand.setPos(origin);
                placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 6.0D));
                aimAt(caster, target);
                Ability ability = power.getAbility(name);
                EntityActionInstance hold = startPlacementAction(ability, stand, caster);
                ConditionCheck held = ((EntityActionAbility) ability).checkHeldActionConditions(hold, power);
                helper.assertTrue(held.isPositive(),
                        "Fixture: the " + name + " hold at a target in reach is refused: " + held.getWarning());
                placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 16.0D));
                aimAt(caster, target);
                assertCheck(helper, ((EntityActionAbility) ability).checkHeldActionConditions(hold, power),
                        name.startsWith("removing") ? "" : TARGET_TOO_FAR,
                        "the held " + name + " once the target is 11 blocks from the Stand");
                stopPlacementAction(stand, caster);
            }
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 makeServerSideTask kept no target past the reach, and onTaskSet put the Stand before the task's target only.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalsIgnoreATargetBeyondTheStandsReachAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-range-task");
        Recorder target = player(helper, "ws-range-task-t");
        try {
            StandEntity stand = summon(helper, caster);
            StandEntity targetStand = summon(helper, target);
            StandPower power = StandPower.get(caster);
            Vec3 origin = caster.position();
            placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 12.0D));
            for (String name : new String[] {"removing_the_memory_disk", "whitesnake_remove_stand_disc"}) {
                stand.setPos(origin);
                aimAt(caster, target);
                EntityActionInstance hold = startPlacementAction(power.getAbility(name), stand, caster);
                helper.assertTrue(hold.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY
                                && stand.position().distanceTo(origin) < 1.0E-6D,
                        "1.16 gave " + name + " no task target past the Stand's reach and left the Stand where it was; the"
                                + " task holds " + hold.getTaskTarget(helper.getLevel()) + " and the Stand moved to "
                                + stand.position());
                stopPlacementAction(stand, caster);
            }
            power.setStamina(500.0F);
            perform(power.getAbility("removing_the_memory_disk"), stand, caster);
            helper.assertTrue(!WhitesnakeAddon.memoryState(target).isExtracted()
                            && memoryDisc(caster, target.getUUID()).isEmpty(),
                    "A memory was extracted from a target 12 blocks from the Stand");
            power.setStamina(500.0F);
            perform(power.getAbility("whitesnake_remove_stand_disc"), stand, caster);
            helper.assertTrue(StandPower.get(target).hasPower(),
                    "A Stand disc was taken from a target 12 blocks from the Stand");

            // pressed in reach, out of it by the perform
            placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 6.0D));
            Runnable leaves = () -> placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 16.0D));
            stand.setPos(origin);
            aimAt(caster, target);
            power.setStamina(500.0F);
            perform(power.getAbility("removing_the_memory_disk"), stand, caster, leaves);
            helper.assertTrue(!WhitesnakeAddon.memoryState(target).isExtracted()
                            && memoryDisc(caster, target.getUUID()).isEmpty(),
                    "A memory was extracted from a target that left the Stand's reach before the perform");
            placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 6.0D));
            stand.setPos(origin);
            aimAt(caster, target);
            power.setStamina(500.0F);
            perform(power.getAbility("whitesnake_remove_stand_disc"), stand, caster, leaves);
            helper.assertTrue(StandPower.get(target).hasPower(),
                    "A Stand disc was taken from a target that left the Stand's reach before the perform");
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 StandEntityAction.checkStandTarget (StandEntity.canAttack) dropped a target the Stand may not attack, a
    // player of the user's team without friendly fire: both disc removals were refused without a message on the press
    // and stopped on the held tick the crosshair met it.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalsRefuseWhatTheStandMayNotAttackAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-ally");
        Recorder target = player(helper, "ws-ally-t");
        Scoreboard board = helper.getLevel().getScoreboard();
        PlayerTeam team = board.addPlayerTeam("ws-allies-" + helper.getTick());
        try {
            team.setAllowFriendlyFire(false);
            StandEntity stand = summon(helper, caster);
            StandEntity targetStand = summon(helper, target);
            StandPower power = StandPower.get(caster);
            Vec3 origin = caster.position();
            for (String name : new String[] {"removing_the_memory_disk", "whitesnake_remove_stand_disc"}) {
                Ability ability = power.getAbility(name);
                stand.setPos(origin);
                placeTarget(target, targetStand, origin.add(0.0D, 0.0D, 4.0D));
                aimAt(caster, target);
                assertDiscCheck(helper, ability, power, null, "a player of another team");
                EntityActionInstance hold = startPlacementAction(ability, stand, caster);
                board.addPlayerToTeam(caster.getScoreboardName(), team);
                board.addPlayerToTeam(target.getScoreboardName(), team);
                assertCheck(helper, ((EntityActionAbility) ability).checkHeldActionConditions(hold, power), "",
                        "the held " + name + " once its target is a teammate without friendly fire");
                stopPlacementAction(stand, caster);
                aimAt(caster, target);
                assertDiscCheck(helper, ability, power, "", "a teammate without friendly fire");
                board.removePlayerFromTeam(caster.getScoreboardName(), team);
                board.removePlayerFromTeam(target.getScoreboardName(), team);
            }
            helper.succeed();
        }
        finally {
            board.removePlayerTeam(team);
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 StandEntity.canAttack (:1932-1949), asked of both disc targets by StandEntityAction.checkStandTarget: not
    // the user's own tamed animal, not an animal riding the user's vehicle, not a teammate without friendly fire.
    // The press summoned the Stand first, so with it away the same rules hold for its user. The tamed wolf and the
    // wild one go through both presses; the shared ride and the team through the rule itself, which both call.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalsRefuseOwnPetsAndSharedRidesWithOrWithoutTheStandAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-pets");
        Wolf wild = null;
        Wolf tamed = null;
        Cow cow = null;
        Boat boat = null;
        PlayerTeam team = null;
        Scoreboard board = helper.getLevel().getScoreboard();
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Vec3 origin = caster.position();
            wild = EntityType.WOLF.create(helper.getLevel());
            tamed = EntityType.WOLF.create(helper.getLevel());
            cow = EntityType.COW.create(helper.getLevel());
            boat = EntityType.BOAT.create(helper.getLevel());
            helper.assertTrue(wild != null && tamed != null && cow != null && boat != null
                            && helper.getLevel().addFreshEntity(wild) && helper.getLevel().addFreshEntity(tamed)
                            && helper.getLevel().addFreshEntity(cow) && helper.getLevel().addFreshEntity(boat),
                    "Fixture: the animals or the boat could not enter the level");
            for (Mob animal : List.of(wild, tamed, cow)) {
                animal.setNoAi(true);
                animal.setNoGravity(true);
            }
            boat.setNoGravity(true);
            tamed.tame(caster);
            for (boolean summoned : new boolean[] {true, false}) {
                if (!summoned) {
                    power.getPowerType().forceUnsummon(caster, power);
                    stand.discard();
                    helper.assertTrue(power.getSummonedStandEntity() == null, "Fixture: Whitesnake is still summoned");
                }
                String when = summoned ? " with its Stand summoned" : " with its Stand away";
                caster.setPos(origin);
                for (Entity far : List.of(wild, tamed, cow, boat)) {
                    far.setPos(origin.add(0.0D, 40.0D, 0.0D));
                }
                for (String name : new String[] {"removing_the_memory_disk", "whitesnake_remove_stand_disc"}) {
                    Ability ability = power.getAbility(name);
                    boolean memory = name.equals("removing_the_memory_disk");
                    wild.setPos(origin.add(0.0D, 0.0D, 4.0D));
                    aimAt(caster, wild);
                    assertDiscCheck(helper, ability, power, null, "a wild wolf" + when);
                    wild.setPos(origin.add(0.0D, 40.0D, 0.0D));
                    tamed.setPos(origin.add(0.0D, 0.0D, 4.0D));
                    aimAt(caster, tamed);
                    assertDiscCheck(helper, ability, power, "", "the user's own tamed wolf" + when);
                    tamed.setPos(origin.add(0.0D, 40.0D, 0.0D));
                }
                helper.assertTrue(WhitesnakeRemoveMemoryDiscAbility.standMayTarget(caster, cow),
                        "1.16 let the discs be taken from a cow riding nothing" + when);
                boat.setPos(origin);
                cow.setPos(origin);
                caster.startRiding(boat, true);
                cow.startRiding(boat, true);
                helper.assertTrue(cow.isPassengerOfSameVehicle(caster), "Fixture: the cow does not share the user's boat");
                boolean sharedRide = WhitesnakeRemoveMemoryDiscAbility.standMayTarget(caster, cow);
                cow.stopRiding();
                caster.stopRiding();
                helper.assertTrue(!sharedRide, "1.16 refused an animal riding the user's vehicle" + when);
                boat.setPos(origin.add(0.0D, 40.0D, 0.0D));
                cow.setPos(origin.add(0.0D, 40.0D, 0.0D));
                caster.setPos(origin);

                team = board.addPlayerTeam("ws-pets-" + helper.getTick() + summoned);
                team.setAllowFriendlyFire(false);
                board.addPlayerToTeam(caster.getScoreboardName(), team);
                board.addPlayerToTeam(cow.getScoreboardName(), team);
                boolean teammate = WhitesnakeRemoveMemoryDiscAbility.standMayTarget(caster, cow);
                board.removePlayerTeam(team);
                team = null;
                helper.assertTrue(!teammate, "1.16 refused a teammate without friendly fire" + when);
            }
            helper.succeed();
        }
        finally {
            if (team != null) {
                board.removePlayerTeam(team);
            }
            caster.stopRiding();
            for (Entity extra : new Entity[] {wild, tamed, cow, boat}) {
                if (extra != null) {
                    extra.discard();
                }
            }
            disconnect(caster);
        }
    }

    // 1.16 WhitesnakeRemovingMemoryDisk (TargetRequirement NONE) and WhitesnakeRemovingStandDisc (ENTITY) were
    // StandEntityActions, so PowerBaseImpl.checkTarget ran StandEntityAction.overrideVanillaMouseTarget on the press: a
    // pressed torch gave way to what the summoned Stand's collision ray met within its reach, here a cow behind the
    // torch, and nothing else; with the Stand looking away the ray met nothing and the press had no target. The cow
    // has no Stand, so the Stand disc press, once it has its target, asks for a disc to insert.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalsPressedAtATorchTakeWhatTheStandsRayMeetsAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-torch");
        Cow cow = EntityType.COW.create(helper.getLevel());
        BlockPos torch = null;
        StandEntity stand = null;
        try {
            stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            Ability memory = power.getAbility("removing_the_memory_disk");
            Ability standDisc = power.getAbility("whitesnake_remove_stand_disc");
            // the scene stays in the caster's chunk: the framework waits for that one only
            boolean south = (Mth.floor(caster.getZ()) & 15) <= 12;
            float yaw = south ? 0.0F : 180.0F;
            double dz = south ? 1.0D : -1.0D;
            caster.moveTo(caster.getX(), caster.getY(), caster.getZ(), yaw, 0.0F);
            caster.setYHeadRot(yaw);
            torch = BlockPos.containing(caster.getEyePosition()).relative(south ? Direction.SOUTH : Direction.NORTH);
            helper.getLevel().setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,
                    south ? Direction.NORTH : Direction.SOUTH), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.assertTrue(cow != null, "Fixture: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(caster.getX(), caster.getEyeY() - cow.getBbHeight() / 2.0D, caster.getZ() + 2.0D * dz, 0.0F,
                    0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Fixture: the cow could not enter the level");
            LivingComponentAction.getComponent(caster).entityAim.setTarget(
                    new ActionTarget(torch, south ? Direction.NORTH : Direction.SOUTH));
            // Whitesnake's eyes level with its user's, past the user's box, looking along the crosshair line
            stand.moveTo(caster.getX(), caster.getEyeY() - stand.getEyeHeight(), caster.getZ() + 0.35D * dz, yaw, 0.0F);
            stand.setYHeadRot(yaw);
            assertDiscCheck(helper, memory, power, null, "a torch with a cow behind it in the Stand's reach");
            assertDiscCheck(helper, standDisc, power, null,
                    "a torch with a cow behind it in the Stand's reach (a cow without a Stand: 1.16 asked for no disc)");
            EntityActionInstance hold = startPlacementAction(memory, stand, caster);
            ActionTarget held = hold.getTaskTarget(helper.getLevel());
            helper.assertTrue(held.getType() == ActionTarget.TargetType.ENTITY && held.getEntity() == cow,
                    "1.16's memory disc removal pressed at a torch took the cow behind it; the port's task holds " + held);
            stopPlacementAction(stand, caster);

            // Whitesnake looking up: its ray meets nothing, the press has no target
            stand.moveTo(caster.getX(), caster.getEyeY() - stand.getEyeHeight(), caster.getZ() + 0.35D * dz, yaw,
                    -90.0F);
            assertDiscCheck(helper, memory, power, "jojo.message.action_condition.whitesnake_disc_target",
                    "a torch with the Stand's ray meeting nothing");
            assertDiscCheck(helper, standDisc, power, "jojo.message.action_condition.whitesnake_disc_target",
                    "a torch with the Stand's ray meeting nothing");
            helper.succeed();
        }
        finally {
            if (torch != null) {
                helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (cow != null) {
                cow.discard();
            }
            if (stand != null) {
                stopPlacementAction(stand, caster);
            }
            disconnect(caster);
        }
    }

    // 1.16 noAdheringToUserOffset kept Whitesnake off its user's offset during both disc removals; a client that does
    // not know the user's power asks the fallback, which must agree.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void discRemovalsKeepTheStandOffItsUserOnEveryClient(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-fallback");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            for (String name : new String[] {"removing_the_memory_disk", "whitesnake_remove_stand_disc"}) {
                StandEntityAbility ability = (StandEntityAbility) power.getAbility(name);
                helper.assertTrue(ability.noAdheringToUserOffset(power, stand)
                                && ability.noAdheringToUserOffsetClientFallback(stand),
                        name + " keeps Whitesnake off its user's offset on the server ("
                                + ability.noAdheringToUserOffset(power, stand)
                                + ") and must on a client without the power too ("
                                + ability.noAdheringToUserOffsetClientFallback(stand) + ")");
            }
            helper.succeed();
        }
        finally {
            disconnect(caster);
        }
    }

    /*
     * 1.16 whitesnake_throw_disc (standPerformDuration(15)), removing_the_memory_disk (holdToFire(30)) and
     * whitesnake_remove_stand_disc (holdType + holdToFire(30)) set no standRecoveryTicks (004 InitStands :70, :75-76) and
     * their classes override no getStandRecoveryTicks: StandEntityAction.AbstractBuilder's recovery 0, so each task ended
     * with its perform and the next press went through. The memory disk's holdToFire(30, false) fired in the 30th held
     * tick (PowerBaseImpl.tickHeldAction) and its Stand's task ran the 1-tick perform in the Stand's next tick, the 31st;
     * the Stand disc is holdType and waited for the key.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 60)
    public static void discActionsEndWithTheirPerformAsIn116(GameTestHelper helper) {
        whenEntitiesTick(helper, () -> discActionsEndWithTheirPerformAsIn116Rows(helper));
    }

    private static void discActionsEndWithTheirPerformAsIn116Rows(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-no-recovery");
        Recorder target = player(helper, "ws-no-recovery-t");
        try {
            StandEntity stand = summon(helper, caster);
            StandEntity targetStand = summon(helper, target);
            StandPower power = StandPower.get(caster);
            // the ability, its 1.16 charge (held windup) and its 1.16 perform
            Object[][] rows = { {"whitesnake_throw_disc", 0, 15}, {"removing_the_memory_disk", 30, 1},
                    {"whitesnake_remove_stand_disc", 30, 1} };
            for (Object[] row : rows) {
                String name = (String) row[0];
                int charge = (Integer) row[1];
                int perform = (Integer) row[2];
                power.setStamina(power.getMaxStamina());
                caster.getInventory().clearContent();
                caster.setItemInHand(InteractionHand.MAIN_HAND,
                        StandDiscItem.withStand(new StandInstance(ModStands.STAR_PLATINUM.get())));
                stand.setPos(caster.position());
                placeTarget(target, targetStand, caster.position().add(0.0D, 0.0D, 3.0D));
                aimAt(caster, target);
                EntityActionInstance action = startPlacementAction(power.getAbility(name), stand, caster);
                float recovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
                boolean recovering = false;
                boolean performed = false;
                float stamina = power.getStamina();
                int ticks = 0;
                while (!action.isOver() && ticks < charge + perform + 20) {
                    // a full charge is let go
                    if (action.getPhase() == ActionPhase.WINDUP && action.getPhaseTick() >= action.getCurPhaseLength()) {
                        action.onButtonStopHold();
                    }
                    recovering |= action.getPhase() == ActionPhase.RECOVERY;
                    performed |= action.getPhase() == ActionPhase.PERFORM;
                    action._tickAction();
                    ticks++;
                }
                // a charge that fires as it ends performs in the tick after its last and is over with it; its effect,
                // the disc moved and the cost paid, shows the perform ran
                boolean firesWithItsCharge = charge > 0 && !name.equals("whitesnake_remove_stand_disc");
                boolean effect = !firesWithItsCharge
                        || power.getStamina() <= stamina - WhitesnakeRemoveMemoryDiscAbility.STAMINA_COST
                                && !memoryDisc(caster, target.getUUID()).isEmpty();
                helper.assertTrue(performed && effect && action.isOver() && !recovering && recovery == 0.0F
                                && (firesWithItsCharge ? ticks == charge + perform : ticks <= charge + perform + 1),
                        "1.16 gave " + name + " no standRecoveryTicks: its task ended with its " + perform
                                + "-tick perform after a " + charge + "-tick charge; the port has a recovery of " + recovery
                                + ", performed " + performed + " (effect " + effect + "), recovering " + recovering
                                + ", over " + action.isOver() + " after " + ticks + " ticks");
                helper.getLevel().getEntitiesOfClass(DiscProjectile.class, new AABB(caster.blockPosition()).inflate(16.0D))
                        .forEach(DiscProjectile::discard);
                ItemStack memory = memoryDisc(caster, target.getUUID());
                if (!memory.isEmpty()) {
                    MemoryDiscService.restore(memory, target);
                }
                stopPlacementAction(stand, caster);
            }
            helper.succeed();
        }
        finally {
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 MeltYourHeart was holdType(100) with no recovery: a Stand action pressed during the held stream replaced it at
    // once (StandEntityAction.checkTaskCancelling, isCancelable) and the hold ended with its cooldown (PowerBaseImpl
    // .onClickAction -> stopHeldAction(false)); an action of the power the Stand does not perform ended the hold the same
    // way, and the manual control key, no 1.16 action, left it held.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 60)
    public static void aPunchPressedDuringMeltYourHeartReplacesItAtOnceAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-melt-gives-way");
        try {
            StandEntity stand = summon(helper, caster);
            StandPower power = StandPower.get(caster);
            LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
            for (String pressed : new String[] {"punch", "heavy_punch"}) {
                EntityActionInstance melt = holdMelt(helper, power, caster, stand);
                BufferingState state = pressKey(helper, power, caster, pressed, InputMethod.CLICK, OTHER_KEY);
                EntityActionInstance now = standAction.getAction();
                helper.assertTrue(state.isActionSuccess && !state.shouldBuffer && now != null && now != melt
                                && now.ability == power.getAbility(pressed),
                        "1.16 let a " + pressed + " pressed during the held Melt Your Heart through at once (holdType,"
                                + " isCancelable): started " + state.isActionSuccess + ", buffered " + state.shouldBuffer
                                + ", the Stand's action " + now);
                int cooldown = power.getAbilityCooldown("melt_your_heart");
                helper.assertTrue(melt.isOver() && cooldown == 120,
                        "1.16 ended Melt Your Heart replaced by the " + pressed + " with its 120-tick cooldown"
                                + " (stopHeldAction(false)): over " + melt.isOver() + ", cooldown " + cooldown);
                clearKeys(power, caster, stand);
            }

            // the manual control key was no action in 1.16 (StandUtil.setManualControl) and left the hold as it was
            EntityActionInstance melt = holdMelt(helper, power, caster, stand);
            BufferingState manual = pressKey(helper, power, caster, "manual_control", InputMethod.CLICK, OTHER_KEY);
            AbilityInput.keyRelease(OTHER_KEY, caster);
            helper.assertTrue(manual.isActionSuccess && stand.isManuallyControlled(),
                    "setup: the manual control key did not take Whitesnake over");
            helper.assertTrue(!melt.isOver() && melt.getPhase() == ActionPhase.PERFORM && standAction.getAction() == melt
                            && isHeldByKey(caster, HOLD_KEY),
                    "1.16 kept Melt Your Heart held when its user took the Stand over: phase " + melt.getPhase());
            StandEntityManualControlToggle.off(helper.getLevel(), stand, false);

            // Whitesnake's other abilities are all Stand actions; a plain ability of the power (as add-ons give
            // Stands) goes off and ends the hold as letting go of its key does
            Ability base = power.getAbility("manual_control");
            Ability plain = new Ability(base.abilityType,
                    new AbilityId(PowerClass.STAND, base.getAbilityId().powerTypeId(), "w2_plain_press"));
            BufferingState plainPress = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(OTHER_KEY, plain, caster, null, InputMethod.CLICK, 0, plainPress, plain.getAbilityId());
            AbilityInput.keyRelease(OTHER_KEY, caster);
            int cooldown = power.getAbilityCooldown("melt_your_heart");
            helper.assertTrue(plainPress.isActionSuccess && (melt.isOver() || melt.getPhase() == ActionPhase.RECOVERY)
                            && !isHeldByKey(caster, HOLD_KEY) && cooldown == 120,
                    "1.16 ended the held Melt Your Heart once another action of the power went off"
                            + " (stopHeldAction(false)), with its cooldown: phase " + melt.getPhase() + ", its key holds it "
                            + isHeldByKey(caster, HOLD_KEY) + ", cooldown " + cooldown);
            clearKeys(power, caster, stand);
            helper.succeed();
        }
        finally {
            clearInput(caster);
            disconnect(caster);
        }
    }

    /*
     * 1.16's client sent no press of a held action (getHoldDurationMax above 0) while the power held another
     * (ActionsOverlayGui.onActionClick :2121-2123): Blindness (holdType + holdToFire(5)), both disc removals
     * (holdToFire(30)) and Melt Your Heart (holdType(100)) pressed during the held guard or barrage did nothing, and the
     * guard or barrage went on; the input buffer keeps such a press until the hold ends. The barrage is an arms-only
     * Whitesnake's, which has no recovery (StandEntityMeleeBarrage.getStandRecoveryTicks) and so gives way to a press.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 60)
    public static void aHeldAbilityPressedDuringTheHeldGuardOrBarrageWaitsAsIn116(GameTestHelper helper) {
        Recorder caster = player(helper, "ws-held-waits");
        Recorder target = player(helper, "ws-held-waits-t");
        try {
            StandEntity stand = summon(helper, caster);
            StandEntity targetStand = summon(helper, target);
            StandPower power = StandPower.get(caster);
            LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
            for (String holdName : new String[] {"guard", "barrage"}) {
                for (String pressed : new String[] {"blindness", "removing_the_memory_disk",
                        "whitesnake_remove_stand_disc", "melt_your_heart"}) {
                    stand.setArmsOnlyMode(holdName.equals("barrage"), true);
                    stand.setPos(caster.position());
                    placeTarget(target, targetStand, caster.position().add(0.0D, 0.0D, 3.0D));
                    aimAt(caster, target);
                    power.setStamina(power.getMaxStamina());
                    BufferingState holdPress = pressKey(helper, power, caster, holdName, InputMethod.HOLD, HOLD_KEY);
                    EntityActionInstance hold = standAction.getAction();
                    helper.assertTrue(holdPress.isActionSuccess && hold != null
                                    && hold.ability == power.getAbility(holdName) && isHeldByKey(caster, HOLD_KEY)
                                    && hold.canBeCancelledInto((EntityActionType) power.getAbility(pressed)),
                            "setup: the " + holdName + " is not held or would not let " + pressed + " replace it: started "
                                    + holdPress.isActionSuccess + ", the Stand's action " + hold);
                    ActionPhase phase = hold.getPhase();
                    BufferingState press = pressKey(helper, power, caster, pressed, InputMethod.HOLD, OTHER_KEY);
                    helper.assertTrue(press.shouldBuffer && !press.isActionSuccess && standAction.getAction() == hold
                                    && !hold.isOver() && hold.getPhase() == phase && isHeldByKey(caster, HOLD_KEY),
                            "1.16's client sent no " + pressed + " (a held action) pressed while the " + holdName
                                    + " was held, which the input buffer keeps: buffered " + press.shouldBuffer
                                    + ", started " + press.isActionSuccess + ", the Stand's action "
                                    + standAction.getAction() + ", the " + holdName + " in " + hold.getPhase());
                    AbilityInput.keyRelease(OTHER_KEY, caster);
                    AbilityInput.keyRelease(HOLD_KEY, caster);
                    clearInput(caster);
                    standAction.setAction(null, caster, SyncType.NO_SYNC);
                    power.setAbilityCooldown(holdName, 0, 0);
                }
            }
            helper.succeed();
        }
        finally {
            clearInput(caster);
            disconnect(target);
            disconnect(caster);
        }
    }

    // 1.16 004 InitStands :69 whitesnake_heavy_punch sets no standPose and WhitesnakeModel.initActionPoses :53-64 puts
    // no HEAVY_ATTACK pose, so Whitesnake drew the core's default humanoid heavy: the port's copy is the core default
    // heavy_punch (the official hooking punch) with the core's one-tick markers; only its head is Whitesnake's own,
    // re-hosted for its rig (D-rotp-whitesnake-01).
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 20)
    public static void heavyPunchKeepsTheCoreDefaultHookingPunch(GameTestHelper helper) {
        JsonObject coreHeavy = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/stand_skins/star_platinum/assets/jojo_ripples/animations/"
                        + "star_platinum.animation.json", "heavy_punch");
        JsonObject coreDefault = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/animations/stand_default.animation.json", "heavy_punch");
        JsonObject markers = JsonParser.parseString("{\"0.0\":\"phase = WINDUP;\","
                + "\"0.4333\":\"phase = PERFORM;\",\"0.4833\":\"phase = RECOVERY;\"}").getAsJsonObject();
        helper.assertTrue(markers.equals(coreDefault.get("timeline")) && markers.equals(coreHeavy.get("timeline")),
                "setup: the core default heavy_punch no longer has PERFORM 0.4333 / RECOVERY 0.4833: "
                        + coreDefault.get("timeline") + ", Star Platinum " + coreHeavy.get("timeline"));
        JsonObject heavy = animationClip(helper, WhitesnakeAddon.MOD_ID,
                "assets/rotp_whitesnake/stand_skins/whitesnake/assets/rotp_whitesnake/animations/"
                        + "whitesnake.animation.json", "heavy_punch");
        helper.assertTrue(markers.equals(heavy.get("timeline")),
                "Whitesnake's heavy_punch must keep the core default's PERFORM 0.4333 / RECOVERY 0.4833 (the thrust"
                        + " lands at 0.4667 in the one-tick perform): " + heavy.get("timeline"));
        helper.assertTrue(coreHeavy.get("animation_length").equals(heavy.get("animation_length"))
                        && coreHeavy.get("loop").equals(heavy.get("loop")),
                "Whitesnake's heavy_punch length or loop differs from the core default's");
        JsonObject bones = heavy.getAsJsonObject("bones");
        JsonObject coreBones = coreHeavy.getAsJsonObject("bones");
        helper.assertTrue(bones.has("right_arm") && bones.has("right_arm_bend") && bones.has("head"),
                "Whitesnake's heavy_punch lost its striking arm or its head: " + bones.keySet());
        for (String bone : bones.keySet()) {
            helper.assertTrue(bone.equals("head") || bones.get(bone).equals(coreBones.get(bone)),
                    "Whitesnake's heavy_punch must stay the core default's hooking punch: " + bone + " "
                            + bones.get(bone) + ", core " + coreBones.get(bone));
        }
        helper.succeed();
    }

    // 1.16 004 InitStands :68 whitesnake_combo_punch, the heavy punch's finisher, sets no standPose either: the default
    // humanoid finisher, the core's stand_default finisher_uppercut, not Star Platinum's own; the head is re-hosted.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 20)
    public static void finisherPunchKeepsTheCoreDefaultFinisher(GameTestHelper helper) {
        JsonObject core = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/animations/stand_default.animation.json", "finisher_uppercut");
        JsonObject finisher = animationClip(helper, WhitesnakeAddon.MOD_ID,
                "assets/rotp_whitesnake/stand_skins/whitesnake/assets/rotp_whitesnake/animations/"
                        + "whitesnake.animation.json", "finisher_punch");
        for (String field : List.of("loop", "animation_length", "timeline")) {
            helper.assertTrue(Objects.equals(core.get(field), finisher.get(field)),
                    "Whitesnake's finisher_punch " + field + " differs from the core default finisher's: "
                            + finisher.get(field) + ", core " + core.get(field));
        }
        JsonObject bones = finisher.getAsJsonObject("bones");
        JsonObject coreBones = core.getAsJsonObject("bones");
        helper.assertTrue(bones.has("right_arm") && bones.has("body") && bones.has("head"),
                "Whitesnake's finisher_punch lost its arm, body or head: " + bones.keySet());
        for (String bone : bones.keySet()) {
            helper.assertTrue(bone.equals("head") || bone.equals("head_rot")
                            || bones.get(bone).equals(coreBones.get(bone)),
                    "Whitesnake's finisher_punch must stay the core default finisher: " + bone + " "
                            + bones.get(bone) + ", core " + coreBones.get(bone));
        }
        helper.succeed();
    }

    // 1.16 004 InitStands :71 whitesnake_block is a plain StandEntityBlock (StandPose.BLOCK) and WhitesnakeModel puts
    // no BLOCK pose, so Whitesnake drew HumanoidStandModel's guard (:590-607): crossed forearms, arms following the look
    // pitch up to 60 degrees. The core's stand_default block is that guard; Star Platinum's static one is its own clip.
    // 1.16 LegacyStandAnimator.poseStand :73 posed Whitesnake's whole idle (WhitesnakeModel.initIdlePose :67-69) first
    // and the BLOCK pose names no leg, so the guard kept Whitesnake's idle legs, not stand_default's leg stance.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 20)
    public static void guardKeepsTheDefaultPitchFollowingGuard(GameTestHelper helper) {
        JsonObject core = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/animations/stand_default.animation.json", "block");
        JsonObject star = animationClip(helper, "jojo_ripples",
                "assets/jojo_ripples/stand_skins/star_platinum/assets/jojo_ripples/animations/"
                        + "star_platinum.animation.json", "block");
        JsonObject coreBones = core.getAsJsonObject("bones");
        helper.assertTrue(coreBones.has("right_arm")
                        && coreBones.get("right_arm").toString().contains("math.min(60, query.head_x_rotation)")
                        && !coreBones.equals(star.getAsJsonObject("bones")),
                "setup: the core default block is no longer the pitch-following humanoid guard: " + coreBones);
        String file = "assets/rotp_whitesnake/stand_skins/whitesnake/assets/rotp_whitesnake/animations/"
                + "whitesnake.animation.json";
        JsonObject idle = animationClip(helper, WhitesnakeAddon.MOD_ID, file, "idle").getAsJsonObject("bones");
        List<String> idleLegs = List.of("left_leg", "right_leg", "left_leg_bend", "right_leg_bend");
        for (String clip : List.of("block", "guard")) {
            JsonObject guard = animationClip(helper, WhitesnakeAddon.MOD_ID, file, clip);
            helper.assertTrue(Objects.equals(core.get("loop"), guard.get("loop"))
                            && Objects.equals(core.get("animation_length"), guard.get("animation_length")),
                    "Whitesnake's " + clip + " loop or length differs from the core default guard's");
            JsonObject bones = guard.getAsJsonObject("bones");
            helper.assertTrue(bones.keySet().equals(coreBones.keySet()),
                    "Whitesnake's " + clip + " bones differ from the core default guard's: " + bones.keySet());
            for (String bone : bones.keySet()) {
                if (idleLegs.contains(bone)) {
                    JsonObject idleRotation = idle.getAsJsonObject(bone).getAsJsonObject("rotation");
                    JsonElement idleFirst = idleRotation.has("vector") ? idleRotation.get("vector")
                            : idleRotation.getAsJsonObject("0.0").get("vector");
                    JsonElement held = bones.getAsJsonObject(bone).getAsJsonObject("rotation").get("vector");
                    helper.assertTrue(idleFirst.equals(held) && !bones.get(bone).equals(coreBones.get(bone)),
                            "Whitesnake's " + clip + " must keep Whitesnake's own idle " + bone + " as 1.16's guard"
                                    + " did: " + bones.get(bone) + ", idle " + idleFirst + ", core default "
                                    + coreBones.get(bone));
                    continue;
                }
                helper.assertTrue(bones.get(bone).equals(coreBones.get(bone)),
                        "Whitesnake's " + clip + " must stay the core default pitch-following guard: " + bone + " "
                                + bones.get(bone) + ", core " + coreBones.get(bone));
            }
        }
        helper.succeed();
    }

    // The port's charged heavy (no 1.16 counterpart) strikes at the end of its PERFORM (StandEntityChargedHeavy
    // .actionPerformEnd). Whitesnake's heavy_charged is its heavy_punch re-timed by 2.4667 s from the pull-back at
    // 0.3333 (heavy_charged_fix.py), so its PERFORM ends where heavy_punch's does, just after the arm's full extension.
    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 20)
    public static void chargedHeavyStrikesRightAfterItsHeavyPunchThrust(GameTestHelper helper) {
        String file = "assets/rotp_whitesnake/stand_skins/whitesnake/assets/rotp_whitesnake/animations/"
                + "whitesnake.animation.json";
        JsonObject heavy = animationClip(helper, WhitesnakeAddon.MOD_ID, file, "heavy_punch");
        JsonObject charged = animationClip(helper, WhitesnakeAddon.MOD_ID, file, "heavy_charged");
        double shift = 2.4667D;
        double heavyRecovery = phaseStart(heavy, "RECOVERY");
        double chargedRecovery = phaseStart(charged, "RECOVERY");
        helper.assertTrue(Math.abs(phaseStart(charged, "PERFORM") - (0.3333D + shift)) < 1.0E-4D
                        && Math.abs(chargedRecovery - (heavyRecovery + shift)) < 1.0E-4D,
                "Whitesnake's heavy_charged must end its PERFORM where its heavy_punch does, re-timed by " + shift
                        + " s: heavy_punch RECOVERY " + heavyRecovery + ", heavy_charged " + charged.get("timeline"));
        JsonObject bones = heavy.getAsJsonObject("bones");
        JsonObject chargedBones = charged.getAsJsonObject("bones");
        int keys = 0;
        for (String bone : bones.keySet()) {
            if (bone.equals("head")) {
                continue;
            }
            for (String channel : bones.getAsJsonObject(bone).keySet()) {
                JsonObject frames = bones.getAsJsonObject(bone).getAsJsonObject(channel);
                if (frames.has("vector")) {
                    continue;
                }
                for (String time : frames.keySet()) {
                    double at = Double.parseDouble(time);
                    if (at <= 0.3333D + 1.0E-9D) {
                        continue;
                    }
                    String shifted = String.valueOf(Math.round((at + shift) * 10000.0D) / 10000.0D);
                    JsonObject chargedFrames = chargedBones.getAsJsonObject(bone).getAsJsonObject(channel);
                    helper.assertTrue(frames.get(time).equals(chargedFrames.get(shifted)),
                            "Whitesnake's heavy_charged is no longer its heavy_punch re-timed: " + bone + " "
                                    + channel + " " + time + " -> " + shifted + " " + chargedFrames.get(shifted));
                    keys++;
                }
            }
        }
        helper.assertTrue(keys > 0, "setup: no heavy_punch keyframe after the pull-back was compared");
        helper.succeed();
    }

    private static double phaseStart(JsonObject clip, String phase) {
        for (String time : clip.getAsJsonObject("timeline").keySet()) {
            if (clip.getAsJsonObject("timeline").get(time).toString().contains("phase = " + phase + ";")) {
                return Double.parseDouble(time);
            }
        }
        return Double.NaN;
    }

    private static JsonObject animationClip(GameTestHelper helper, String modId, String file, String clip) {
        JsonObject animations;
        try {
            Path path = ModList.get().getModFileById(modId).getFile().findResource(file);
            animations = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read " + modId + " " + file, e);
        }
        helper.assertTrue(animations != null && animations.has(clip), modId + " " + file + " has no " + clip);
        return animations.getAsJsonObject(clip);
    }

    // pressed by its key as ClAbilityInputPacket does, kept held, and ticked into its perform
    private static EntityActionInstance holdMelt(GameTestHelper helper, StandPower power, Recorder caster,
            StandEntity stand) {
        power.setStamina(power.getMaxStamina());
        BufferingState state = pressKey(helper, power, caster, "melt_your_heart", InputMethod.HOLD, HOLD_KEY);
        EntityActionInstance melt = LivingComponentAction.getComponent(stand).getAction();
        helper.assertTrue(state.isActionSuccess && melt != null && melt.ability == power.getAbility("melt_your_heart")
                        && isHeldByKey(caster, HOLD_KEY),
                "setup: Melt Your Heart is not held: started " + state.isActionSuccess + ", the Stand's action " + melt);
        for (int tick = 0; tick < 3 && melt.getPhase() != ActionPhase.PERFORM; tick++) {
            melt._tickAction();
        }
        helper.assertTrue(melt.getPhase() == ActionPhase.PERFORM && power.getAbilityCooldown("melt_your_heart") == 0,
                "setup: Melt Your Heart is not streaming without a cooldown: " + melt.getPhase());
        return melt;
    }

    // the key's resolved ability, its condition check, then the press
    private static BufferingState pressKey(GameTestHelper helper, StandPower power, Recorder caster, String name,
            InputMethod inputMethod, short key) {
        BufferingState state = BufferingState.clickCanBuffer();
        Ability base = power.getAbility(name);
        helper.assertTrue(base != null, "Missing ability " + name);
        AbilityConditionCheck container = resolve(power, name);
        if (container != null && AbilityInput.withConditionCheck(container, caster, inputMethod)) {
            AbilityInput.keyPress(key, container.ability, caster, null, inputMethod, 0, state, base.getAbilityId());
        }
        return state;
    }

    // the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
    private static AbilityConditionCheck resolve(StandPower power, String name) {
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(power, false);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
    }

    private static boolean isHeldByKey(Recorder caster, short key) {
        EntityActionInputState input = caster.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
        return input.heldKeys.containsKey(key) && input.heldKeys.get(key).action != null;
    }

    private static void clearKeys(StandPower power, Recorder caster, StandEntity stand) {
        clearInput(caster);
        LivingComponentAction.getComponent(stand).setAction(null, caster, SyncType.NO_SYNC);
        power.setAbilityCooldown("melt_your_heart", 0, 0);
    }

    private static void clearInput(Recorder caster) {
        caster.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
    }

    private static void placeTarget(Recorder target, StandEntity targetStand, Vec3 pos) {
        target.setPos(pos);
        targetStand.setPos(pos);
    }

    // expected: null a positive check, "" a refusal without a message, else the message key
    private static void assertDiscCheck(GameTestHelper helper, Ability ability, StandPower power, String expected,
            String when) {
        assertCheck(helper, ability.checkSpecificConditions(power), expected,
                "the click of " + ability.getAbilityId().nameInMoveset() + " at " + when);
    }

    private static void assertCheck(GameTestHelper helper, ConditionCheck check, String expected, String when) {
        String actual;
        if (check.isPositive()) {
            actual = null;
        }
        else if (check.getWarning() == null) {
            actual = "";
        }
        else if (check.getWarning().getContents() instanceof TranslatableContents translated) {
            actual = translated.getKey();
        }
        else {
            actual = "?";
        }
        helper.assertTrue(Objects.equals(expected, actual),
                "1.16 answered " + when + " with " + describeCheck(expected) + ", the port answered "
                        + describeCheck(actual));
    }

    private static String describeCheck(String warning) {
        return warning == null ? "a positive check" : warning.isEmpty() ? "a refusal without a message" : warning;
    }

    private static EntityActionInstance startPlacementAction(Ability ability, StandEntity stand, Recorder caster) {
        EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(
                caster.level(), caster, stand, null);
        LivingComponentAction.getComponent(stand).setAction(action, caster, SyncType.NO_SYNC);
        return action;
    }

    // a Stand left far from its user is retracted when its action ends
    private static void stopPlacementAction(StandEntity stand, Recorder caster) {
        stand.setPos(caster.position());
        LivingComponentAction.getComponent(stand).setAction(null, caster, SyncType.NO_SYNC);
    }

    private static void turnPlacementUser(Recorder user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertPlacedAt(GameTestHelper helper, Recorder user, StandEntity stand, boolean following,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(stand.isFollowingUser() == following && Math.abs(actual.x - dx) < 1.0E-4D
                        && Math.abs(actual.y - dy) < 1.0E-4D && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put Whitesnake at its user's feet + (" + dx + ", " + dy + ", " + dz + "), following its user "
                        + following + ", during " + when + ", got " + actual + " (following " + stand.isFollowingUser()
                        + ", offset " + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    private static boolean playsBarrageCry(EntityActionInstance barrage, StandEntity stand) {
        try {
            Method method = StandEntityBarrage.class.getDeclaredMethod("playsBarrageCry", Level.class, StandEntity.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(barrage, stand.level(), stand);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing barrage cry hook", e);
        }
    }

    // Runs the rows once the test column's chunk ticks entities. The framework starts a test when the structure's chunk
    // is loaded, and under load a world query still misses what stands there (a target by its id, a shot drop).
    private static void whenEntitiesTick(GameTestHelper helper, Runnable rows) {
        int[] waited = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            waited[0]++;
            helper.assertTrue(waited[0] < 30, "Setup: the test column's chunk never ticked entities");
            if (waited[0] < 2 || !helper.getLevel().isPositionEntityTicking(helper.absolutePos(new BlockPos(0, 2, 0)))) {
                return;
            }
            done[0] = true;
            rows.run();
        });
    }

    private static StandEntity summon(GameTestHelper helper, Recorder player) {
        StandPower power = StandPower.get(player);
        helper.assertTrue(power != null, "Player has no Stand attachment");
        power.setStand(WhitesnakeAddon.WHITESNAKE.get());
        power.skipProgression();
        helper.assertTrue(WhitesnakeAddon.WHITESNAKE.get().summon(player, power), "Stand did not summon");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null, "Summoned Stand is absent");
        stand.setPos(player.position());
        helper.assertTrue(!power.isStaminaInfinite(), "Fixture: stamina is infinite");
        return stand;
    }

    private static void aimAt(Recorder caster, LivingEntity target) {
        LivingComponentAction.getComponent(caster);
        LivingComponentAction.getAim(caster).setTarget(target != null ? new ActionTarget(target) : ActionTarget.EMPTY);
    }

    // The perform boundary the Stand task reaches once the hold is released.
    private static void perform(Ability ability, StandEntity performer, Recorder caster) {
        perform(ability, performer, caster, () -> {});
    }

    // beforePerform runs between the press and the perform, where the hold or windup was.
    private static void perform(Ability ability, StandEntity performer, Recorder caster, Runnable beforePerform) {
        EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(
                caster.level(), caster, performer, null);
        LivingComponentAction.getComponent(performer).setAction(action, caster, SyncType.NO_SYNC);
        try {
            beforePerform.run();
            action.actionPerformStart();
        }
        finally {
            LivingComponentAction.getComponent(performer).setAction(null, caster, SyncType.NO_SYNC);
        }
    }

    private static ItemStack memoryDisc(ServerPlayer player, UUID target) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (MemoryDiscItem.getData(stack).filter(data -> data.targetId().equals(target)).isPresent()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Recorder player(GameTestHelper helper, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        Recorder player = new Recorder(helper.getLevel(), profile, cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        // The structure block's own column: the framework loads and waits only for the 1x1x1 structure's chunk, and a
        // world query misses what stands in a chunk whose entities are not visible yet (a drop shot there is not found).
        player.setPos(helper.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D)));
        player.listen(connection, cookie);
        return player;
    }

    private static void disconnect(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        if (power != null && power.hasPower()) {
            power.getPowerType().forceUnsummon(player, power);
        }
        player.connection.disconnect(Component.literal("Whitesnake stamina test complete"));
    }

    private static final class Recorder extends ServerPlayer {
        private final List<String> actionBarKeys = new ArrayList<>();
        private final List<Packet<?>> sent = new ArrayList<>();

        private Recorder(ServerLevel level, GameProfile profile, CommonListenerCookie cookie) {
            super(level.getServer(), level, profile, cookie.clientInformation());
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar && actionBarKeys != null) {
                actionBarKeys.add(message.getContents() instanceof TranslatableContents translated
                        ? translated.getKey() : message.getString());
            }
            super.displayClientMessage(message, actionBar);
        }

        // The mock player is not ticked: its tracking is moved to where it stands and its own chunk counts as sent, so
        // it tracks its Stand and the Stand's sound packets reach it.
        private void listen(Connection connection, CommonListenerCookie cookie) {
            new Recording(this, connection, cookie);
            serverLevel().getChunkSource().move(this);
            this.connection.chunkSender.dropChunk(this, chunkPosition());
        }

        private int standSounds(StandEntity stand) {
            int count = 0;
            for (Packet<?> packet : List.copyOf(sent)) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof StandEntitySoundPacket sound
                        && sound.entityId() == stand.getId()
                        && sound.sound().value() == WhitesnakeAddon.WHITESNAKE_REMOVE_DISC.get()) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class Recording extends ServerGamePacketListenerImpl {
        private final Recorder recorder;

        private Recording(Recorder player, Connection connection, CommonListenerCookie cookie) {
            super(player.getServer(), connection, player, cookie);
            this.recorder = player;
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            if (recorder != null) {
                recorder.sent.add(packet);
            }
            super.send(packet, listener);
        }
    }
}
