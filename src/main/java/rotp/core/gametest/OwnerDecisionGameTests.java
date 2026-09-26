package rotp.core.gametest;

import java.util.List;

import io.netty.buffer.Unpooled;
import rotp.core.JojoModConfig;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModStands;
import rotp.core.mechanics.standarrow.StandArrowEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("rotp_owner_decisions")
@PrefixGameTestTemplate(false)
public final class OwnerDecisionGameTests {
    private OwnerDecisionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void arrowDurabilityAndClientSyncMatchDonor(GameTestHelper helper) {
        ModConfigSpec.ConfigValue<Integer> ordinary = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "arrowDurability"));
        ModConfigSpec.ConfigValue<Integer> beetle = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "arrowDurabilityBeetle"));
        int oldOrdinary = ordinary.get();
        int oldBeetle = beetle.get();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ordinary.set(25);
            beetle.set(250);
            helper.assertTrue(ModItems.STAND_ARROW.toStack().getMaxDamage() == 25,
                    "Ordinary donor arrow durability must be 25");
            helper.assertTrue(ModItems.STAND_ARROW_METEORITE.toStack().getMaxDamage() == 25,
                    "Crafted arrow durability must remain 25");
            ItemStack stack = ModItems.STAND_ARROW_BEETLE.toStack();
            helper.assertTrue(stack.isDamageableItem() && stack.getMaxDamage() == 250,
                    "Beetle arrow must be damageable with 250 durability");
            ordinary.set(41);
            beetle.set(333);
            helper.assertTrue(ModItems.STAND_ARROW.toStack().getMaxDamage() == 41
                    && ModItems.STAND_ARROW_METEORITE.toStack().getMaxDamage() == 41
                    && stack.getMaxDamage() == 333, "Arrow variants must read their live settings");
            new JojoModConfig.Common.SyncedValues(JojoModConfig.getCommonConfigInstance(false)).writeToBuf(buf);
            JojoModConfig.applySyncedConfig(new JojoModConfig.Common.SyncedValues(buf));
            helper.assertTrue(buf.readableBytes() == 0
                    && JojoModConfig.getCommonConfigInstance(true).arrowDurability.get() == 41
                    && JojoModConfig.getCommonConfigInstance(true).arrowDurabilityBeetle.get() == 333,
                    "Both durability values must survive client config serialization");
        }
        finally {
            ordinary.set(oldOrdinary);
            beetle.set(oldBeetle);
            JojoModConfig.resetSyncedConfig();
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void beetleArrowActuallyWearsAndBreaks(GameTestHelper helper) {
        LivingEntity shooter = EntityType.ZOMBIE.create(helper.getLevel());
        LivingEntity target = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(shooter != null && target != null, "Missing test entities");
        ItemStack stack = ModItems.STAND_ARROW_BEETLE.toStack();
        stack.setDamageValue(stack.getMaxDamage() - 1);
        StandArrowEntity arrow = new StandArrowEntity(helper.getLevel(), 0, 62, 0, stack, null);
        try {
            arrow.setOwner(shooter);
            arrow.setBaseDamage(2);
            arrow.setDeltaMovement(new Vec3(1, 0, 0));
            var hit = StandArrowEntity.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
            hit.setAccessible(true);
            hit.invoke(arrow, new EntityHitResult(target));
            helper.assertTrue(arrow.isRemoved(), "The last durability point must break a beetle projectile");
        }
        catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
        finally {
            arrow.discard();
            shooter.discard();
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void beetleEnchantingRemainsAvailable(GameTestHelper helper) {
        StandArrowBeetleEnchantGameTests.enchantingTableOffersEnchantmentsForBeetleArrow(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aboveCapResolveGatesRespectProgressionBypass(GameTestHelper helper) {
        ModConfigSpec.ConfigValue<Boolean> skip = JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "Stand Progression", "skipStandProgression"));
        boolean oldSkip = skip.get();
        Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
        Ability ability = null;
        int oldLevel = -1;
        try {
            skip.set(false);
            player.getAbilities().instabuild = false;
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(ModStands.THE_WORLD.get());
            power.setResolveLevel(power.getMaxResolveLevel());
            ability = power.getAbility("punch");
            helper.assertTrue(ability != null, "Missing normal ability for Resolve check");
            oldLevel = ability.getResolveLevelToUnlock();
            for (int level : new int[] {5, 6}) {
                ability.resolveLevelToUnlock(level);
                helper.assertTrue(!ability.getResolveUnlockConditionCheck(power).isPositive(),
                        "Ordinary Resolve 4 must not pass the donor's above-cap gate");
                player.getAbilities().instabuild = true;
                helper.assertTrue(ability.getResolveUnlockConditionCheck(power).isPositive(), "Creative must bypass");
                player.getAbilities().instabuild = false;
                skip.set(true);
                helper.assertTrue(ability.getResolveUnlockConditionCheck(power).isPositive(), "Config must bypass");
                skip.set(false);
            }
            power.skipProgression();
            helper.assertTrue(ability.getResolveUnlockConditionCheck(power).isPositive(),
                    "Persisted skipped progression must keep its learned above-cap ability");
        }
        finally {
            if (ability != null) ability.resolveLevelToUnlock(oldLevel);
            skip.set(oldSkip);
            player.discard();
        }
        helper.succeed();
    }
}
