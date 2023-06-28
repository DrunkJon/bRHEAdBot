package main.BetterAbstraction.CheapActions;

import main.BetterAbstraction.BetterAction;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import rts.units.UnitType;
import util.XMLWriter;

public class CheapBuild extends BetterAction {

    private final int direction;
    private final UnitType type;
    public CheapBuild(Unit a_unit, UnitType type, int direction) {
        super(a_unit);
        this.direction = direction;
        this.type = type;
    }

    @Override
    public boolean completed(GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int[] pos = unit_after_move(direction, pgs);
        Unit u = pgs.getUnitAt(pos[0], pos[1]);
        return u != null;
    }

    @Override
    public void toxml(XMLWriter w) {

    }

    @Override
    public UnitAction execute(GameState gs, ResourceUsage ru) {
        return build_if_possible(direction, gs, type);
    }
}
