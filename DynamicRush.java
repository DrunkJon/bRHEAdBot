package main;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.LightRush;
import ai.abstraction.RangedRush;
import ai.abstraction.WorkerRush;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import rts.GameState;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitTypeTable;

import java.util.List;

public class DynamicRush extends AbstractionLayerAI {
    private UnitTypeTable utt;
    WorkerRush workerRush;
    RangedRush rangedRush;
    LightRush lightRush;
    public DynamicRush(UnitTypeTable utt, PathFinding a_pf) {
        super(a_pf);
        this.utt = utt;
        this.workerRush = new WorkerRush(utt, a_pf);
        this.rangedRush = new RangedRush(utt, a_pf);
        this.lightRush = new LightRush(utt, a_pf);
    }

    @Override
    public PlayerAction getAction(int player, GameState gs) throws Exception {
        BetterGameState bgs = new BetterGameState(gs);
        List<Unit> my_barracks = bgs.getPlayerUnits(player, utt.getUnitType("Barracks"));
        List<Unit> ur_barracks = bgs.getPlayerUnits(1-player, utt.getUnitType("Barracks"));
        if (my_barracks.size() == 0) {
            return workerRush.getAction(player, gs);
        } else if (ur_barracks.size() ==0) {
            return rangedRush.getAction(player, gs);
        } else {
            return lightRush.getAction(player, gs);
        }
    }

    @Override
    public AI clone() {
        return null;
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        return null;
    }
}
