package rotp.addons.diverdown.ability;

import java.util.ArrayList;
import java.util.List;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

// 1.16 DiverDownStealItem was a StandAction (InitStands :77): the user
// performs it, with Diver Down summoned or not, and it never summons it.
public final class StealItemAbility extends EntityActionAbility {
    public static final int RESOLVE_LEVEL = 2;
    public static final int WINDUP_TICKS = 15;
    public static final int COOLDOWN_TICKS = 300;
    public static final float STAMINA_COST = 300.0F;
    private static final int[] NEXT_LEVEL_XP_THRESHOLDS = {0, 10, 70, 150, 250};

    public StealItemAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, StealAction::new);
        resolveLevelToUnlock(RESOLVE_LEVEL);
        cooldown(COOLDOWN_TICKS);
        // 1.16 holdToFire(15, false): the 15 ticks are a charge.
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 StandAction.getPerformer: the stun that counts is the
        // summoned Stand's, else the user's (checkPerformerStun).
        setIgnoresPerformerStun();
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    // 1.16 set the cooldown when the hold fired, so a cancelled hold
    // leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity owner = context.getUser();
        if (owner == null) {
            return ConditionCheck.NEGATIVE;
        }
        DiverDownStandEntity stand = DiverDownAddon.activeStand(owner);
        // 1.16 DiverDownStealItem.checkSpecificConditions :73-99 refused
        // nothing aimed and a block without a block entity silently; the owner
        // kept the port's dd_target_no_inv for those too (2026-10-19)
        if (!hasInventoryTarget(owner, stand, this)) {
            return ConditionCheck.createNegative("dd_target_no_inv");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 tickHeldAction checked the ANY target requirement and the press
    // conditions against the crosshair on every held tick: a charge whose
    // crosshair left an inventory stopped unfired.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkSpecificConditions(context);
    }

    // 1.16 :80-100: any player or merchant under the crosshair, whatever its
    // team, game mode, invulnerability or the PvP rule; an inventory on the
    // side-less item handler of a block entity
    private static boolean hasInventoryTarget(
            LivingEntity owner, DiverDownStandEntity stand, StealItemAbility steal) {
        HitResult aim = DiverDownTargeting.crosshair(owner, stand, steal);
        if (aim instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            return target instanceof Player
                    || target instanceof AbstractVillager
                            && (!(target instanceof Villager villager)
                                    || villager.getVillagerData().getProfession()
                                            != VillagerProfession.NITWIT);
        }
        // 1.16 :75 hasTileEntity: an inventory without a block entity,
        // a composter's, is refused silently
        if (!(aim instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || owner.level().getBlockEntity(hit.getBlockPos()) == null) {
            return false;
        }
        return owner.level().getCapability(
                Capabilities.ItemHandler.BLOCK,
                hit.getBlockPos(), null) != null;
    }

    public static final class StealAction
            extends EntityActionInstance {
        public StealAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdToFire(15, false): letting go before the charge
        // completes cancels the steal.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            StandPower power =
                    owner != null ? StandPower.get(owner) : null;
            DiverDownStandEntity stand = DiverDownAddon.activeStand(owner);
            if (!DiverDownAuthority.validUser(
                            owner,
                            power,
                            DiverDownAddon.STEAL_ITEM_NAME)
                    || !(ability instanceof StealItemAbility steal)
                    || !hasInventoryTarget(owner, stand, steal)) {
                startRecovery();
                return;
            }
            steal.setCooldownOnUse(power);
            // 1.16 StandAction.onPerform paid the cost before the steal and
            // ignored the result: a short bar only drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
            // 1.16 DiverDownStealItem.perform stole only for a player user.
            if (!(owner instanceof Player player)) {
                return;
            }

            ItemStack stolen = ItemStack.EMPTY;
            HitResult aim = DiverDownTargeting.crosshair(owner, stand, steal);
            Entity target = aim instanceof EntityHitResult entityHit
                    ? entityHit.getEntity()
                    : null;
            if (target instanceof Player playerTarget) {
                stolen = stealFromPlayer(playerTarget);
            } else if (target instanceof AbstractVillager merchant) {
                stolen = stealFromMerchant(merchant);
            } else if (aim instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK) {
                stolen = stealFromBlock(level, hit);
            }

            if (!stolen.isEmpty()) {
                give(level, player, stolen);
            }
        }

        // 1.16 stealItem, stealItemFromCap and stealItemFromArrayList: a free
        // slot takes the stack, else an ItemEntity spawns at the user's feet
        // with no pickup delay, so the user picks it up again at once and the
        // stack merges into what the inventory can hold
        private static void give(
                ServerLevel level, Player player, ItemStack stolen) {
            if (player.getInventory().getFreeSlot() != -1) {
                player.getInventory().add(stolen);
                return;
            }
            level.addFreshEntity(new ItemEntity(
                    level,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    stolen));
        }

        // 1.16 stealItem :171-182: the first slot with items, 64 at most
        private static ItemStack stealFromPlayer(Player target) {
            for (int slot = 0;
                    slot < target.getInventory()
                            .getContainerSize();
                    slot++) {
                ItemStack stack =
                        target.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    return target.getInventory().removeItem(
                            slot, Math.min(stack.getCount(), 64));
                }
            }
            return ItemStack.EMPTY;
        }

        // 1.16 perform :119-166
        private static ItemStack stealFromMerchant(
                AbstractVillager merchant) {
            List<ItemStack> stock = new ArrayList<>();
            MerchantOffers offers = merchant.getOffers();
            // :130-139 every offer with uses left gives its result and
            // spends one use
            for (MerchantOffer offer : offers) {
                if (offer.isOutOfStock()) {
                    continue;
                }
                stock.add(offer.getResult().copy());
                offer.increaseUses();
            }
            if (merchant instanceof Villager villager) {
                // :142 the xp falls by a fifth of what its level took (1.16
                // threw for a level above 5)
                int level = villager.getVillagerData().getLevel();
                villager.setVillagerXp(Math.max(
                        villager.getVillagerXp()
                                - NEXT_LEVEL_XP_THRESHOLDS[Math.min(
                                        level, NEXT_LEVEL_XP_THRESHOLDS.length) - 1] / 5,
                        0));
                // :143-145 at the floor of its level it drops one level
                if (villager.getVillagerXp()
                        <= VillagerData.getMinXpPerLevel(level)) {
                    villager.setVillagerData(
                            villager.getVillagerData().setLevel(level - 1));
                }
                // :146-149 more offers than two per level lose the first two
                if (offers.size() > villager.getVillagerData().getLevel() * 2) {
                    offers.remove(0);
                    offers.remove(0);
                }
            } else if (merchant instanceof WanderingTrader
                    && allOutOfStock(offers)) {
                // :150-161 a trader with nothing left to sell becomes a
                // nitwit and gives its own things as well
                stock.add(new ItemStack(Items.MILK_BUCKET));
                stock.add(new ItemStack(Items.BLUE_CARPET));
                stock.add(new ItemStack(Items.LEAD));
                stock.add(PotionContents.createItemStack(
                        Items.POTION, Potions.LONG_INVISIBILITY));
                Villager nitwit =
                        new Villager(EntityType.VILLAGER, merchant.level());
                nitwit.setVillagerData(nitwit.getVillagerData()
                        .setProfession(VillagerProfession.NITWIT));
                nitwit.moveTo(merchant.position());
                merchant.discard();
                merchant.level().addFreshEntity(nitwit);
            }
            if (stock.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return stock.get(merchant.getRandom().nextInt(stock.size()));
        }

        // 1.16 needsToRestock :210-217
        private static boolean allOutOfStock(MerchantOffers offers) {
            int spent = 0;
            for (MerchantOffer offer : offers) {
                if (offer.isOutOfStock()) {
                    spent++;
                }
            }
            return offers.size() <= spent;
        }

        // 1.16 perform :112-118: no spawn protection or adventure mode check
        private static ItemStack stealFromBlock(
                ServerLevel level, BlockHitResult hit) {
            BlockState state =
                    level.getBlockState(hit.getBlockPos());
            if (state.isAir()) {
                return ItemStack.EMPTY;
            }
            // 1.16 stealItemFromCap: the side-less handler, the first slot
            // that holds anything, 64 at most; a slot that gives nothing
            // ends the steal
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    hit.getBlockPos(),
                    null);
            if (handler == null) {
                return ItemStack.EMPTY;
            }
            for (int slot = 0;
                    slot < handler.getSlots();
                    slot++) {
                ItemStack held = handler.getStackInSlot(slot);
                if (!held.isEmpty()) {
                    return handler.extractItem(
                            slot, Math.min(held.getCount(), 64), false);
                }
            }
            return ItemStack.EMPTY;
        }
    }
}
