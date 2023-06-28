package main.BetterAbstraction.CheapActions;

import main.BetterAbstraction.BetterAction;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import util.XMLWriter;

import java.util.Collection;

public class CheapMove extends BetterAction {

    private final int direction;
    public CheapMove(Unit a_unit, int direction) {
        super(a_unit);
        this.direction = direction;
    }

    @Override
    public boolean completed(GameState pgs) {
        return false;
    }

    @Override
    public void toxml(XMLWriter w) {

    }

    @Override
    public UnitAction execute(GameState gs, ResourceUsage ru) {
        return move_if_possible(direction, gs);
    }
}
