package main.BetterAbstraction.CheapActions;

import main.BetterAbstraction.BetterAction;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import util.XMLWriter;

import java.util.Collection;

public class CheapHarvest extends BetterAction {

    private final int direction;
    public CheapHarvest(Unit a_unit, int direction) {
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

    public int resource_direction(PhysicalGameState pgs) {
        //returns -1 if no resource
        int[] pos;
        for (int dir = 0; dir <= 3; dir++) {
            pos = unit_after_move(dir, pgs);
            Unit maybe_resource = pgs.getUnitAt(pos[0], pos[1]);
            if (maybe_resource != null && maybe_resource.getPlayer() == -1) return dir;
        }
        return -1;
    }

    public int base_direction(PhysicalGameState pgs) {
        //returns -1 if no resource
        int[] pos;
        for (int dir = 0; dir <= 3; dir++) {
            pos = unit_after_move(dir, pgs);
            Unit maybe_base = pgs.getUnitAt(pos[0], pos[1]);
            if (maybe_base != null && maybe_base.getPlayer() == unit.getPlayer() && maybe_base.getType().name.equals("Base")) return dir;
        }
        return -1;
    }

    @Override
    public UnitAction execute(GameState gs, ResourceUsage ru) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Collection<Unit> around_units = pgs.getUnitsAround(unit.getX(), unit.getY(), 1);
        if (unit.getResources() == 0) {
            int dir = resource_direction(pgs);
            if (dir != -1) return new UnitAction(UnitAction.TYPE_HARVEST, dir);
        } else {
            int dir = base_direction(pgs);
            if (dir != -1) return new UnitAction(UnitAction.TYPE_RETURN, dir);
        }
        return move_if_possible(direction, gs);
    }
}
