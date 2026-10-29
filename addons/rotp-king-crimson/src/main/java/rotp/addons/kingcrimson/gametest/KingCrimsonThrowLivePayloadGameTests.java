package rotp.addons.kingcrimson.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.gametest.KingCrimsonThrowGameTestFixture.Mode;
import rotp.core.customobjects.entity_projectile.MolotovEntity;
import rotp.core.init.ModItems;
import rotp.core.powersystem.entityaction.ActionPhase;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowLivePayloadGameTests {
    private static PayloadSuite active;
    private KingCrimsonThrowLivePayloadGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 1800, batch = "kc_throw_02")
    public static void throwChecksFlintInitiallyAndUsesTheLivePayload(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowLivePayloadGameTests.class);
        helper.assertTrue(active == null, "No overlapping throw payload suite");
        PayloadSuite suite = new PayloadSuite(helper); active = suite;
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
    @AfterBatch(batch = "kc_throw_02")
    public static void cleanupPayload(ServerLevel level) { if (active != null) active.close(); }

    private static final class PayloadSuite extends KingCrimsonThrowGameTestFixture.Suite {
        int kind;
        boolean triple, lateStandArrow;
        ItemStack expectedItem, initialHand, initialFlint, waitingHand;
        boolean firstVerified, recoverySeen, sameActionAtRecovery, extraWaitDebit, comparableWait = true, sampleValid;
        int waitSpawns, recoveryAge, waitSamples, sampleUserAge;
        float sampleStamina;
        String fixedAllowed;
        PayloadSuite(GameTestHelper helper) { super(helper, "D-KC-THROW-02", 28); }
        @Override void prepare(int index) {
            kind = index % 14; triple = index >= 14; lateStandArrow = kind == 6 || kind == 8;
            firstVerified = recoverySeen = sameActionAtRecovery = extraWaitDebit = sampleValid = false;
            comparableWait = true; waitSamples = 0; recoveryAge = -1;
            scene = new KingCrimsonThrowGameTestFixture(helper, "payload/" + (triple ? "triple/" : "single/") + kind, Mode.FINITE, 600, 73);
            KingCrimsonConfig.ALLOWED_PROJECTILES.set("minecraft:snowball," + BuiltInRegistries.ITEM.getKey(ModItems.MOLOTOV.get()));
            ItemStack molotov = namedMolotov("initial-payload", 3);
            scene.hand(kind <= 3 || kind == 10 ? molotov : new ItemStack(Items.ARROW, 3));
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            flint.set(DataComponents.CUSTOM_NAME, Component.literal("retained-flint-safety")); flint.setDamageValue(7);
            scene.offhand(flint);
            expectedItem = molotov.copyWithCount(1);
            if (kind == 2 || kind == 4) scene.offhand(ItemStack.EMPTY);
            if (kind == 5) scene.hand(new ItemStack(ModItems.STAND_ARROW.get()));
            if (kind == 7) scene.hand(new ItemStack(ModItems.STAND_ARROW_BEETLE.get()));
            if (kind == 9) scene.hand(new ItemStack(Items.DIRT, 3));
            if (kind == 10) KingCrimsonConfig.ALLOWED_PROJECTILES.set("");
            if (kind == 12 || kind == 13) {
                fixedAllowed = BuiltInRegistries.ITEM.getKey(ModItems.MOLOTOV.get()).toString();
                KingCrimsonConfig.ALLOWED_PROJECTILES.set(fixedAllowed);
                scene.premise(!KingCrimsonConfig.allowsProjectile(Items.SNOWBALL), "Snowball excluded before Arrow admission");
            }
            if (kind == 13) scene.hand(new ItemStack(Items.SNOWBALL, 2));
            if (!scene.user.getMainHandItem().isEmpty() && !scene.user.getMainHandItem().is(ModItems.MOLOTOV.get())) {
                scene.user.getMainHandItem().set(DataComponents.CUSTOM_NAME, Component.literal("initial-hand-safety"));
            }
            initialHand = scene.user.getMainHandItem().copy(); initialFlint = scene.user.getOffhandItem().copy();
            int initialAmmo = initialHand.getCount();
            boolean refused = kind == 2 || kind == 5 || kind == 7 || kind == 9 || kind == 10 || kind == 13;
            var press = scene.press(triple ? KingCrimsonThrowGameTestFixture.TRIPLE : KingCrimsonThrowGameTestFixture.SINGLE,
                    KingCrimsonThrowGameTestFixture.FIRST);
            checks.check(press.admitted() == !refused && !press.buffered(), scene.id + " initial condition-checked admission/refusal");
            if (refused) {
                checks.check(scene.spawns.isEmpty() && scene.power.getStamina() == 600
                        && scene.power.getAbilityCooldown(scene.ability.name()) == 0
                        && scene.user.getMainHandItem().getCount() == initialAmmo, scene.id + " initial refusal has no side effects");
                checks.check(sameStack(scene.user.getMainHandItem(), initialHand), scene.id + " initial refusal preserves hand item/components/count");
                checks.check(sameStack(scene.user.getOffhandItem(), initialFlint), scene.id + " initial refusal preserves offhand item/components/count/damage");
                done = true; return;
            }
            if (!press.admitted()) { done = true; return; }
            scene.premise(press.action().getPhase() == ActionPhase.WINDUP, "Positive native windup");
            scene.duringWindup = () -> {
                scene.aim(triple ? -52 : 74, -29);
                if (kind == 1 || kind == 3) scene.offhand(ItemStack.EMPTY);
                if (kind == 3) scene.hand(new ItemStack(Items.SNOWBALL, 2));
                if (kind == 4) {
                    ItemStack live = namedMolotov("live-swap-payload", 2);
                    scene.hand(live); expectedItem = live.copyWithCount(1);
                }
                if (kind == 6) scene.hand(new ItemStack(ModItems.STAND_ARROW.get()));
                if (kind == 8) scene.hand(new ItemStack(ModItems.STAND_ARROW_BEETLE.get()));
                if (kind == 11) { scene.hand(new ItemStack(Items.SNOWBALL, 2)); KingCrimsonConfig.ALLOWED_PROJECTILES.set(""); }
                if (kind == 12) scene.hand(new ItemStack(Items.SNOWBALL, 2));
            };
            if (kind == 11 || kind == 12) {
                scene.listen(EntityTickEvent.Pre.class, event -> {
                    if (event.getEntity() == scene.stand && scene.performed && !recoverySeen) {
                        sampleStamina = scene.power.getStamina(); sampleUserAge = scene.user.tickCount; sampleValid = true;
                    }
                });
                scene.listen(EntityTickEvent.Post.class, event -> {
                    if (event.getEntity() != scene.stand || !scene.performed || recoverySeen) return;
                    if (sampleValid) {
                        waitSamples++; comparableWait &= scene.user.tickCount == sampleUserAge;
                        extraWaitDebit |= scene.power.getStamina() < sampleStamina; sampleValid = false;
                    }
                    if (scene.action.getPhase() == ActionPhase.RECOVERY) {
                        recoverySeen = true; sameActionAtRecovery = scene.stand.getCurStandAction() == scene.action;
                        recoveryAge = scene.stand.tickCount;
                    }
                });
            }
        }
        @Override boolean verify() {
            if (!scene.performed) return false;
            boolean noPayload = kind == 11 || kind == 12;
            if (!firstVerified) {
            scene.premise(scene.windupChanged, "Live inventory change followed admission and natural ticks");
            scene.checkPayment(checks, true);
            int expected = noPayload ? 0 : Math.min(scene.preAmmo, triple ? 3 : 1);
            scene.checkSpawns(checks, expected, kind == 3 ? Snowball.class : lateStandArrow ? Arrow.class : MolotovEntity.class);
            checks.check(scene.postAmmo == scene.preAmmo - expected, scene.id + " live count consumed only for actual permitted payload");
            if (noPayload) {
                checks.check(scene.preCooldown > 0 && scene.postCooldown == scene.preCooldown, scene.id + " admission cooldown not moved or reset at perform");
            }
            if (kind == 12) {
                scene.premise(KingCrimsonConfig.ALLOWED_PROJECTILES.get().equals(fixedAllowed)
                        && !KingCrimsonConfig.allowsProjectile(Items.SNOWBALL), "Unchanged config excludes live Snowball throughout the admitted throw");
            }
            if (lateStandArrow) {
                // Owner decision 2026-10-14 (1.16): a Stand Arrow swapped in after the start flies as a plain arrow.
                for (var spawn : scene.spawns) checks.check(spawn.entity().getType() == EntityType.ARROW
                                && spawn.entity() instanceof Arrow arrow
                                && ItemStack.isSameItemSameComponents(arrow.getPickupItem(), new ItemStack(Items.ARROW)),
                        scene.id + " late Stand Arrow is thrown as an ordinary arrow");
            }
            if (!lateStandArrow && !noPayload && kind != 3) {
                for (var spawn : scene.spawns) checks.check(ItemStack.isSameItemSameComponents(spawn.item(), expectedItem),
                        scene.id + " actual Molotov preserves live item components");
            }
            if (kind == 0) {
                ItemStack retained = scene.user.getOffhandItem();
                checks.check(ItemStack.isSameItemSameComponents(retained, initialFlint)
                                && retained.getCount() == initialFlint.getCount() && retained.getDamageValue() == initialFlint.getDamageValue(),
                        scene.id + " retained flint item/components/count/damage unchanged count=" + retained.getCount() + " damage=" + retained.getDamageValue());
            }
            if (kind == 1 || kind == 3 || kind == 4) scene.premise(scene.user.getOffhandItem().isEmpty(), "No perform-time flint");
            firstVerified = true; waitSpawns = scene.spawns.size(); waitingHand = scene.user.getMainHandItem().copy();
            }
            if (noPayload) {
                scene.premise(scene.stand.tickCount - scene.postStandAge <= 40, "Bounded natural same-action RECOVERY deadline");
                if (!recoverySeen) return false;
                checks.check(sameActionAtRecovery && scene.action.getPhase() == ActionPhase.RECOVERY
                                && recoveryAge >= scene.postStandAge,
                        scene.id + " config-excluded payload returns normally into recovery");
                checks.check(comparableWait && !extraWaitDebit && scene.spawns.size() == waitSpawns
                                && sameStack(scene.user.getMainHandItem(), waitingHand),
                        scene.id + " recovery wait has no extra emission/debit/ammo mutation");
                KingCrimsonThrowGameTestFixture.log("NATURAL-RECOVERY " + scene.id + " firstPerformAge=" + scene.postStandAge
                        + " recoveryAge=" + recoveryAge + " waitSamples=" + waitSamples + " sameAction=" + sameActionAtRecovery
                        + " extraDebit=" + extraWaitDebit + " spawns=" + scene.spawns.size());
            }
            return true;
        }
        boolean sameStack(ItemStack current, ItemStack expected) {
            return ItemStack.isSameItemSameComponents(current, expected) && current.getCount() == expected.getCount();
        }
        ItemStack namedMolotov(String name, int count) {
            ItemStack result = new ItemStack(ModItems.MOLOTOV.get(), count);
            result.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return result;
        }
        @Override void release() { if (active == this) active = null; }
    }
}
