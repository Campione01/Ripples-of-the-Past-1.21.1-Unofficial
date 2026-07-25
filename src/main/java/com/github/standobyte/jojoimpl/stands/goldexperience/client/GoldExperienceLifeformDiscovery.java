package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.util.List;

import com.github.standobyte.jojo.network.c2s.ClGEMetLifeformPacket;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntitySubtype;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeforms;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeformState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GoldExperienceLifeformDiscovery {
    private static final double MAX_DISTANCE_SQR = 144.0D;

    private GoldExperienceLifeformDiscovery() {
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        if (!(mc.hitResult instanceof EntityHitResult hitResult)) {
            return;
        }
        Entity entity = hitResult.getEntity();
        if (entity == mc.player || !entity.isAlive() || entity.distanceToSqr(mc.player) > MAX_DISTANCE_SQR) {
            return;
        }

        List<EntitySubtype<?>> unseenSubtypes = EntitySubtype.getMatchingSubtypes(entity)
                .filter(subtype -> GoldExperienceLifeforms.isValidLifeform(subtype, mc.level))
                .filter(subtype -> !GoldExperienceLifeformState.get(mc.player).hasMetLifeform(subtype.getId().toString()))
                .toList();
        if (unseenSubtypes.isEmpty()) {
            return;
        }

        GoldExperienceLifeformState state = GoldExperienceLifeformState.get(mc.player);
        unseenSubtypes.forEach(subtype -> state.learnLifeformIdFromSync(subtype.getId().toString()));
        PacketDistributor.sendToServer(new ClGEMetLifeformPacket(entity.getId()));
        MetEntityTypeToast.addOrUpdate(mc.getToasts(), entity.getType());
        mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER,
                0.5F, 2.0F, RandomSource.create(), entity.getX(), entity.getY(0.5), entity.getZ()));
    }
}
