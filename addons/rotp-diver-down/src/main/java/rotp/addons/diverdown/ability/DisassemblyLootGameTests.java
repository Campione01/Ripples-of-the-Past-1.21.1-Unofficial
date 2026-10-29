package rotp.addons.diverdown.ability;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.DiverDownDamageTypes;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.DamageUtil;

@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisassemblyLootGameTests {
    private DisassemblyLootGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void disassemblyRollsVictimLootFiveTimesWithLuckAndBones(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> rollsVictimLootFiveTimes(helper, placement));
    }

    private static void rollsVictimLootFiveTimes(GameTestHelper helper, GameTestPlacement placement) {
        var level = helper.getLevel();
        Pig owner = EntityType.PIG.create(level);
        Cow target = EntityType.COW.create(level);
        helper.assertTrue(owner != null && target != null, "Missing caster/victim fixtures");
        Set<UUID> existingItems = new HashSet<>();
        owner.setPos(placement.owner);
        target.setPos(placement.target);
        var area = target.getBoundingBox().inflate(1.0D);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            existingItems.add(item.getUUID());
        }
        try {
            GameTestPlacement.assertTicks(helper, "owner", owner);
            GameTestPlacement.assertTicks(helper, "target", target);
            owner.setNoAi(true);
            owner.setNoGravity(true);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.setPos(placement.target);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
            target.getAttribute(Attributes.ARMOR).setBaseValue(20.0D);
            target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0D);
            target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(128.0D);
            target.setHealth(5.0F);
            target.setAbsorptionAmount(100.0F);
            CompoundTag serialized = new CompoundTag();
            target.saveWithoutId(serialized);
            serialized.putString("DeathLootTable", "rotp_dd:gametest/disassembly");
            target.load(serialized);
            level.addFreshEntity(owner);
            level.addFreshEntity(target);
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(DiverDownAddon.DIVER_DOWN.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(DiverDownAddon.DIVER_DOWN.get().summon(owner, power),
                    "Could not summon the disassembly caster");
            var stand = (DiverDownStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.setEmbeddedTarget(target)
                            && MobDisassembleAbility.qualifies(stand),
                    "Victim did not qualify for disassembly");
            pressDisassembly(helper, power, owner, stand);
            afterDisassembly(helper, stand, 0, () -> {
                helper.assertTrue(target.isAlive() && !stand.isEmbedded(),
                        "The controlled victim died or remained embedded");
                var drops = level.getEntitiesOfClass(ItemEntity.class, area,
                        item -> !existingItems.contains(item.getUUID()));
                int iron = drops.stream().filter(item -> item.getItem().is(Items.IRON_INGOT))
                        .mapToInt(item -> item.getItem().getCount()).sum();
                int bones = drops.stream().filter(item -> item.getItem().is(Items.BONE))
                        .mapToInt(item -> item.getItem().getCount()).sum();
                helper.assertTrue(iron == 30 && bones == 30
                                && drops.stream().noneMatch(item -> item.getItem().is(Items.SADDLE)),
                        "Expected five luck5 rolls retaining bones and excluding saddles; iron="
                                + iron + ", bones=" + bones);
                helper.succeed();
            }, () -> cleanup(level, owner, target, area, existingItems));
        } catch (Throwable error) {
            cleanup(level, owner, target, area, existingItems);
            throw error;
        }
    }

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 60)
    public static void disassemblyExcludesVanillaRavagerSaddles(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> excludesRavagerSaddles(helper, placement));
    }

    private static void excludesRavagerSaddles(GameTestHelper helper, GameTestPlacement placement) {
        var level = helper.getLevel();
        Pig owner = EntityType.PIG.create(level);
        Ravager target = EntityType.RAVAGER.create(level);
        helper.assertTrue(owner != null && target != null, "Missing ravager disassembly fixtures");
        owner.setPos(placement.owner);
        target.setPos(placement.target);
        var area = target.getBoundingBox().inflate(1.0D);
        Set<UUID> existingItems = new HashSet<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            existingItems.add(item.getUUID());
        }
        try {
            GameTestPlacement.assertTicks(helper, "owner", owner);
            GameTestPlacement.assertTicks(helper, "target", target);
            owner.setNoAi(true);
            owner.setNoGravity(true);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.ARMOR).setBaseValue(20.0D);
            target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0D);
            target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(128.0D);
            target.setHealth(5.0F);
            target.setAbsorptionAmount(100.0F);
            level.addFreshEntity(owner);
            level.addFreshEntity(target);
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(DiverDownAddon.DIVER_DOWN.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(DiverDownAddon.DIVER_DOWN.get().summon(owner, power),
                    "Could not summon the ravager disassembly caster");
            var stand = (DiverDownStandEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.setEmbeddedTarget(target)
                            && MobDisassembleAbility.qualifies(stand),
                    "Ravager did not qualify for disassembly");
            var source = DamageUtil.make(level, DiverDownDamageTypes.DISASSEMBLE, stand, owner);
            var params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, target)
                    .withParameter(LootContextParams.ORIGIN, target.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withParameter(LootContextParams.ATTACKING_ENTITY, owner)
                    .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, stand)
                    .withLuck(MobDisassembleAbility.BONUS_LOOT_LUCK)
                    .create(LootContextParamSets.ENTITY);
            var table = level.getServer().reloadableRegistries().getLootTable(target.getLootTable());
            helper.assertTrue(table.getRandomItems(params).stream().anyMatch(stack -> stack.is(Items.SADDLE)),
                    "Vanilla ravager table did not provide the positive saddle control");
            pressDisassembly(helper, power, owner, stand);
            afterDisassembly(helper, stand, 0, () -> {
                helper.assertTrue(target.isAlive() && !stand.isEmbedded(),
                        "Ravager died or did not finish disassembly; ordinary death loot would confound the test");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area,
                                item -> !existingItems.contains(item.getUUID()) && item.getItem().is(Items.SADDLE))
                                .isEmpty(),
                        "Bonus disassembly rolls retained saddles excluded by the donor");
                helper.succeed();
            }, () -> cleanup(level, owner, target, area, existingItems));
        } catch (Throwable error) {
            cleanup(level, owner, target, area, existingItems);
            throw error;
        }
    }

    /**
     * 1.16 reached the disassembly through the heavy key only: the finisher gave the key to the deform dash, which gave
     * it to the disassembly while Diver Down was inside a target at a quarter of its health or less. Presses it so.
     */
    static EntityActionInstance pressDisassembly(GameTestHelper helper, StandPower power, LivingEntity owner,
            DiverDownStandEntity stand) {
        stand.summonLockTicks = 0;
        stand.gradualSummonWeaknessTicks = 0;
        stand.setFinisherMeter(1.0F);
        var heavyKey = power.updateAvailableMoves().getContextVariationContainer(DiverDownAddon.HEAVY_PUNCH);
        helper.assertTrue(heavyKey != null && heavyKey.ability instanceof MobDisassembleAbility,
                "1.16 gave the heavy key to the disassembly at a full finisher meter inside a weak target; it holds "
                        + (heavyKey != null ? heavyKey.ability.name() : null));
        BufferingState buffering = BufferingState.clickOnly();
        heavyKey.ability.onKeyPress(helper.getLevel(), owner, null, InputMethod.CLICK, 0.0F, buffering);
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(buffering.isActionSuccess && action instanceof MobDisassembleAbility.DisassembleAction,
                "The disassembly pressed through the heavy key did not start");
        return action;
    }

    /** Runs {@code then} on the tick Diver Down has left the target, its perform done; {@code cleanup} at the end. */
    static void afterDisassembly(GameTestHelper helper, DiverDownStandEntity stand, int waited, Runnable then,
            Runnable cleanup) {
        helper.runAfterDelay(1, () -> {
            try {
                if (stand.isEmbedded()) {
                    helper.assertTrue(waited < 40, "The disassembly never performed");
                    afterDisassembly(helper, stand, waited + 1, then, cleanup);
                    return;
                }
                then.run();
                cleanup.run();
            } catch (Throwable error) {
                cleanup.run();
                throw error;
            }
        });
    }

    private static void cleanup(ServerLevel level, LivingEntity owner, LivingEntity target, AABB area,
            Set<UUID> existingItems) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
                item -> !existingItems.contains(item.getUUID()))) {
            item.discard();
        }
        DiverDownAuthority.clearOwnedBy(level.getServer(), owner.getUUID());
        var power = PowerClass.STAND.get(owner);
        if (power != null && power.hasPower()) {
            power.getPowerType().forceUnsummon(owner, power);
        }
        target.discard();
        owner.discard();
    }
}
