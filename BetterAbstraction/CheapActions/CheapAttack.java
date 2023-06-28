package main.BetterAbstraction.CheapActions;

import ai.abstraction.pathfinding.PathFinding;
import main.BetterAbstraction.Attack;
import main.BetterAbstraction.BetterAction;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import util.XMLWriter;

import java.util.Collection;
import java.util.List;

public class CheapAttack extends BetterAction {

    private final int direction;
    public CheapAttack(Unit a_unit, int direction) {
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
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int[] pos = unit_after_move(direction, pgs);
        Unit direction_unit = pgs.getUnitAt(pos[0], pos[1]);
        if (direction_unit != null && direction_unit.getPlayer() != unit.getPlayer() && direction_unit.getPlayer() != -1) {
            return new UnitAction(UnitAction.TYPE_ATTACK_LOCATION, pos[0], pos[1]);
        }
        Collection<Unit> around_units = pgs.getUnitsAround(unit.getX(), unit.getY(), unit.getAttackRange());
        Unit around_enemy = around_units.stream().filter(
                u -> u.getPlayer() != unit.getPlayer() && u.getPlayer() != -1
        ).findFirst().orElse(null);
        if (around_enemy != null) {
            return new UnitAction(UnitAction.TYPE_ATTACK_LOCATION, around_enemy.getX(), around_enemy.getY());
        }
        return move_if_possible(direction, gs);
    }
}
