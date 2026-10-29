package rotp.addons.frosttraveler.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.frosttraveler.FrostTravelerAddon;
import rotp.addons.frosttraveler.ability.CreamOperations;
import rotp.addons.frosttraveler.ability.CreamStandActionAbility;
import rotp.addons.frosttraveler.state.CreamStarterService;
import rotp.core.init.ModItems;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

@GameTestHolder("frost_injection")
@PrefixGameTestTemplate(false)
public final class FrostInjectionGameTests {
    private static final short KEY = 71;

    private FrostInjectionGameTests() {}

    @GameTest(template = "empty", templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void flintAndSteelAdmissionAndCallbackCannotSpendInventoryOrStamina(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.prepare(new ItemStack(Items.FLINT_AND_STEEL));
            ItemStack before = fixture.player.getOffhandItem().copy();
            float stamina = fixture.power.getStamina();
            int ammo = CreamStarterService.state(fixture.player).ammo();
            helper.assertTrue(!fixture.injection.checkConditions(fixture.power).isPositive(),
                    "The registered cs_item conditions admitted Flint and Steel");
            helper.assertTrue(!CreamOperations.injectOffhandItem(fixture.player, fixture.target),
                    "The production callback accepted the invalid item");
            helper.assertTrue(ItemStack.isSameItemSameComponents(before, fixture.player.getOffhandItem())
                            && before.getCount() == fixture.player.getOffhandItem().getCount()
                            && fixture.power.getStamina() == stamina
                            && CreamStarterService.state(fixture.player).ammo() == ammo
                            && fixture.target.getRemainingFireTicks() <= 0,
                    "Invalid injection spent inventory, durability, stamina, ammo or applied fire");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void fireChargeUsesRegisteredHeldInjectionAndDonorFiveSecondFire(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.prepare(new ItemStack(Items.FIRE_CHARGE, 2));
            float stamina = fixture.power.getStamina();
            fixture.performHeld();
            helper.assertTrue(fixture.target.getRemainingFireTicks() == 100
                            && fixture.player.getOffhandItem().is(Items.FIRE_CHARGE)
                            && fixture.player.getOffhandItem().getCount() == 1
                            && Math.abs(stamina - fixture.power.getStamina()
                                    - CreamOperations.ITEM_INJECTION_STAMINA) < 0.001F,
                    "Real Fire Charge injection changed its donor fire, item or stamina cost");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "frost_injection", required = true, timeoutTicks = 40)
    public static void remainingDonorTableKeepsRealInjectionAndConditionalArrowAdmission(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            List<ItemStack> items = new ArrayList<>();
            for (Item item : new Item[] { Items.CHORUS_FRUIT, Items.ENDER_EYE, Items.SNOWBALL,
                    Items.SNOW, Items.SNOW_BLOCK, Items.ICE, Items.PACKED_ICE, Items.BLUE_ICE,
                    Items.BLAZE_POWDER, Items.BLAZE_ROD, Items.LAVA_BUCKET, Items.GLOWSTONE_DUST,
                    Items.SPECTRAL_ARROW, Items.GLOWSTONE, Items.MILK_BUCKET, Items.APPLE,
                    Items.ENCHANTED_GOLDEN_APPLE }) {
                items.add(new ItemStack(item));
            }
            ItemStack potion = new ItemStack(Items.POTION);
            potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS));
            items.add(potion);
            for (ItemStack item : items) {
                fixture.prepare(item);
                float stamina = fixture.power.getStamina();
                fixture.performHeld();
                helper.assertTrue(fixture.player.getOffhandItem().isEmpty()
                                && Math.abs(stamina - fixture.power.getStamina()
                                        - CreamOperations.ITEM_INJECTION_STAMINA) < 0.001F,
                        "A retained donor item failed production injection: " + item.getItem());
            }
            fixture.prepare(new ItemStack(ModItems.STAND_ARROW.get()));
            helper.assertTrue(fixture.injection.checkConditions(fixture.power).isPositive(),
                    "Conditional Stand Arrow admission was lost");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", templateNamespace = "frost_injection", required = true, timeoutTicks = 20)
    public static void injectionFixtureStandsInItsStructuresChunk(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        base = new BlockPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(base.getX())), base.getY(),
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(base.getZ())));
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                BlockPos origin = base.offset(dx, 0, dz);
                Placement placement = placement(origin);
                Vec3 cow = placement.user().add(placement.targetOffset());
                ChunkPos chunk = new ChunkPos(origin);
                helper.assertTrue(new ChunkPos(BlockPos.containing(placement.user())).equals(chunk)
                                && new ChunkPos(BlockPos.containing(cow)).equals(chunk),
                        "A structure at " + origin.toShortString() + " puts the injection user at "
                                + placement.user() + " or its target at " + cow + " outside its chunk " + chunk);
                Vec3 ahead = Vec3.directionFromRotation(0.0F, placement.yaw()).scale(2.0D);
                helper.assertTrue(ahead.distanceTo(placement.targetOffset()) < 1.0E-4D,
                        "The injection target is not two blocks ahead of its user: " + placement.targetOffset()
                                + " at a yaw of " + placement.yaw());
            }
        }
        helper.succeed();
    }

    private record Placement(Vec3 user, float yaw, Vec3 targetOffset) {}

    // The framework forces and waits for only the chunk of the 1x1x1 structure (StructureUtils.prepareTestStructure ->
    // forceLoadChunks); a cow in a neighbouring chunk whose entities were not visible yet was missed by cs_item's aim
    // ("Registered injection conditions rejected a valid donor item" under load). The user stands in the structure's
    // column and faces the side of its chunk with room for the cow two blocks ahead.
    private static Placement placement(BlockPos origin) {
        boolean south = SectionPos.sectionRelative(origin.getZ()) <= 13;
        return new Placement(new Vec3(origin.getX() + 0.5D, origin.getY() + 2.0D, origin.getZ() + 0.5D),
                south ? 0.0F : 180.0F, new Vec3(0.0D, 0.0D, south ? 2.0D : -2.0D));
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Placement placement;
        private final FakePlayer player;
        private final Cow target;
        private final StandPower power;
        private final CreamStandActionAbility injection;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FrostInjector"));
            target = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(target != null, "Could not create the live injection target");
            placement = placement(helper.absolutePos(BlockPos.ZERO));
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setPos(placement.user());
            player.setYRot(placement.yaw());
            player.setXRot(20);
            target.setNoAi(true);
            target.setNoGravity(true);
            // in the test's own space from the start, not added at the world origin and moved by prepare()
            target.setPos(player.position().add(placement.targetOffset()));
            helper.assertTrue(target.position().distanceToSqr(player.position()) < 9.0D,
                    "The injection target was not placed in the test's own space before it was added: "
                            + target.position());
            helper.assertTrue(helper.getLevel().addFreshEntity(player)
                            && helper.getLevel().addFreshEntity(target),
                    "Could not register actual injection fixtures");
            assertTargetVisible();
            power = PowerClass.STAND.attachGet(player);
            power.setStand(FrostTravelerAddon.CREAM_STARTER.get());
            power.setResolveLevel(4);
            player.setItemInHand(InteractionHand.MAIN_HAND, CreamStarterService.createOwnedSprayer(player));
            helper.assertTrue(FrostTravelerAddon.CREAM_STARTER.get().summon(player, power)
                            && FrostTravelerAddon.controller(player) != null,
                    "The registered Frost controller was not really summoned");
            helper.assertTrue(power.getAbility(FrostTravelerAddon.CS_ITEM_NAME) instanceof CreamStandActionAbility,
                    "The real moveset did not expose cs_item as a Stand action");
            injection = (CreamStandActionAbility) power.getAbility(FrostTravelerAddon.CS_ITEM_NAME);
            FrostTravelerAddon.controller(player).summonLockTicks = 0;
        }

        private void prepare(ItemStack offhand) {
            target.setPos(player.position().add(placement.targetOffset()));
            assertTargetVisible();
            target.setDeltaMovement(Vec3.ZERO);
            target.clearFire();
            target.removeAllEffects();
            player.setItemInHand(InteractionHand.OFF_HAND, offhand);
            player.getCooldowns().removeCooldown(FrostTravelerAddon.CREAM_STARTER_ITEM.get());
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(!power.isStaminaInfinite()
                            && power.getStamina() >= CreamOperations.ITEM_INJECTION_STAMINA,
                    "The cost control lacks finite usable stamina");
        }

        // a world query (cs_item's aim) sees only entities of chunks whose entities are visible
        private void assertTargetVisible() {
            helper.assertTrue(helper.getLevel().getEntity(target.getId()) == target,
                    "Fixture: the injection target at " + target.position() + " stands in a chunk whose entities"
                            + " are not visible yet");
        }

        // the registered key press holds cs_item's Stand action until its 15-tick charge fires
        private void performHeld() {
            helper.assertTrue(injection.checkConditions(power).isPositive(),
                    "Registered injection conditions rejected a valid donor item");
            StandEntity stand = FrostTravelerAddon.controller(player);
            HeldInputEntry held = AbilityInput.keyPress(KEY, injection, player, null, InputMethod.HOLD, 0,
                    BufferingState.clickOnly(), injection.getAbilityId());
            try {
                EntityActionInstance action = stand.getCurStandAction();
                helper.assertTrue(held != null && action != null && held.action == action,
                        "Registered injection input did not start its Stand action");
                for (int tick = 0; tick < 40 && stand.getCurStandAction() == action; tick++) {
                    stand.getStandActionComponent().tick();
                }
                helper.assertTrue(stand.getCurStandAction() == null && action.isOver(),
                        "The held cs_item Stand action did not fire and end");
            } finally {
                AbilityInput.keyRelease(KEY, player);
            }
        }

        @Override
        public void close() {
            FrostTravelerAddon.CREAM_STARTER.get().forceUnsummon(player, power);
            player.discard();
            target.discard();
        }
    }
}
