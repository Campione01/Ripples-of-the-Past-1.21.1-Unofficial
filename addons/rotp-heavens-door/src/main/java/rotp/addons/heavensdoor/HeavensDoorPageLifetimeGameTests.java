package rotp.addons.heavensdoor;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.item.PageItem;
import rotp.addons.heavensdoor.state.BookState;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.addons.heavensdoor.state.PageOwnerSavedData;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavensDoorPageLifetimeGameTests {
    private HeavensDoorPageLifetimeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tornPagesSurviveCloseTimeoutAndRangeThenReturn(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "hd-pages-return");
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        owner.setPos(target.position().add(-2.0D, 0.0D, 0.0D));
        float health = target.getMaxHealth();
        double gravity = target.getAttributeValue(Attributes.GRAVITY);
        tear(helper, owner, target);
        ItemStack page = takePage(helper, owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, page);
        BookState state = HeavensDoorAddon.bookState(target);
        assertTorn(helper, target, page, health, gravity);
        helper.assertTrue(BookStateService.clear(target), "Explicit close must close the book");
        helper.assertTrue(!state.active(), "Explicit close kept the book active");
        assertTorn(helper, target, page, health, gravity);
        ((PageItem) page.getItem()).inventoryTick(page, owner.level(), owner, 0, true);
        helper.assertTrue(!page.isEmpty(), "Closed book deleted its page item");

        helper.assertTrue(BookStateService.markFromHit(HeavensDoorAddon.controller(owner), target),
                "Recast after close failed");
        // Shorten only the temporary fixture deadline; the production tick expires it.
        state.mark(owner.getUUID(), target.getUUID(), target.level().dimension().location(),
                target.level().getGameTime() + 1L, state.bookAmplifier());
        helper.runAfterDelay(3L, () -> {
            try {
                BookStateService.tick(target);
                helper.assertTrue(!state.active(), "Book deadline did not expire");
                assertTorn(helper, target, page, health, gravity);
                helper.assertTrue(BookStateService.markFromHit(HeavensDoorAddon.controller(owner), target),
                        "Recast after timeout failed");
                owner.setPos(target.position().add(30.0D, 0.0D, 0.0D));
                BookStateService.tick(target);
                helper.assertTrue(!state.active(), "Owner range did not close book");
                assertTorn(helper, target, page, health, gravity);
                helper.assertTrue(!BookStateService.returnHeldPage(owner), "Out-of-range return was accepted");
                owner.setPos(target.position().add(-2.0D, 0.0D, 0.0D));

                Cow foreign = helper.spawnWithNoFreeWill(EntityType.COW, 3, 2, 2);
                foreign.setItemInHand(InteractionHand.MAIN_HAND, page.copy());
                helper.assertTrue(!BookStateService.returnHeldPage(foreign), "Foreign holder returned another creator's page");
                foreign.discard();
                helper.assertTrue(BookStateService.returnHeldPage(owner), "Creator could not return page after book closure");
                helper.assertTrue(page.isEmpty(), "Returned item was not consumed");
                assertRestored(helper, target, health, gravity);
                helper.succeed();
            }
            finally {
                target.discard();
                disconnect(owner);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unloadedTargetsKeepLowHealthPagesButReconcileDeathRevocation(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "hd-pages-revoke");
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 2);
        try {
            owner.setPos(target.position().add(-2.0D, 0.0D, 0.0D));
            float health = target.getMaxHealth();
            double gravity = target.getAttributeValue(Attributes.GRAVITY);
            tear(helper, owner, target);
            ItemStack page = takePage(helper, owner);
            owner.setItemInHand(InteractionHand.MAIN_HAND, page);
            CompoundTag unloaded = unload(helper, target);
            long pageEpoch = HeavensDoorAddon.bookState(target).pageOwnerEpoch();
            owner.setGameMode(GameType.CREATIVE);
            owner.setHealth(owner.getMaxHealth() * 0.25F);
            BookStateService.tickPageOwner(owner);
            helper.assertTrue(PageOwnerSavedData.get(owner.getServer()).matches(owner.getUUID(), pageEpoch),
                    "Creative low health incorrectly revoked pages");
            owner.setGameMode(GameType.SURVIVAL);
            BookStateService.tickPageOwner(owner);
            reloadRevocations(helper);
            target = reload(helper, unloaded);
            assertTorn(helper, target, page, health, gravity);
            ((PageItem) page.getItem()).inventoryTick(page, owner.level(), owner, 0, true);
            helper.assertTrue(!page.isEmpty(), "Low health incorrectly revoked a page after target reload");

            owner.setHealth(owner.getMaxHealth());
            tear(helper, owner, target);
            page = takePage(helper, owner);
            owner.setItemInHand(InteractionHand.MAIN_HAND, page);
            unloaded = unload(helper, target);
            ItemStack oldPage = page.copy();
            owner.hurt(owner.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!owner.isAlive(), "Fixture owner did not actually die");
            reloadRevocations(helper);
            target = reload(helper, unloaded);
            assertRestored(helper, target, health, gravity);
            ((PageItem) oldPage.getItem()).inventoryTick(oldPage, target.level(), target, 0, true);
            helper.assertTrue(oldPage.isEmpty(), "Death-revoked page survived target reload");
            helper.succeed();
        }
        finally {
            target.discard();
            disconnect(owner);
        }
    }

    private static void tear(GameTestHelper helper, ServerPlayer owner, Cow target) {
        HeavensDoorStandEntity stand = HeavensDoorAddon.controller(owner);
        helper.assertTrue(stand != null, "Missing summoned Heaven's Door");
        BookStateService.markFromHit(stand, target);
        helper.assertTrue(BookStateService.tearPage(owner, target), "Production page extraction failed");
    }

    private static ItemStack takePage(GameTestHelper helper, ServerPlayer owner) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            if (PageItem.data(owner.getInventory().getItem(slot)) != null) {
                return owner.getInventory().removeItem(slot, 1);
            }
        }
        helper.fail("Production tear did not produce a real page item");
        return ItemStack.EMPTY;
    }

    private static void assertTorn(GameTestHelper helper, Cow target, ItemStack page, float health, double gravity) {
        helper.assertTrue(HeavensDoorAddon.bookState(target).pageCount() == 1
                        && BookStateService.pageMatches(target, PageItem.data(page)),
                "Book lifetime invalidated the durable page binding");
        helper.assertTrue(Math.abs(target.getMaxHealth() - (health - 0.5F)) < 0.0001F
                        && Math.abs(target.getAttributeValue(Attributes.GRAVITY) - (gravity - 0.001D)) < 0.00001D
                        && target.hasEffect(HeavensDoorAddon.FORGOT_SECOND_HAND),
                "Book lifetime removed page modifiers or forgotten action");
    }

    private static void assertRestored(GameTestHelper helper, Cow target, float health, double gravity) {
        helper.assertTrue(HeavensDoorAddon.bookState(target).pageCount() == 0
                        && Math.abs(target.getMaxHealth() - health) < 0.0001F
                        && Math.abs(target.getAttributeValue(Attributes.GRAVITY) - gravity) < 0.00001D
                        && !target.hasEffect(HeavensDoorAddon.FORGOT_SECOND_HAND),
                "Page revocation/return did not restore target: pages="
                        + HeavensDoorAddon.bookState(target).pageCount()
                        + ", maxHealth=" + target.getMaxHealth() + "/" + health
                        + ", gravity=" + target.getAttributeValue(Attributes.GRAVITY) + "/" + gravity
                        + ", forgot=" + target.hasEffect(HeavensDoorAddon.FORGOT_SECOND_HAND));
    }

    private static CompoundTag unload(GameTestHelper helper, Cow target) {
        CompoundTag saved = new CompoundTag();
        helper.assertTrue(target.save(saved), "Target NBT save failed");
        target.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(helper.getLevel().getEntity(target.getUUID()) == null, "Fixture target is still loaded");
        return saved;
    }

    private static Cow reload(GameTestHelper helper, CompoundTag saved) {
        Entity entity = EntityType.loadEntityRecursive(saved, helper.getLevel(), loaded -> loaded);
        helper.assertTrue(entity instanceof Cow, "Target NBT reload failed");
        Cow target = (Cow) entity;
        helper.assertTrue(helper.getLevel().addFreshEntity(target), "Reloaded target could not join level");
        BookStateService.tick(target);
        return target;
    }

    private static void reloadRevocations(GameTestHelper helper) {
        PageOwnerSavedData data = PageOwnerSavedData.get(helper.getLevel().getServer());
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.getLevel().getServer().overworld().getDataStorage().set("rotp_hd_page_owners",
                PageOwnerSavedData.load(saved, helper.getLevel().registryAccess()));
    }

    private static ServerPlayer owner(GameTestHelper helper, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        StandPower power = StandPower.get(player);
        helper.assertTrue(power != null, "Fixture owner has no Stand power");
        power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
        helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(player, power),
                "Fixture owner could not summon Heaven's Door");
        power.getSummonedStandEntity().summonLockTicks = 0;
        return player;
    }

    private static void disconnect(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        if (power != null && power.hasPower()) {
            power.getPowerType().forceUnsummon(player, power);
        }
        player.connection.disconnect(Component.literal("Heaven's Door page lifetime GameTest complete"));
    }
}
