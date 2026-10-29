package rotp.addons.achtungbaby.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.achtungbaby.AchtungBabyAddon;
import rotp.addons.achtungbaby.entity.InvisibilityRuneEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(AchtungBabyPerfectInvisibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AchtungBabyRuneEffectsGameTests {
    private static final String NAMESPACE = AchtungBabyPerfectInvisibilityGameTests.NAMESPACE;
    private static final Vec3 USER_AT = new Vec3(0.5D, 2.0D, 0.5D);

    private AchtungBabyRuneEffectsGameTests() {}

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 60)
    public static void harmfulRuneAppliesTheMobAndPlayerResolveTiers(GameTestHelper helper) {
        harmfulCase(helper, 0);
    }

    private static void harmfulCase(GameTestHelper helper, int index) {
        if (index == 5) {
            helper.succeed();
            return;
        }
        ServerPlayer user = player(helper, "ABHarmOwner", helper.absoluteVec(USER_AT));
        LivingEntity target;
        Vec3 targetAt = helper.absoluteVec(USER_AT.add(0.0D, 1.52D + 4.2D, 0.0D));
        if (index == 0) {
            target = pig(helper, targetAt);
        } else {
            ServerPlayer player = player(helper, "ABHarmTarget", targetAt);
            StandPower power = PowerClass.STAND.attachGet(player);
            if (index < 4) {
                power.setStand(AchtungBabyAddon.ACHTUNG_BABY.get());
                power.setResolveLevel(index - 1);
            } else {
                helper.assertTrue(!power.hasPower(), "Setup: the unpowered target has a Stand");
            }
            target = player;
        }
        InvisibilityRuneEntity rune = InvisibilityRuneEntity.launch(user, true);
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(rune.isRemoved(), "The harmful rune did not hit its target");
                int duration = index == 0 ? 200 : index == 3 ? 400 : 300;
                effect(helper, target, MobEffects.WEAKNESS, index == 0 || index == 3 ? 3 : 2, duration);
                effect(helper, target, MobEffects.MOVEMENT_SLOWDOWN, index == 0 ? 3 : 2, duration);
                effect(helper, target, index == 0 ? MobEffects.CONFUSION : MobEffects.DIG_SLOWDOWN,
                        index == 0 ? 0 : index == 3 ? 2 : 1, duration);
            } finally {
                rune.discard();
                remove(helper, target);
                remove(helper, user);
            }
            harmfulCase(helper, index + 1);
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void beneficialRuneStacksInvisibilityAndAddsTwoDistinctDonorBuffs(GameTestHelper helper) {
        ServerPlayer user = player(helper, "ABGrantOwner", helper.absoluteVec(USER_AT));
        Pig target = pig(helper, helper.absoluteVec(USER_AT.add(0.0D, 1.52D + 4.2D, 0.0D)));
        target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, true, true, false));
        InvisibilityRuneEntity rune = InvisibilityRuneEntity.launch(user, false);
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(rune.isRemoved(), "The beneficial rune did not hit its target");
                MobEffectInstance invisible = target.getEffect(MobEffects.INVISIBILITY);
                helper.assertTrue(invisible != null && invisible.getAmplifier() == 0
                                && invisible.getDuration() >= 330 && invisible.getDuration() <= 340,
                        "Grant did not add 240 ticks to the existing invisibility");
                helper.assertTrue(!invisible.isAmbient() && !invisible.isVisible() && invisible.showIcon(),
                        "Grant invisibility did not use non-ambient, hidden-particle, visible-icon flags");
                List<Holder<MobEffect>> allowed = List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED,
                        MobEffects.DAMAGE_BOOST, MobEffects.JUMP, MobEffects.REGENERATION,
                        MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING,
                        MobEffects.NIGHT_VISION, MobEffects.HEALTH_BOOST, MobEffects.ABSORPTION,
                        MobEffects.SATURATION, MobEffects.LUCK);
                List<MobEffectInstance> buffs = target.getActiveEffects().stream()
                        .filter(effect -> !effect.is(MobEffects.INVISIBILITY)).toList();
                helper.assertTrue(buffs.size() == 2 && !buffs.get(0).getEffect().equals(buffs.get(1).getEffect()),
                        "Grant did not select two distinct buffs");
                for (MobEffectInstance buff : buffs) {
                    helper.assertTrue(allowed.contains(buff.getEffect()) && buff.getDuration() >= 196
                                    && buff.getDuration() <= 200 && buff.getAmplifier() >= 0
                                    && buff.getAmplifier() <= 4,
                            "Grant selected an unexpected buff, duration or amplifier: " + buff);
                    helper.assertTrue(!buff.isAmbient() && !buff.isVisible() && buff.showIcon(),
                            "Grant buff did not use non-ambient, hidden-particle, visible-icon flags: " + buff);
                }
            } finally {
                rune.discard();
                target.discard();
                remove(helper, user);
            }
            helper.succeed();
        });
    }

    private static void effect(GameTestHelper helper, LivingEntity target, Holder<MobEffect> type,
            int amplifier, int duration) {
        MobEffectInstance effect = target.getEffect(type);
        helper.assertTrue(effect != null && effect.getAmplifier() == amplifier
                        && effect.getDuration() >= duration - 4 && effect.getDuration() <= duration,
                "Unexpected harmful rune effect " + type + ": " + effect);
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 at) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(at);
        player.setXRot(-90.0F);
        player.setYRot(0.0F);
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static Pig pig(GameTestHelper helper, Vec3 at) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.setPos(at);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        helper.getLevel().addFreshEntity(pig);
        return pig;
    }

    private static void remove(GameTestHelper helper, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        } else {
            entity.discard();
        }
    }
}
