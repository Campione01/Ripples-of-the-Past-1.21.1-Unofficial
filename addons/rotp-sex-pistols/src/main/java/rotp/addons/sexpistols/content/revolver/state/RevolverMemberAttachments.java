package rotp.addons.sexpistols.content.revolver.state;

import rotp.addons.sexpistols.registry.SexPistolsDataComponents;
import net.minecraft.world.item.ItemStack;

public final class RevolverMemberAttachments {
    public static final int MEMBER_COUNT = 6;
    public static final int BITS_PER_MEMBER = 3;
    public static final int LEGACY_MEMBER_MASK = 0x3F;
    public static final int ASSIGNMENT_OFFSET = 6;
    public static final int MAX_ENCODED =
            (1 << (ASSIGNMENT_OFFSET
                    + MEMBER_COUNT * BITS_PER_MEMBER)) - 1;

    private static final int ASSIGNMENT_MASK =
            (1 << BITS_PER_MEMBER) - 1;

    private RevolverMemberAttachments() {}

    public static int encoded(ItemStack stack) {
        return stack.getOrDefault(
                        SexPistolsDataComponents
                                .SEX_PISTOLS_ATTACHMENT_MASK.get(),
                        0)
                & MAX_ENCODED;
    }

    public static int attachedMemberMask(ItemStack stack) {
        return encoded(stack) & LEGACY_MEMBER_MASK;
    }

    public static int chamberMemberMask(
            ItemStack stack, int chamber) {
        int safeChamber = Math.floorMod(
                chamber, RevolverState.CHAMBER_COUNT);
        int memberMask = 0;
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            if (chamberForSlot(stack, slot) == safeChamber) {
                memberMask |= 1 << slot;
            }
        }
        return memberMask;
    }

    public static int chamberForSlot(
            ItemStack stack, int slot) {
        int encoded = encoded(stack);
        if (slot < 0
                || slot >= MEMBER_COUNT
                || (encoded & 1 << slot) == 0) {
            return -1;
        }
        int assigned = assignedChamber(encoded, slot);
        if (assigned >= 0) {
            return assigned;
        }

        RevolverState state = stack.getOrDefault(
                SexPistolsDataComponents.REVOLVER_STATE.get(),
                RevolverState.EMPTY);
        int selected = state.selectedChamber();
        if (state.chamber(selected)
                == RevolverState.Chamber.LIVE) {
            return selected;
        }
        return state.firstChamber(
                RevolverState.Chamber.LIVE);
    }

    public static void attach(
            ItemStack stack, int chamber, int slot) {
        if (slot < 0 || slot >= MEMBER_COUNT) {
            return;
        }
        int safeChamber = Math.floorMod(
                chamber, RevolverState.CHAMBER_COUNT);
        int shift = ASSIGNMENT_OFFSET
                + slot * BITS_PER_MEMBER;
        int encoded = encoded(stack);
        encoded = (encoded
                & ~(ASSIGNMENT_MASK << shift))
                | ((safeChamber + 1) << shift)
                | (1 << slot);
        setEncoded(stack, encoded);
    }

    public static int detachChamber(
            ItemStack stack, int chamber) {
        int memberMask = chamberMemberMask(stack, chamber);
        detachMembers(stack, memberMask);
        return memberMask;
    }

    public static void detachMembers(
            ItemStack stack, int memberMask) {
        int encoded = encoded(stack);
        int normalizedMask = memberMask & 0x3F;
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            if ((normalizedMask & 1 << slot) != 0) {
                encoded &= ~(ASSIGNMENT_MASK
                        << (ASSIGNMENT_OFFSET
                                + slot * BITS_PER_MEMBER));
                encoded &= ~(1 << slot);
            }
        }
        setEncoded(stack, encoded);
    }

    public static int clear(ItemStack stack) {
        int memberMask = attachedMemberMask(stack);
        setEncoded(stack, 0);
        return memberMask;
    }

    public static int retainMembers(
            ItemStack stack, int allowedMask) {
        int before = attachedMemberMask(stack);
        detachMembers(stack, before & ~allowedMask);
        return before & allowedMask & 0x3F;
    }

    public static int leastLoadedLiveChamber(
            ItemStack stack, RevolverState state) {
        int bestChamber = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int offset = 0;
                offset < RevolverState.CHAMBER_COUNT;
                offset++) {
            int chamber = Math.floorMod(
                    state.selectedChamber() + offset,
                    RevolverState.CHAMBER_COUNT);
            if (state.chamber(chamber)
                    != RevolverState.Chamber.LIVE) {
                continue;
            }
            int count = Integer.bitCount(
                    chamberMemberMask(stack, chamber));
            if (count < bestCount) {
                bestChamber = chamber;
                bestCount = count;
            }
        }
        return bestChamber;
    }

    private static int assignedChamber(
            int encoded, int slot) {
        if (slot < 0 || slot >= MEMBER_COUNT) {
            return -1;
        }
        int assignment =
                encoded >> (ASSIGNMENT_OFFSET
                                + slot * BITS_PER_MEMBER)
                        & ASSIGNMENT_MASK;
        return assignment >= 1
                && assignment <= RevolverState.CHAMBER_COUNT
                        ? assignment - 1
                        : -1;
    }

    private static void setEncoded(
            ItemStack stack, int encoded) {
        int normalized = encoded & MAX_ENCODED;
        if (normalized == 0) {
            stack.remove(
                    SexPistolsDataComponents
                            .SEX_PISTOLS_ATTACHMENT_MASK.get());
        }
        else {
            stack.set(
                    SexPistolsDataComponents
                            .SEX_PISTOLS_ATTACHMENT_MASK.get(),
                    normalized);
        }
    }
}
