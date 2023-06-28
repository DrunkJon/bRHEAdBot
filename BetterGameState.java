package main;

import rts.GameState;
import rts.PhysicalGameState;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import java.util.*;
import java.util.stream.Collectors;

public class BetterGameState extends GameState {
    private final HashMap<Integer, List<Unit>> unit_map;

    // private final UnitCounter uc;

    /**
     * Initializes the GameState with a PhysicalGameState and a UnitTypeTable
     *
     * @param a_pgs
     * @param a_utt
     */
    public BetterGameState(PhysicalGameState a_pgs, UnitTypeTable a_utt) {
        super(a_pgs, a_utt);
        unit_map = init_unit_map();
        // uc = new UnitCounter(a_pgs, utt);
    }

    public BetterGameState(GameState gs) {
        this(gs.getPhysicalGameState(), gs.getUnitTypeTable());
    }

    private HashMap<Integer, List<Unit>> init_unit_map() {
        HashMap<Integer, List<Unit>> um = new HashMap<>();
        List<Unit> units = pgs.getUnits();
        for (Unit u:units) {
            int player_id = u.getPlayer();
            if (!um.containsKey(player_id)) {
                um.put(player_id, new ArrayList<>());
            }
            um.get(player_id).add(u);
        }
        return um;
    }

    private List<Unit> getUnitList(int player_id) {
        List<Unit> output = unit_map.get(player_id);
        if (output == null) {
            return new ArrayList<>();
        } else {
            return output;
        }
    }

    public List<Unit> getPlayerUnits(int player_id, UnitType[] unit_types) {
        if (unit_types == null) {
            return getUnitList(player_id);
        } else {
            HashSet<UnitType> unit_type_set = new HashSet<UnitType>(Arrays.asList(unit_types));
            return getUnitList(player_id).stream()
                    .filter(u -> unit_type_set.contains(u.getType()))
                    .collect(Collectors.toList());
        }
    }

    public List<Unit> getPlayerUnits(int player_id, UnitType unit_type) {
        return getPlayerUnits(player_id, new UnitType[]{unit_type});
    }

    public List<Unit> getPlayerUnits(int player_id) {
        return getPlayerUnits(player_id, (UnitType[]) null);
    }

    public List<Unit> getResources() {
        return getPlayerUnits(-1);
    }

    public BetterGameState clone() {
        return new BetterGameState(super.clone());
    }

    public long getBetterID(Unit u) {
        return u.getID();
    }

    public long getBetterID(long unit_id) {
        return unit_id;
    }

    public Unit getUnitFromBetterID(long id) {
        return pgs.getUnit(id);
    }

    public void ucDEBUG() {
        // uc.printDEBUG(getUnits());
        return;
    }
}
