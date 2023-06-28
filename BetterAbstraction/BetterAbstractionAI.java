/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package main.BetterAbstraction;

import ai.abstraction.pathfinding.GreedyPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AIWithComputationBudget;
import main.BetterAbstraction.CheapActions.CheapAttack;
import main.BetterAbstraction.CheapActions.CheapBuild;
import main.BetterAbstraction.CheapActions.CheapHarvest;
import main.BetterAbstraction.CheapActions.CheapMove;
import main.BetterGameState;
import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 *
 * @author santi
 */
public abstract class BetterAbstractionAI extends AIWithComputationBudget {

    // set this to true, if you believe there is a bug, and want to verify that actions
    // being generated are actually possible before sending to the game.
    public static boolean VERIFY_ACTION_CORRECTNESS = false;

    // functionality that this abstraction layer offers:
    // 1) You can forget about issuing actions to all units, just issue the ones you want, the rest are set to NONE automatically
    // 2) High level actions (using A*):
    //      - move(x,y)
    //      - train(type)
    //      - build(type,x,y)
    //      - harvest(target)
    //      - attack(target)
    protected HashMap<Unit, BetterAction> actions = new LinkedHashMap<>();
    protected PathFinding pf;
    public UnitTypeTable utt;
    // In case the GameState is cloned, and the Unit pointers in the "actions" map change, this variable
    // saves a pointer to the previous GameState, if it's different than the current one, then we need to find a mapping
    // between the old units and the new ones
    protected GameState lastGameState;
    protected HashMap<Unit, Unit[]> harvest_hash = new HashMap<Unit, Unit[]>();

    public BetterAbstractionAI(GreedyPathFinding a_pf, UnitTypeTable utt) {
        super(100, -1);
        pf = a_pf;
        this.utt = utt;
    }

    public BetterAbstractionAI(GreedyPathFinding a_pf, int timebudget, int cyclesbudget, UnitTypeTable utt) {
        super(timebudget, cyclesbudget);
        pf = a_pf;
        this.utt = utt;
    }

    public void reset() {
        actions.clear();
    }
       

    public PlayerAction translateActions(int player, GameState gs) {        
        PhysicalGameState pgs = gs.getPhysicalGameState();
        PlayerAction pa = new PlayerAction();
        List<Pair<Unit, UnitAction>> desires = new ArrayList<>();

        if (lastGameState != gs) {
            harvest_hash = new HashMap<>();
        }
        lastGameState = gs;

        // Execute abstract actions:
        List<Unit> toDelete = new ArrayList<>();
        ResourceUsage ru = new ResourceUsage();
        for (BetterAction aa : actions.values()) {
            if (!pgs.getUnits().contains(aa.unit)) {
                // The unit is dead:
                toDelete.add(aa.unit);
            } else {
                if (aa.completed(gs)) {
                    // the action is complete:
                    toDelete.add(aa.unit);
                } else {
                    if (gs.getActionAssignment(aa.unit) == null) {
                        UnitAction ua = aa.execute(gs, ru);
                        if (ua != null) {
                            if (VERIFY_ACTION_CORRECTNESS) {
                                // verify that the action is actually feasible:
                                List<UnitAction> ual = aa.unit.getUnitActions(gs);
                                if (ual.contains(ua)) {
                                    desires.add(new Pair<>(aa.unit, ua));
                                }
                            } else {
                                desires.add(new Pair<>(aa.unit, ua));
                            }
                            ru.merge(ua.resourceUsage(aa.unit, pgs));
                        }

                    }
                }
            }
        }
        for (Unit u : toDelete) {
            actions.remove(u);
        }

        // compose desires:
        ResourceUsage r = gs.getResourceUsage();
        pa.setResourceUsage(r);
        for (Pair<Unit, UnitAction> desire : desires) {
            ResourceUsage r2 = desire.m_b.resourceUsage(desire.m_a, pgs);
            if (pa.consistentWith(r2, gs)) {
                pa.addUnitAction(desire.m_a, desire.m_b);
                pa.getResourceUsage().merge(r2);
            }
        }

        pa.fillWithNones(gs, player, 10);
        return pa;
    }

    public BetterAction getAbstractAction(Unit u) {
        return actions.get(u);
    }

    public int findBuildingPosition(List<Integer> reserved, int desiredX, int desiredY, Player p, PhysicalGameState pgs) {

        boolean[][] free = pgs.getAllFree();
        int x, y;

        /*
        System.out.println("-" + desiredX + "," + desiredY + "-------------------");
        for(int i = 0;i<free[0].length;i++) {
            for(int j = 0;j<free.length;j++) {
                System.out.print(free[j][i] + "\t");
            }
            System.out.println("");
        }
        */
        
        for (int l = 1; l < Math.max(pgs.getHeight(), pgs.getWidth()); l++) {
            for (int side = 0; side < 4; side++) {
                switch (side) {
                    case 0://up
                        y = desiredY - l;
                        if (y < 0) {
                            continue;
                        }
                        for (int dx = -l; dx <= l; dx++) {
                            x = desiredX + dx;
                            if (x < 0 || x >= pgs.getWidth()) {
                                continue;
                            }
                            int pos = x + y * pgs.getWidth();
                            if (!reserved.contains(pos) && free[x][y]) {
                                return pos;
                            }
                        }
                        break;
                    case 1://right
                        x = desiredX + l;
                        if (x >= pgs.getWidth()) {
                            continue;
                        }
                        for (int dy = -l; dy <= l; dy++) {
                            y = desiredY + dy;
                            if (y < 0 || y >= pgs.getHeight()) {
                                continue;
                            }
                            int pos = x + y * pgs.getWidth();
                            if (!reserved.contains(pos) && free[x][y]) {
                                return pos;
                            }
                        }
                        break;
                    case 2://down
                        y = desiredY + l;
                        if (y >= pgs.getHeight()) {
                            continue;
                        }
                        for (int dx = -l; dx <= l; dx++) {
                            x = desiredX + dx;
                            if (x < 0 || x >= pgs.getWidth()) {
                                continue;
                            }
                            int pos = x + y * pgs.getWidth();
                            if (!reserved.contains(pos) && free[x][y]) {
                                return pos;
                            }
                        }
                        break;
                    case 3://left
                        x = desiredX - l;
                        if (x < 0) {
                            continue;
                        }
                        for (int dy = -l; dy <= l; dy++) {
                            y = desiredY + dy;
                            if (y < 0 || y >= pgs.getHeight()) {
                                continue;
                            }
                            int pos = x + y * pgs.getWidth();
                            if (!reserved.contains(pos) && free[x][y]) {
                                return pos;
                            }
                        }
                        break;
                }
            }
        }
        return -1;
    }

    public boolean buildIfNotAlreadyBuilding(Unit u, UnitType type, int desiredX, int desiredY, List<Integer> reservedPositions, Player p, PhysicalGameState pgs) {
        BetterAction action = getAbstractAction(u);
//        System.out.println("buildIfNotAlreadyBuilding: action = " + action);
        if (!(action instanceof Build) || ((Build) action).type != type) {
            int pos = findBuildingPosition(reservedPositions, desiredX, desiredY, p, pgs);
            
//            System.out.println("pos = " + (pos % pgs.getWidth()) + "," + (pos / pgs.getWidth()));
            
            build(u, type, pos % pgs.getWidth(), pos / pgs.getWidth());
            reservedPositions.add(pos);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + pf + ")";
    }

    public PathFinding getPathFinding() {
        return pf;
    }

    public void setPathFinding(GreedyPathFinding a_pf) {
        pf = a_pf;
    }

    public void move(Unit u, int x, int y) {
        actions.put(u, new Move(u, x, y, pf));
    }

    public void train(Unit u, UnitType unit_type) {
        actions.put(u, new Train(u, unit_type));
    }

    public void build(Unit u, UnitType unit_type, int x, int y) {
        actions.put(u, new Build(u, unit_type, x, y, pf));
    }

    public void harvest(Unit u, Unit target, Unit base) {
        actions.put(u, new Harvest(u, target, base, pf));
    }

    public void attack(Unit u, Unit target) {
        actions.put(u, new Attack(u, target, pf));
    }

    public void idle(Unit u) {
        actions.put(u, new Idle(u));
    }

    // CUSTOM ACTIONS
    public void attack_closest(Unit u, BetterGameState bgs) {
        Unit closest_enemy = get_closest_unit(u, bgs.getPlayerUnits(1-u.getPlayer()), bgs);
        if (closest_enemy != null) {
            attack(u, closest_enemy);
        } else {
            idle(u);
        }
    }

    public void attack_closest(Unit u, BetterGameState bgs, int x, int y) {
        Unit closest_enemy = get_closest_unit(u, bgs.getPlayerUnits(1-u.getPlayer()), bgs);
        if (closest_enemy != null) {
            attack(u, closest_enemy);
        } else {
            move(u, x, y);
        }
    }
    
    public void attack_move(Unit u, int x, int y, BetterGameState bgs) {
        PhysicalGameState pgs = bgs.getPhysicalGameState();
        Unit closest_enemy = get_closest_unit(u, bgs.getPlayerUnits(1-u.getPlayer()), bgs);
        if (closest_enemy != null && euclidianDistance(u, closest_enemy) <= u.getAttackRange()) {
            attack(u, closest_enemy);
        } else {
            move(u, x, y);
        }
    }

    public void build_base(Unit u, int x, int y) {
        build(u, utt.getUnitType("Base"), x, y);
    }

    public void build_barracks(Unit u, int x, int y) {
        build(u, utt.getUnitType("Barracks"), x, y);
    }

    public void harvest_closest(Unit u, BetterGameState bgs) {
        List<Unit> resources = bgs.getResources();
        List<Unit> bases = bgs.getPlayerUnits(u.getPlayer(), utt.getUnitType("Base"));
        if (resources.isEmpty() || bases.isEmpty()) {
            idle(u);
        }
        Unit closest_resource = get_closest_unit(u, resources, bgs);
        Unit closest_base = get_closest_unit(u, bases, bgs);
        if (!(closest_resource == null || closest_base == null)) {
            harvest_hash.put(u, new Unit[]{closest_resource, closest_base});
            harvest(u, closest_resource, closest_base);
        } else {
            idle(u);
        }
    }

    public void cheap_attack(Unit u, int direction) {
        actions.put(u, new CheapAttack(u, direction));
    }

    public void cheap_move(Unit u, int direction) {
        actions.put(u, new CheapMove(u, direction));
    }

    public void cheap_base(Unit u, int direction) {
        actions.put(u, new CheapBuild(u, utt.getUnitType("Base"), direction));
    }

    public void cheap_barracks(Unit u, int direction) {
        actions.put(u, new CheapBuild(u, utt.getUnitType("Barracks"), direction));
    }

    public void cheap_harvest(Unit u, int direction) {
        actions.put(u, new CheapHarvest(u, direction));
    }

    // CUSTOM UTIL
    public double euclidianDistance(Unit u1, Unit u2) {
        return Math.sqrt(Math.pow(u1.getX() - u2.getX(), 2) + Math.pow(u1.getY() - u2.getY(), 2));
    }
    public int manhattanDistance(Unit u1, Unit u2, BetterGameState bgs) {
        return Math.abs(u2.getX() - u1.getX()) + Math.abs(u2.getY() - u1.getY());
    }
    public Unit get_closest_unit(Unit me, List<Unit> units, BetterGameState bgs) {
        assert !units.isEmpty();
        return units.stream().min(
                (u1, u2) -> manhattanDistance(u1, me, bgs) - manhattanDistance(u2, me, bgs)
        ).orElse(null);
    }
}
