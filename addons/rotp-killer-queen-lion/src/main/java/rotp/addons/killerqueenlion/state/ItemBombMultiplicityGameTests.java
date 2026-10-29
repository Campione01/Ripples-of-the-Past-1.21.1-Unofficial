package rotp.addons.killerqueenlion.state;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.killerqueenlion.KillerQueenLionAddon;

@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemBombMultiplicityGameTests {
    private ItemBombMultiplicityGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_addon_api_smoke", timeoutTicks = 20)
    public static void eachMarkedStackProducesItsOwnNativeBlast(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BombProbe"));
        var otherOwner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "OtherBombProbe"));
        Cow carrier = EntityType.COW.create(level);
        Cow outside = EntityType.COW.create(level);
        helper.assertTrue(carrier != null && outside != null, "Missing item carriers");
        owner.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
        AtomicInteger blasts = new AtomicInteger();
        Consumer<ExplosionEvent.Detonate> listener = event -> {
            if (event.getLevel() == level && event.getExplosion().getDirectSourceEntity() == owner) {
                blasts.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ExplosionEvent.Detonate.class, listener);
        try {
            carrier.setNoAi(true);
            carrier.setNoGravity(true);
            carrier.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0D);
            carrier.setHealth(2000.0F);
            carrier.setPos(owner.position().add(0, 0, 3));
            outside.setNoAi(true);
            outside.setNoGravity(true);
            outside.setPos(owner.position().add(20, 0, 0));
            level.addFreshEntity(carrier);
            level.addFreshEntity(outside);
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 4));
            helper.assertTrue(ItemBombService.markOffhandItem(owner), "First public mark failed");
            ItemStack first = owner.getMainHandItem().copy();
            helper.assertTrue(first.getCount() == 1 && owner.getOffhandItem().getCount() == 3,
                    "Marking changed the one-item split");
            carrier.setItemSlot(EquipmentSlot.MAINHAND, first);
            outside.setItemSlot(EquipmentSlot.MAINHAND, first.copy());
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD, 3));
            helper.assertTrue(ItemBombService.markOffhandItem(owner), "Second public mark failed");
            carrier.setItemSlot(EquipmentSlot.OFFHAND, owner.getMainHandItem().copy());
            owner.getInventory().clearContent();
            otherOwner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            otherOwner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT));
            helper.assertTrue(ItemBombService.markOffhandItem(otherOwner), "Control mark failed");
            carrier.setItemSlot(EquipmentSlot.HEAD, otherOwner.getMainHandItem().copy());
            otherOwner.getInventory().clearContent();
            helper.assertTrue(ItemBombService.hasBombsInRange(owner, ItemBombService.LEGACY_RANGE),
                    "The carrier's marked stacks were not discoverable");
            int count = ItemBombService.detonate(owner, ItemBombService.LEGACY_RANGE);
            helper.assertTrue(count == 2 && blasts.get() == 2,
                    "Two marked stacks must produce two native blasts; count=" + count + ", blasts=" + blasts.get());
            helper.assertTrue(carrier.isAlive() && carrier.getMainHandItem().isEmpty()
                            && carrier.getOffhandItem().isEmpty(), "Carrier or consumed-stack control failed");
            helper.assertTrue(ItemBombService.isBomb(carrier.getItemBySlot(EquipmentSlot.HEAD), otherOwner.getUUID()),
                    "Another owner's marked stack was consumed");
            helper.assertTrue(ItemBombService.isBomb(outside.getMainHandItem(), owner.getUUID()),
                    "The out-of-range marked stack was consumed");
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            owner.getInventory().clearContent();
            otherOwner.getInventory().clearContent();
            carrier.discard();
            outside.discard();
        }
    }
}
