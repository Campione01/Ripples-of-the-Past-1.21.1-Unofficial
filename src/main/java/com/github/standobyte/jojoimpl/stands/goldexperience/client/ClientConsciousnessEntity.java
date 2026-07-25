package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

public class ClientConsciousnessEntity extends RemotePlayer {
	private final Minecraft minecraft;
	private final UUID realPlayerId;
	private final int hurtTimerStart;
	private boolean cancelPlayerRender = true;

	public ClientConsciousnessEntity(Minecraft minecraft, ClientLevel level, LocalPlayer player) {
		super(level, player.getGameProfile());
		this.minecraft = minecraft;
		this.realPlayerId = player.getUUID();
		this.hurtTimerStart = player.hurtTime;
		this.noPhysics = false;
		copyPosition(player);
		setUUID(UUID.randomUUID());
		copyAttribute(player, Attributes.MOVEMENT_SPEED, 2.0D);
		copyAttribute(player, Attributes.ATTACK_SPEED, 2.0D);
		copyAttribute(player, NeoForgeMod.SWIM_SPEED, 2.0D);
		setHealth(player.getHealth());
	}

	private void copyAttribute(LocalPlayer player, net.minecraft.core.Holder<Attribute> attribute, double multiplier) {
		AttributeInstance ownAttribute = getAttribute(attribute);
		AttributeInstance playerAttribute = player.getAttribute(attribute);
		if (ownAttribute != null && playerAttribute != null) {
			ownAttribute.setBaseValue(playerAttribute.getValue() * multiplier);
		}
	}

	@Nullable
	@Override
	protected PlayerInfo getPlayerInfo() {
		return minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(realPlayerId) : super.getPlayerInfo();
	}

	@Override
	public boolean isModelPartShown(PlayerModelPart part) {
		return minecraft.player != null && minecraft.player.isModelPartShown(part);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isControlledByLocalInstance() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		tickPlayerRenderCancel();
	}

	public void travelWithInput(float leftImpulse, float forwardImpulse, boolean jumping, boolean shiftKeyDown) {
		if (!isAlive()) {
			return;
		}
		this.xxa = leftImpulse;
		this.zza = forwardImpulse;
		setShiftKeyDown(shiftKeyDown);
		setSprinting(forwardImpulse >= 0.8F && minecraft.options.keySprint.isDown());
		if (jumping && onGround()) {
			jumpFromGround();
		}
		setJumping(jumping);
		float sneakSlowdown = shiftKeyDown ? 0.3F : 1.0F;
		travel(new Vec3(leftImpulse * sneakSlowdown, 0.0D, forwardImpulse * sneakSlowdown));
		calculateEntityAnimation(false);
	}

	private void tickPlayerRenderCancel() {
		LocalPlayer player = minecraft.player;
		if (player != null && (player.isOnFire() || tickCount > 2 && player.hurtTime >= hurtTimerStart)) {
			cancelPlayerRender = false;
		}
	}

	public boolean shouldCancelPlayerRender() {
		return cancelPlayerRender;
	}
}
