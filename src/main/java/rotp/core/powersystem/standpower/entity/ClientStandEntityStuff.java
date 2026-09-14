package rotp.core.powersystem.standpower.entity;

import java.util.ArrayList;
import java.util.List;

import rotp.core.client.entityanim.barrage.BarrageSwings;
import rotp.core.client.sound.barrage.BarrageHitSoundHandler;

import net.minecraft.world.phys.Vec3;

public class ClientStandEntityStuff {
	public final BarrageSwings barrageSwings = new BarrageSwings();
	public final BarrageHitSoundHandler barrageHitSounds = new BarrageHitSoundHandler();

	public List<Vec3> tiltVecQueue = new ArrayList<>();
	public float lastMotionTiltTick = -1;

	public float getAlpha(StandEntity standEntity, float partialTick) {
		return (float) standEntity.rangeEfficiency * standEntity.modelAlpha.lerp(partialTick);
	}
}
