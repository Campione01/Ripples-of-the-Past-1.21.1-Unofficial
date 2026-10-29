package rotp.addons.sexpistols;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;

@GameTestHolder(SexPistolsIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RevolverProtectionGameTests {
    private RevolverProtectionGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_revolver_protection", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void realShotsRespectArmorAndProtectionWithoutLosingStandDamage(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> realShotsRespectArmorAndProtectionWithoutLosingStandDamageBody(h));
    }

    private static void realShotsRespectArmorAndProtectionWithoutLosingStandDamageBody(GameTestHelper helper) {
        for (ShotMode mode : ShotMode.values()) {
            float naked = shootCow(helper, mode, ArmorMode.NONE);
            float armor = shootCow(helper, mode, ArmorMode.DIAMOND);
            float protection = shootCow(helper, mode, ArmorMode.PROTECTION_IV);
            helper.assertTrue(naked > armor && armor > protection && protection > 0.0F,
                    mode + " failed armor/Protection ordering: " + naked + ", " + armor + ", " + protection);
            helper.assertTrue(Math.abs(protection - armor * 0.84F) < 0.02F,
                    mode + " Protection IV did not reduce equal-armor damage by 16%: " + armor + " -> " + protection);
        }
        shootStand(helper);
        helper.succeed();
    }

    private static float shootCow(GameTestHelper helper, ShotMode mode, ArmorMode armorMode) {
        ServerPlayer owner = player(helper, "prot-" + mode.ordinal() + armorMode.ordinal(), 2);
        Cow target = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(target != null, "Damage target was not created");
        try {
            prepareOwner(helper, owner, mode);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            target.setHealth(200.0F);
            target.setPos(inOriginChunk(helper, 2, 12, 6));
            if (armorMode != ArmorMode.NONE) {
                ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
                if (armorMode == ArmorMode.PROTECTION_IV) {
                    chest.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(Enchantments.PROTECTION), 4);
                }
                target.setItemSlot(EquipmentSlot.CHEST, chest);
            }
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Damage target did not join");
            target.tick();
            double armor = target.getAttributeValue(Attributes.ARMOR);
            double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            helper.assertTrue(armor == (armorMode == ArmorMode.NONE ? 0.0D : 8.0D)
                            && toughness == (armorMode == ArmorMode.NONE ? 0.0D : 2.0D),
                    "Equipment fixture did not apply equal diamond armor: " + armor + ", " + toughness);
            RevolverBulletEntity bullet = fire(helper, owner);
            // A kick consumes its first movement segment; compare armor at the next trace.
            placeOnFlight(target, bullet, mode == ShotMode.KICKED ? 12.0D : 4.0D);
            assertInOriginChunk(helper, target.position(), "The target");
            if (mode == ShotMode.KICKED) {
                SexPistolsCoordinator coordinator = SexPistolsAttachments.coordinator(owner);
                SexPistolsEntity member = coordinator.selectedMember(helper.getLevel());
                helper.assertTrue(member != null, "No production member available for the kick");
                Vec3 kick = bullet.position().add(bullet.getDeltaMovement().normalize().scale(1.25D))
                        .subtract(0.0D, member.getBbHeight() * 0.5D, 0.0D);
                helper.assertTrue(coordinator.toggleSelectedMove(helper.getLevel(), owner, kick),
                        "Production scouting command failed");
                member.setPos(kick);
                helper.assertTrue(member.canRedirectBullet(), "Scouting member cannot kick");
            }
            float before = target.getHealth();
            tickUntilHit(helper, bullet, target, before);
            helper.assertTrue(bullet.standDamage() == (mode == ShotMode.KICKED)
                            && bullet.specialShotType() == ShotType.NONE,
                    "Shot did not follow the requested ordinary/kicked path");
            assertSource(helper, target.getLastDamageSource(), mode != ShotMode.NO_STAND);
            float damage = before - target.getHealth();
            System.out.printf(Locale.ROOT,
                    "REVOLVER_PROTECTION mode=%s armor=%s armorValue=%.1f toughness=%.1f damage=%.6f source=%s kicked=%s%n",
                    mode, armorMode, armor, toughness, damage,
                    target.getLastDamageSource().getMsgId(), bullet.standDamage());
            return damage;
        }
        finally {
            target.discard();
            disconnect(owner);
        }
    }

    private static void shootStand(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "prot-stand-shot", 2);
        ServerPlayer victim = player(helper, "prot-stand-user", 8);
        try {
            prepareOwner(helper, owner, ShotMode.STAND_USER);
            StandPower targetPower = StandPower.get(victim);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
            helper.assertTrue(type != null && targetPower != null, "Core Stand target fixture unavailable");
            targetPower.setStand(type);
            helper.assertTrue(type.summon(victim, targetPower), "Target Stand did not summon");
            StandEntity target = targetPower.getSummonedStandEntity();
            helper.assertTrue(target != null, "Summoned Stand target missing");
            target.summonLockTicks = 0;
            target.setNoGravity(true);
            target.setYRot(0.0F);
            RevolverBulletEntity bullet = fire(helper, owner);
            placeOnFlight(target, bullet, 4.0D);
            assertInOriginChunk(helper, target.position(), "The Stand target");
            float before = target.getHealth();
            tickUntilHit(helper, bullet, target, before);
            assertSource(helper, target.getLastDamageSource(), true);
            helper.assertTrue(!bullet.standDamage(), "Stand-target control was redirected unexpectedly");
            System.out.printf(Locale.ROOT, "REVOLVER_PROTECTION target=STAND damage=%.6f source=%s%n",
                    before - target.getHealth(), target.getLastDamageSource().getMsgId());
        }
        finally {
            disconnect(victim);
            disconnect(owner);
        }
    }

    private static void prepareOwner(GameTestHelper helper, ServerPlayer owner, ShotMode mode) {
        StandPower power = StandPower.get(owner);
        helper.assertTrue(power != null, "Mock player lacks Stand attachment");
        if (mode != ShotMode.NO_STAND) {
            power.setStand(SexPistolsStands.SEX_PISTOLS.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
        }
        if (mode == ShotMode.KICKED) {
            helper.assertTrue(SexPistolsStands.SEX_PISTOLS.get().summon(owner, power),
                    "Sex Pistols could not summon for actual redirection");
            SexPistolsCoordinator coordinator = SexPistolsAttachments.coordinator(owner);
            coordinator.cycleTargetMode(owner, false);
            coordinator.cycleTargetMode(owner, false);
        }
        helper.assertTrue(power.hasPower() == (mode != ShotMode.NO_STAND), "Wrong shooter power state");
    }

    private static RevolverBulletEntity fire(GameTestHelper helper, ServerPlayer owner) {
        ItemStack gun = new ItemStack(SexPistolsItems.REVOLVER.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, gun);
        owner.getInventory().add(new ItemStack(SexPistolsItems.REVOLVER_AMMO.get()));
        helper.assertTrue(RevolverItem.openForStandReload(helper.getLevel(), owner, gun, true, true)
                        && RevolverItem.loadSelectedChamberIfEmpty(owner, gun)
                        && RevolverItem.closeReloadMode(helper.getLevel(), owner, gun),
                "Production revolver loading failed");
        owner.setYRot(0.0F);
        owner.setYHeadRot(0.0F);
        owner.setXRot(0.0F);
        ((RevolverItem) gun.getItem()).use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.isUsingItem() && RevolverItem.state(gun).aiming(),
                "Production revolver did not enter aiming state");
        owner.releaseUsingItem();
        List<RevolverBulletEntity> bullets = helper.getLevel().getEntitiesOfClass(
                RevolverBulletEntity.class, new AABB(owner.blockPosition()).inflate(8.0D),
                bullet -> bullet.getOwner() == owner);
        helper.assertTrue(bullets.size() == 1, "Actual release created " + bullets.size() + " bullets");
        return bullets.getFirst();
    }

    private static void placeOnFlight(LivingEntity target, RevolverBulletEntity bullet, double distance) {
        Vec3 center = bullet.position().add(bullet.getDeltaMovement().normalize().scale(distance));
        target.setPos(center.subtract(0.0D, target.getBbHeight() * 0.5D, 0.0D));
    }

    // The flight is at most about 15 blocks long, so it is laid out from the
    // west edge of the origin chunk, the only one that ticks entities.
    private static Vec3 inOriginChunk(GameTestHelper helper, double x, double y, double z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Vec3 height = helper.absoluteVec(new Vec3(0.0D, y, 0.0D));
        return new Vec3((origin.getX() & ~15) + x, height.y, (origin.getZ() & ~15) + z);
    }

    private static void assertInOriginChunk(GameTestHelper helper, Vec3 position, String what) {
        ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkPos actual = new ChunkPos(BlockPos.containing(position));
        helper.assertTrue(origin.equals(actual), what + " stands in chunk " + actual + ", outside the chunk "
                + origin + " of the test origin, which is the one that ticks entities.");
    }

    private static void tickUntilHit(GameTestHelper helper, RevolverBulletEntity bullet,
            LivingEntity target, float before) {
        for (int tick = 0; tick < 4 && bullet.isAlive() && target.getHealth() >= before; tick++) {
            bullet.tick();
        }
        helper.assertTrue(target.getHealth() < before && target.getLastDamageSource() != null,
                "Production projectile ticks did not damage the target: alive=" + bullet.isAlive()
                        + ", redirected=" + bullet.standDamage() + ", bullet=" + bullet.position()
                        + ", target=" + target.position());
        helper.assertTrue(target.getLastDamageSource().getDirectEntity() == bullet,
                "Damage did not come from the actually fired bullet");
    }

    private static void assertSource(GameTestHelper helper, DamageSource source, boolean standOwner) {
        helper.assertTrue(!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                        && !source.is(DamageTypeTags.BYPASSES_ARMOR),
                "Revolver bypassed enchantments or physical armor");
        helper.assertTrue(source.is(DamageTypeTags.IS_PROJECTILE), "Revolver lost projectile identity");
        if (standOwner) {
            helper.assertTrue(source.is(RevolverBulletEntity.STAND_REVOLVER_BULLET_DAMAGE)
                            && DamageUtil.canHurtStands(source)
                            && source.is(DamageTypeTags.BYPASSES_COOLDOWN),
                    "Revolver lost its Stand identity or invulnerability-tick behavior");
        }
        else {
            helper.assertTrue(!source.is(RevolverBulletEntity.STAND_REVOLVER_BULLET_DAMAGE),
                    "Non-Stand shooter gained the Stand-specific damage source");
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name, int x) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setPos(inOriginChunk(helper, x, 12, 1));
        assertInOriginChunk(helper, player.position(), "The fixture player " + name);
        return player;
    }

    private static void disconnect(ServerPlayer player) {
        RevolverBulletEntity.discardOwnedBy(player.serverLevel(), player.getUUID());
        StandPower power = StandPower.get(player);
        if (power != null && power.hasPower()) {
            power.getPowerType().forceUnsummon(player, power);
        }
        player.connection.disconnect(Component.literal("Revolver Protection GameTest complete"));
    }

    private enum ShotMode { STAND_USER, NO_STAND, KICKED }
    private enum ArmorMode { NONE, DIAMOND, PROTECTION_IV }
}
