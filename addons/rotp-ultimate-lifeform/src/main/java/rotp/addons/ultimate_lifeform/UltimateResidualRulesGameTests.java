package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformPowerType;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

/**
 * The last 1.16 rules of the Ultimate Lifeform restored in wave 28 (lane F4):
 * the tentacle's kept attachment, the barrages' hand swings, what the evasion
 * takes, where the teleport sound is, presses after a scripted move, the hit
 * cancels at the head of Player.hurt and the flight's "hands".
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateResidualRulesGameTests {
    private UltimateResidualRulesGameTests() {}

    /*
     * 1.16 OwnerBoundProjectileEntity.getEntityAttachedTo kept the entity it
     * had found, so a tentacle attached to a mob that died stayed where the
     * body had been until its lifespan (42 + 20) ran out.
     */
    @GameTest(template = "empty", timeoutTicks = 90)
    public static void tentacleStaysWhereItsDeadTargetWas(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateResidualTentacle",
                "b2d1f3a5-1c2e-4d3f-9a4b-6c7d8e9f0a01", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        face(player, 0.0F, 0.0F);
        Cow cow = cow(helper, new Vec3(2.5D, 2.0D, 5.5D));
        UltimateLifeformCombat.perform(player, holder.data,
                UltimateLifeformSpec.TENTACLES, 0);
        List<UltimateProjectileEntity> tentacles = tentacles(helper, player);
        List<UltimateProjectileEntity> attached = new ArrayList<>();
        helper.runAfterDelay(14, () -> {
            tentacles.stream()
                    .filter(tentacle -> tentacle.getAttachedEntity() == cow)
                    .forEach(attached::add);
            helper.assertTrue(!attached.isEmpty(),
                    "Premise: no tentacle attached to the cow in front");
            cow.kill();
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(cow.isRemoved(),
                    "Premise: the dead cow is still in the level");
            Vec3 body = new Vec3(cow.getX(), cow.getY(0.5D), cow.getZ());
            for (UltimateProjectileEntity tentacle : attached) {
                helper.assertTrue(!tentacle.isRemoved()
                                && tentacle.position().distanceTo(body) < 1.0E-3D,
                        "1.16 kept a tentacle where its dead target's body"
                                + " was until its lifespan ended; removed="
                                + tentacle.isRemoved() + " at "
                                + tentacle.position() + " body at " + body);
            }
        });
        helper.runAfterDelay(68, () -> {
            helper.assertTrue(attached.stream().allMatch(Entity::isRemoved),
                    "1.16 removed the tentacle once its 62 ticks were over");
            tentacles.forEach(Entity::discard);
            holder.close((short) 32_400);
            helper.succeed();
        });
    }

    /*
     * 1.16 UltimateLifeformBarrage.onHoldTickClientEffect, run by every client
     * holding the Ultimate or the Hamon Barrage (tickClient calls the same
     * helper there): every 2nd held tick swinging = false and a swing, the off
     * hand at 2, the main hand at 4, so a running swing starts over.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void barragesSwingTheHandsInTurnEveryOtherTick(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateResidualSwings",
                "b2d1f3a5-1c2e-4d3f-9a4b-6c7d8e9f0a02", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        List<String> seen = new ArrayList<>();
        for (int ticksHeld = 1; ticksHeld <= 8; ticksHeld++) {
            // one tick into the swing of the tick before
            player.swingTime = 1;
            UltimateLifeformCombat.swingBarrageHands(player, ticksHeld);
            seen.add(ticksHeld + ":" + (player.swinging
                    ? player.swingingArm + "@" + player.swingTime
                    : "none"));
        }
        List<String> expected = List.of(
                "1:none", "2:OFF_HAND@-1", "3:OFF_HAND@1", "4:MAIN_HAND@-1",
                "5:MAIN_HAND@1", "6:OFF_HAND@-1", "7:OFF_HAND@1",
                "8:MAIN_HAND@-1");
        helper.assertTrue(seen.equals(expected),
                "1.16 swung the barrage hands in turn every 2nd held tick,"
                        + " starting each swing over; expected " + expected
                        + " got " + seen);
        holder.close((short) 32_401);
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformUltimateAgility.onUserAttacked (:62) let only
     * explosions through: a living attacker's hit that bypasses
     * invulnerability was evaded by Evasion and Ultimate Agility as any other.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void evasionTakesAHitThatBypassesInvulnerability(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateResidualEvasion",
                "b2d1f3a5-1c2e-4d3f-9a4b-6c7d8e9f0a03", GameType.SURVIVAL,
                true);
        ServerPlayer player = holder.player;
        Cow attacker = cow(helper, new Vec3(2.5D, 2.0D, 3.5D));
        DamageSource bypassing = DamageUtil.make(helper.getLevel(),
                DamageTypes.GENERIC_KILL, attacker, attacker);
        helper.assertTrue(bypassing.is(DamageTypeTags.BYPASSES_INVULNERABILITY),
                "Premise: generic_kill does not bypass invulnerability");
        for (UltimateLifeformSpec move : List.of(UltimateLifeformSpec.EVASION,
                UltimateLifeformSpec.ULTIMATE_AGILITY)) {
            holder.data.beginHeld(player, move);
            float health = player.getHealth();
            boolean hurt = player.hurt(bypassing, 2.0F);
            helper.assertTrue(!hurt && player.getHealth() == health,
                    "1.16 " + move.id + " evaded a living attacker's hit that"
                            + " bypasses invulnerability (only explosions got"
                            + " through); hurt=" + hurt + " health " + health
                            + " -> " + player.getHealth());
            holder.data.releaseHeld(player);
        }
        attacker.discard();
        holder.close((short) 32_402);
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformEnderTeleport.perform (:55-64) played the sound
     * with Player.playSound on the user's client, which the server had not
     * moved yet: the user heard it from where it stood, not at the arrival.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void endermanTeleportSoundsWhereTheUserStood(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateResidualTeleport",
                "b2d1f3a5-1c2e-4d3f-9a4b-6c7d8e9f0a04", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        face(player, 0.0F, 0.0F);
        holder.aimNothing();
        Vec3 start = player.position();
        UltimateLifeformCombat.perform(player, holder.data,
                UltimateLifeformSpec.ENDERMAN_TELEPORT, 0);
        helper.assertTrue(player.position().distanceTo(start) > 1.0D,
                "Premise: the teleport did not move the user");
        List<Vec3> heardAt = holder.notified.stream()
                .filter(heard -> heard.sound() == SoundEvents.ENDERMAN_TELEPORT)
                .map(Notified::at)
                .toList();
        helper.assertTrue(heardAt.size() == 1
                        && heardAt.get(0).distanceTo(start) < 1.0E-6D,
                "1.16 played the teleport sound for the user where it stood"
                        + " before the teleport; heard at " + heardAt
                        + ", stood at " + start + ", now at "
                        + player.position());
        holder.close((short) 32_403);
        helper.succeed();
    }

    /*
     * 1.16 UltimateLifeformEnderTeleport.calcBlinkPos (:67-76) with no mouse
     * target: JojoModUtil.rayTrace(user, 24, a living entity but the own
     * Stand), the first entity anywhere on the ray by its own box (inflate 0,
     * through blocks), else the block met by its outline. Its own batch: the
     * 24-block entity search would reach the other plots' mobs.
     */
    @GameTest(template = "empty", timeoutTicks = 20,
            batch = "ulf_teleport_ray")
    public static void endermanTeleportRayIsThe116OutlineRayWithNoMargin(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateResidualBlinkRay",
                "b2d1f3a5-1c2e-4d3f-9a4b-6c7d8e9f0a08", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        holder.aimNothing();
        Vec3 start = player.position();
        for (int z = 4; z <= 8; z++) {
            helper.setBlock(new BlockPos(2, 1, z), Blocks.GRASS_BLOCK);
        }
        helper.setBlock(new BlockPos(2, 2, 5), Blocks.POPPY);
        helper.setBlock(new BlockPos(2, 3, 7), Blocks.STONE);
        Vec3 aboveWall = Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(2, 4, 7)));

        // a cow 0.15 beside the level look, inside the old 0.3 margin
        face(player, 0.0F, 0.0F);
        Cow beside = cow(helper, new Vec3(
                2.5D + 0.45D + 0.15D, 2.5D, 5.0D));
        Vec3 landed = blink(holder, start);
        beside.discard();
        helper.assertTrue(landed.distanceTo(aboveWall) < 1.0E-6D,
                "1.16 met no mob 0.15 beside the look (boxes not grown)"
                        + " and blinked onto the wall it met, to "
                        + aboveWall + "; landed at " + landed
                        + " (the cow stood at " + beside.position() + ")");

        // a poppy (an outline, no collision shape) on the look; its outline
        // is shifted by the position's random flower offset
        BlockPos poppy = helper.absolutePos(new BlockPos(2, 2, 5));
        helper.assertBlockPresent(Blocks.POPPY, new BlockPos(2, 2, 5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                helper.getLevel().getBlockState(poppy)
                        .getShape(helper.getLevel(), poppy).bounds()
                        .getCenter().add(Vec3.atLowerCornerOf(poppy)));
        player.xRotO = player.getXRot();
        player.yRotO = player.getYRot();
        player.setYHeadRot(player.getYRot());
        player.yHeadRotO = player.getYRot();
        Vec3 abovePoppy = Vec3.atBottomCenterOf(poppy.above());
        String look = "look " + player.getLookAngle() + " from "
                + player.getEyePosition();
        landed = blink(holder, start);
        helper.assertTrue(landed.distanceTo(abovePoppy) < 1.0E-6D,
                "1.16 met the poppy by its outline and blinked above it, to "
                        + abovePoppy + "; landed at " + landed + " (" + look
                        + ")");

        // a cow on the look behind the wall
        face(player, 0.0F, 0.0F);
        Cow behind = cow(helper, new Vec3(2.5D, 2.5D, 9.0D));
        landed = blink(holder, start);
        helper.assertTrue(landed.distanceTo(behind.position()) < 1.0E-6D,
                "1.16 took the first mob anywhere on the 24-block ray,"
                        + " through blocks, and blinked to it at "
                        + behind.position() + "; landed at " + landed);
        behind.discard();
        holder.close((short) 32_408);
        helper.succeed();
    }

    private static Vec3 blink(Holder holder, Vec3 from) {
        holder.player.teleportTo(from.x, from.y, from.z);
        UltimateLifeformCombat.perform(holder.player, holder.data,
                UltimateLifeformSpec.ENDERMAN_TELEPORT, 0);
        Vec3 landed = holder.player.position();
        holder.player.teleportTo(from.x, from.y, from.z);
        return landed;
    }

    /*
     * 1.16 had no recovery after a scripted move: once the heavy punch's
     * ContinuousActionInstance ended, the next press was taken at once. The
     * port's 2-tick RECOVERY gives way to a press, so one at the end of the
     * punch starts at once, neither refused nor held in the input buffer.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aPressAtTheEndOfAScriptedMoveStartsAtOnce(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateResidualRecovery",
                "b2d1f3a5-1c2e-4d3f-9a4b-6c7d8e9f0a05", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        holder.aimNothing();
        helper.assertTrue(holder.tryClick(UltimateLifeformSpec.HEAVY_PUNCH,
                        (short) 32_404),
                "Premise: the heavy punch press was refused: "
                        + holder.messages);
        boolean[] pressed = new boolean[1];
        boolean[] accepted = new boolean[1];
        helper.onEachTick(() -> {
            EntityActionInstance action =
                    LivingComponentAction.getCurEntityAction(player);
            if (!pressed[0] && action != null && !action.isOver()
                    && action.ability instanceof UltimateLifeformAbility punch
                    && punch.spec() == UltimateLifeformSpec.HEAVY_PUNCH
                    && action.getPhase() == ActionPhase.RECOVERY) {
                pressed[0] = true;
                accepted[0] = holder.tryClick(
                        UltimateLifeformSpec.HORN_ATTACK, (short) 32_405);
            }
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(pressed[0],
                    "Premise: the heavy punch never reached its recovery");
            helper.assertTrue(accepted[0]
                            && holder.data.isAbilityOnCooldown(
                                    UltimateLifeformSpec.HORN_ATTACK.id),
                    "1.16 took a press at the end of a scripted move at once;"
                            + " the Horn Attack pressed in the recovery was"
                            + " held back or refused: accepted=" + accepted[0]
                            + " cooldown=" + holder.data.getAbilityCooldownTicks(
                                    UltimateLifeformSpec.HORN_ATTACK.id)
                            + " " + holder.messages);
            holder.close((short) 32_405);
            helper.succeed();
        });
    }

    /*
     * 1.16 core GameplayEventHandler.cancelLivingAttack ran on LivingAttackEvent
     * at the head of PlayerEntity.hurt: the Blade Dash instance cancelled every
     * hit before PlayerEntity.hurt dropped a shoulder parrot, and the kick's
     * cancelled only melee hits (DamageUtil.isMeleeAttack: the living attacker
     * is the direct entity), so a hit made through another entity got through.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bladeDashAndKickCancelHitsAtTheHeadOfPlayerHurt(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateResidualDashKick",
                "b2d1f3a5-1c2e-4d3f-9a4b-6c7d8e9f0a06", GameType.SURVIVAL,
                true);
        ServerPlayer player = holder.player;
        Cow attacker = cow(helper, new Vec3(2.5D, 2.0D, 3.5D));
        Cow sender = cow(helper, new Vec3(4.5D, 2.0D, 3.5D));
        player.setOnGround(true);
        CompoundTag parrot = new CompoundTag();
        parrot.putString("id", "minecraft:parrot");
        helper.assertTrue(player.setEntityOnShoulder(parrot),
                "Premise: the parrot did not sit on the shoulder");
        // PlayerEntity.hurt drops a shoulder entity that sat for 20 ticks
        helper.runAfterDelay(25, () -> {
            holder.data.beginHeld(player,
                    UltimateLifeformSpec.BLADE_DASH_ATTACK);
            float health = player.getHealth();
            boolean hurt = player.hurt(
                    player.damageSources().mobAttack(attacker), 2.0F);
            helper.assertTrue(!hurt && player.getHealth() == health
                            && !player.getShoulderEntityLeft().isEmpty(),
                    "1.16 cancelled a hit during the Blade Dash at the head"
                            + " of PlayerEntity.hurt, before it dropped the"
                            + " shoulder parrot; hurt=" + hurt + " parrot="
                            + player.getShoulderEntityLeft());
            holder.data.releaseHeld(player);
            holder.data.beginHeld(player,
                    UltimateLifeformSpec.ULTIMATE_HAMON_KICK);
            boolean melee = UltimateLifeformGameEvents.cancelHeldMoveHit(
                    player, player.damageSources().mobAttack(attacker));
            boolean throughAnother = UltimateLifeformGameEvents
                    .cancelHeldMoveHit(player, DamageUtil.make(
                            helper.getLevel(), DamageTypes.MOB_ATTACK,
                            attacker, sender));
            helper.assertTrue(melee && !throughAnother,
                    "1.16 cancelled the kick's melee hits at the head of"
                            + " PlayerEntity.hurt and nothing whose living"
                            + " attacker was not the direct entity; melee="
                            + melee + " through another=" + throughAnother);
            attacker.discard();
            sender.discard();
            holder.close((short) 32_406);
            helper.succeed();
        });
    }

    /*
     * 1.16 UltimateLifeformTameAnimal.checkHeldItems (:69-74) said "hands" also
     * while the Caelus flight was on without an elytra worn (the port's flight
     * lease stands for 1.16's base value 1, D-rotp-ultimate-lifeform-02); another mod's flight modifier did not.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hypnotizeSaysHandsWhileTheFlightIsOnWithoutAnElytra(
            GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateResidualHypnosis",
                "b2d1f3a5-1c2e-4d3f-9a4b-6c7d8e9f0a07", GameType.SURVIVAL,
                false);
        ServerPlayer player = holder.player;
        holder.data.addHamonCharge(1_000);
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(wolf != null, "Premise: no wolf");
        wolf.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(2, 2, 4))));
        wolf.setNoAi(true);
        helper.getLevel().addFreshEntity(wolf);
        holder.aim(wolf);
        Ability ability = holder.power.getAbility(
                UltimateLifeformSpec.HYPNOTIZE.id);
        helper.assertTrue(ability != null, "Premise: no Hypnotize");
        ConditionCheck free = ability.checkConditions(holder.power);
        helper.assertTrue(free.isPositive(),
                "Premise: Hypnotize at an untamed wolf was refused: "
                        + free.getWarning());
        // another mod's flight modifier on the Caelus attribute is not the Ultimate Lifeform's glide
        net.minecraft.world.entity.ai.attributes.AttributeInstance caelus = player.getAttribute(
                com.illusivesoulworks.caelus.api.CaelusApi.getInstance().getFallFlyingAttribute());
        net.minecraft.resources.ResourceLocation foreignId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("othermod", "flight");
        caelus.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(foreignId, 1.0D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        ConditionCheck foreign = ability.checkConditions(holder.power);
        caelus.removeModifier(foreignId);
        UltimateLifeformPowerType.enableFlight(player);
        ConditionCheck flying = ability.checkConditions(holder.power);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        ConditionCheck elytra = ability.checkConditions(holder.power);
        UltimateLifeformPowerType.disableFlight(player);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        helper.assertTrue(!flying.isPositive() && isWarning(flying, "hands"),
                "1.16 said \"hands\" for Hypnotize while the flight was on"
                        + " without an elytra; got " + (flying.isPositive()
                                ? "admitted" : String.valueOf(flying.getWarning())));
        helper.assertTrue(elytra.isPositive(),
                "1.16 admitted Hypnotize with the flight on and an elytra"
                        + " worn; got " + elytra.getWarning());
        helper.assertTrue(foreign.isPositive(),
                "1.16 read the Caelus base value, so another mod's flight modifier did not make Hypnotize say"
                        + " \"hands\"; got " + (foreign.isPositive() ? "admitted" : String.valueOf(foreign.getWarning())));
        wolf.discard();
        holder.close((short) 32_407);
        helper.succeed();
    }

    /*
     * The add-on stone masks were head ArmorItems on ModArmorMaterials.STONE_MASK (enchantability 0, durability 33) in
     * 1.16, so they took what the core's Stone Mask takes at the table and at the anvil: the head armor set, Unbreaking
     * and Mending (durability) and Thorns. Here they are listed in the enchantable tags as the core's masks are, not in
     * #minecraft:head_armor (which makes them trimmable), and carry the material's durability.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void addonStoneMasksTakeTheEnchantmentsOfTheCoreStoneMask(GameTestHelper helper) {
        net.minecraft.core.Registry<net.minecraft.world.item.enchantment.Enchantment> registry = helper.getLevel()
                .registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        ItemStack core = new ItemStack(rotp.core.init.ModItems.STONE_MASK.get());
        List<String> wrong = new ArrayList<>();
        for (net.minecraft.world.item.Item mask : List.of(UltimateLifeformAddon.STONE_MASK_UL_NOSTONE.get(),
                UltimateLifeformAddon.STONE_MASK_UL_WITH_STONE.get())) {
            ItemStack stack = new ItemStack(mask);
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mask).toString();
            registry.holders().forEach(holder -> {
                if (stack.supportsEnchantment(holder) != core.supportsEnchantment(holder)) {
                    wrong.add(id + " anvil " + holder.key().location() + " " + stack.supportsEnchantment(holder));
                }
                if (stack.isPrimaryItemFor(holder) != core.isPrimaryItemFor(holder)) {
                    wrong.add(id + " table " + holder.key().location() + " " + stack.isPrimaryItemFor(holder));
                }
            });
            if (stack.getMaxDamage() != rotp.core.init.ModArmorMaterials.STONE_MASK_DURABILITY
                    || stack.getMaxDamage() != core.getMaxDamage()) {
                wrong.add(id + " durability " + stack.getMaxDamage() + ", 1.16 " + core.getMaxDamage());
            }
            if (stack.getEnchantmentValue() != core.getEnchantmentValue() || !stack.isEnchantable()) {
                wrong.add(id + " enchantability " + stack.getEnchantmentValue() + " enchantable " + stack.isEnchantable());
            }
            if (!stack.is(net.minecraft.tags.ItemTags.HEAD_ARMOR_ENCHANTABLE)
                    || !stack.is(net.minecraft.tags.ItemTags.EQUIPPABLE_ENCHANTABLE)
                    || !stack.is(net.minecraft.tags.ItemTags.DURABILITY_ENCHANTABLE)
                    || stack.is(net.minecraft.tags.ItemTags.HEAD_ARMOR)
                    || stack.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR)) {
                wrong.add(id + " tags head_armor_enchantable=" + stack.is(net.minecraft.tags.ItemTags.HEAD_ARMOR_ENCHANTABLE)
                        + " equippable=" + stack.is(net.minecraft.tags.ItemTags.EQUIPPABLE_ENCHANTABLE)
                        + " durability=" + stack.is(net.minecraft.tags.ItemTags.DURABILITY_ENCHANTABLE)
                        + " head_armor=" + stack.is(net.minecraft.tags.ItemTags.HEAD_ARMOR)
                        + " trimmable=" + stack.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR));
            }
        }
        helper.assertTrue(core.getMaxDamage() == 33 && core.supportsEnchantment(registry.getHolderOrThrow(
                        net.minecraft.world.item.enchantment.Enchantments.PROTECTION)),
                "Premise: the core Stone Mask is not the 33 durability head armor of the table: " + core.getMaxDamage());
        helper.assertTrue(wrong.isEmpty(),
                "The add-on stone masks take other enchantments than the core Stone Mask (1.16 head ArmorItems on the"
                        + " same material): " + wrong);
        helper.succeed();
    }

    private static boolean isWarning(ConditionCheck check, String postfix) {
        return check.getWarning() != null
                && check.getWarning().getContents()
                        instanceof TranslatableContents contents
                && ("jojo.message.action_condition." + postfix)
                        .equals(contents.getKey());
    }

    private static List<UltimateProjectileEntity> tentacles(
            GameTestHelper helper, ServerPlayer owner) {
        return helper.getLevel().getEntitiesOfClass(
                UltimateProjectileEntity.class,
                owner.getBoundingBox().inflate(16.0D),
                tentacle -> tentacle.isTentacle()
                        && tentacle.getOwner() == owner);
    }

    private static void face(ServerPlayer player, float yRot, float xRot) {
        player.setYRot(yRot);
        player.yRotO = yRot;
        player.setYHeadRot(yRot);
        player.yHeadRotO = yRot;
        player.setYBodyRot(yRot);
        player.yBodyRotO = yRot;
        player.setXRot(xRot);
        player.xRotO = xRot;
    }

    private static Cow cow(GameTestHelper helper, Vec3 relative) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        if (cow == null) {
            throw new IllegalStateException("Cow fixture failed");
        }
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(
                BlockPos.ZERO)).add(
                        relative.x - 0.5D, relative.y, relative.z - 0.5D);
        cow.setPos(position);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0D);
        cow.setHealth(1_000.0F);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static Holder holder(GameTestHelper helper, String name,
            String uuid, GameType gameType, boolean hurtable) {
        List<Component> messages = new ArrayList<>();
        List<Notified> notified = new ArrayList<>();
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name)) {
            @Override
            public void displayClientMessage(
                    Component message, boolean overlay) {
                if (overlay) {
                    messages.add(message);
                }
            }

            // reach Player.hurt the way a real player does
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return !hurtable && super.isInvulnerableTo(source);
            }

            // a real player's connection moves it at once; FakePlayer's
            // ignores the teleport
            @Override
            public void teleportTo(double x, double y, double z) {
                absMoveTo(x, y, z);
            }
        };
        // the sounds sent to this player alone, with where the server had the player
        UserSoundRecorder.attach(player, heard -> notified.add(new Notified(heard.sound(), heard.at())));
        if (hurtable) {
            try {
                java.lang.reflect.Field spawnInvulnerable = ServerPlayer.class
                        .getDeclaredField("spawnInvulnerableTime");
                spawnInvulnerable.setAccessible(true);
                spawnInvulnerable.setInt(player, 0);
            }
            catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "Premise: no ServerPlayer.spawnInvulnerableTime",
                        exception);
            }
        }
        player.setPos(Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setGameMode(gameType);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException(
                    "Residual rules test player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException(
                    "Residual rules test power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        return new Holder(player, power, data, messages, notified);
    }

    private record Notified(SoundEvent sound, Vec3 at) {}

    private record Holder(
            ServerPlayer player,
            PlayerPower power,
            UltimateLifeformData data,
            List<Component> messages,
            List<Notified> notified) {
        // whether the click went through at once (not refused, not
        // waiting in the input buffer)
        boolean tryClick(UltimateLifeformSpec spec, short key) {
            Ability ability = power.getAbility(spec.id);
            if (ability == null) {
                throw new IllegalStateException("No " + spec.id);
            }
            BufferingState buffering = BufferingState.clickCanBuffer();
            FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
            try {
                Object held = AbilityInput.keyPress(key, ability, player,
                        extra, InputMethod.CLICK, 0.0F, buffering,
                        ability.getAbilityId());
                return !buffering.isBuffered()
                        && (held != null || buffering.isActionSuccess);
            }
            finally {
                extra.release();
                AbilityInput.keyRelease(key, player);
            }
        }

        void aim(Entity target) {
            player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    target.getBoundingBox().getCenter());
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            player.setYBodyRot(player.getYRot());
            player.yBodyRotO = player.getYRot();
            LivingComponentAction.getComponent(player)
                    .entityAim.setTarget(new ActionTarget(target));
        }

        void aimNothing() {
            LivingComponentAction.getComponent(player)
                    .entityAim.setTarget(ActionTarget.EMPTY);
        }

        void close(short key) {
            AbilityInput.keyRelease(key, player);
            power.setPowerType(null);
            player.discard();
        }
    }
}
