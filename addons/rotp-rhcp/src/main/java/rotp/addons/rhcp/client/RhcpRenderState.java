package rotp.addons.rhcp.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.powersystem.entityaction.ActionPhase;

public final class RhcpRenderState extends StandEntityRenderState {
    public int potency;
    public boolean inConductor;
    public String actionName = "";
    public ActionPhase actionPhase;
    public float actionPhaseRatio;
    public float actionPhaseTick;
    public boolean mirrorAction;
    public boolean attackAlternate;
    public boolean comboAlternate;
    public boolean floating = true;
    public boolean moving;
    public boolean userCrouching;
    public float walkSpeedFactor = 1.0F;
    public float idleLoopTick;
}
