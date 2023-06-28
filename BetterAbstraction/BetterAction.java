/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package main.BetterAbstraction;

import ai.abstraction.pathfinding.*;
import org.jdom.Element;
import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import util.XMLWriter;

import static rts.UnitAction.*;

/**
 *
 * @author santi
 */
public abstract class BetterAction {

    public Unit unit;

    public BetterAction(Unit a_unit) {
        unit = a_unit;
    }

    
    public Unit getUnit() {
        return unit;
    }
    
    
    public void setUnit(Unit u) {
        unit = u;
    }


    public abstract boolean completed(GameState pgs);
    
    
    public UnitAction execute(GameState pgs){
    	return execute(pgs,null);
    }


    public abstract void toxml(XMLWriter w);
    
    
    public static BetterAction fromXML(Element e, PhysicalGameState gs, UnitTypeTable utt)
    {
        PathFinding pf = null;
        String pfString = e.getAttributeValue("pathfinding");
        if (pfString != null) {
            if (pfString.equals("AStarPathFinding")) pf = new AStarPathFinding();
            if (pfString.equals("BFSPathFinding")) pf = new BFSPathFinding();
            if (pfString.equals("FloodFillPathFinding")) pf = new FloodFillPathFinding();
            if (pfString.equals("GreedyPathFinding")) pf = new GreedyPathFinding();
        }
        switch (e.getName()) {
            case "Attack":
                return new Attack(gs.getUnit(Long.parseLong(e.getAttributeValue("unitID"))),
                        gs.getUnit(Long.parseLong(e.getAttributeValue("target"))),
                        pf);
            case "Build":
                return new Build(gs.getUnit(Long.parseLong(e.getAttributeValue("unitID"))),
                        utt.getUnitType(e.getAttributeValue("type")),
                        Integer.parseInt(e.getAttributeValue("x")),
                        Integer.parseInt(e.getAttributeValue("y")),
                        pf);
            case "Harvest":
                return new Harvest(gs.getUnit(Long.parseLong(e.getAttributeValue("unitID"))),
                        gs.getUnit(Long.parseLong(e.getAttributeValue("target"))),
                        gs.getUnit(Long.parseLong(e.getAttributeValue("base"))),
                        pf);
            case "Idle":
                return new Idle(gs.getUnit(Long.parseLong(e.getAttributeValue("unitID"))));
            case "Move":
                return new Move(gs.getUnit(Long.parseLong(e.getAttributeValue("unitID"))),
                        Integer.parseInt(e.getAttributeValue("x")),
                        Integer.parseInt(e.getAttributeValue("y")),
                        pf);
            case "Train":
                return new Train(gs.getUnit(Long.parseLong(e.getAttributeValue("unitID"))),
                        utt.getUnitType(e.getAttributeValue("type")));
            default:
                return null;
        }
    }
    
    
    public abstract UnitAction execute(GameState pgs, ResourceUsage ru);

    // Custom utils
    public int[] unit_after_move(int direction, PhysicalGameState pgs) {
        switch (direction) {
            case DIRECTION_NONE:    // -1
                return new int[]{unit.getX(), unit.getY()};
            case DIRECTION_UP:    // 0
                return new int[]{unit.getX(), Math.max(unit.getY()-1, 0)};
            case DIRECTION_RIGHT:    // 1
                return new int[]{Math.min(unit.getX()+1, pgs.getWidth()), unit.getY()};
            case DIRECTION_DOWN:    // 2
                return new int[]{unit.getX(), Math.min(unit.getY()+1, pgs.getHeight())};
            case DIRECTION_LEFT:    // 3
                return new int[]{Math.max(unit.getX()-1, 0), unit.getY()};
            default:
                throw new IllegalArgumentException("not a valid direction");
        }
    }

    public boolean unit_move_inbounds(int direction, PhysicalGameState pgs) {
        int[] pos = unit_after_move(direction, pgs);
        return pos[0] >= 0 && pos[0] < pgs.getWidth() && pos[1] >= 0 && pos[1] < pgs.getHeight();
    }

    public UnitAction move_if_possible(int direction, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int[] pos = unit_after_move(direction, pgs);
        boolean inbounds = pos[0] >= 0 && pos[0] < pgs.getWidth() && pos[1] >= 0 && pos[1] < pgs.getHeight();
        if (!inbounds || !gs.free(pos[0], pos[1])) {
            return null;
        } else {
            return new UnitAction(TYPE_MOVE, direction);
        }
    }

    public UnitAction build_if_possible(int direction, GameState gs, UnitType type) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int[] pos = unit_after_move(direction, pgs);
        boolean inbounds = pos[0] >= 0 && pos[0] < pgs.getWidth() && pos[1] >= 0 && pos[1] < pgs.getHeight();
        if (!inbounds || !gs.free(pos[0], pos[1])) {
            return null;
        } else {
            return new UnitAction(TYPE_PRODUCE, direction, type);
        }
    }
}
