package rotp.core.subsystems.target;

import net.minecraft.world.level.Level;

/**
 * Aim target that is constantly synchronized from player client to server
 */
public class ActionTargetAim {
	protected ActionTarget target = ActionTarget.EMPTY;
	protected ActionTarget targetPrev = ActionTarget.EMPTY;
	protected boolean clientEntityMissing;

	public void setTarget(ActionTarget target) {
		setTarget(target, false);
	}

	public void setTarget(ActionTarget target, boolean clientEntityMissing) {
		this.target = target;
		this.clientEntityMissing = clientEntityMissing;
	}

	/**
	 * Server: stores an aim the client sent. An entity the server cannot find is stored as no aim, and remembered as
	 * such until the next aim: 1.16 PowerBaseImpl.checkTarget emptied the whole target of a press on it, whatever an
	 * action would have put in its place.
	 */
	public void setTargetFromClient(ActionTarget received, Level level) {
		boolean entity = received.getType() == ActionTarget.TargetType.ENTITY;
		ActionTarget resolved = received.resolveEntityId(level);
		setTarget(resolved, entity && resolved.getType() == ActionTarget.TargetType.EMPTY);
	}

	/** Whether the stored aim is an entity the client aimed at and the server cannot find. */
	public boolean isClientEntityMissing() {
		return clientEntityMissing;
	}

	public boolean checkDirty() {
		boolean dirty = !target.equals(targetPrev);
		this.targetPrev = target;
		return dirty;
	}

	public ActionTarget getTarget() {
		return target;
	}

}
