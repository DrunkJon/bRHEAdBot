package main;

import rts.PhysicalGameState;
import rts.Player;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import java.util.*;

public class UnitCounter {

    private final HashMap<UnitType, Integer> unitType_to_index = new HashMap<>();

    private HashMap<Integer, HashMap<UnitType, List<Long>>> player_unit_map = new HashMap<>();

    private HashMap<Long, Long> betterID = new HashMap<>();
    private HashMap<Long, Unit> unit_from_betterID = new HashMap<>();

    private UnitTypeTable utt;
    private final int num_unit_types;
    private final int num_players = 2; //resource player handled separately

    private PhysicalGameState pgs;

    public UnitCounter(PhysicalGameState pgs, UnitTypeTable utt) {
        this.pgs = pgs;
        List<UnitType> unit_types = utt.getUnitTypes();
        this.num_unit_types = unit_types.size();
        int count = 0;
        for (UnitType ut: unit_types) {
            this.unitType_to_index.put(ut, count);
            count++;
        }

        for (int i = -1; i < 2; i++) {
            player_unit_map.put(i, new HashMap<>());
        }
        List<Unit> units = new ArrayList<>(pgs.getUnits());
        units.sort(Comparator.comparing(Unit::getID));
        for (Unit u : units) {
            addUnit(u.clone());
        }
    }

    public void addUnit(Unit newUnit) {
        int player_id = newUnit.getPlayer();
        long unit_id = newUnit.getID();
        UnitType newUnitType = newUnit.getType();
        HashMap<UnitType, List<Long>> unit_type_map = player_unit_map.get(player_id);
        if (! unit_type_map.containsKey(newUnitType)) {
            unit_type_map.put(newUnitType, new ArrayList<>());
        }
        List<Long> id_list = unit_type_map.get(newUnitType);
        long unit_num = id_list.size();
        id_list.add(unit_id);
        long better_id;
        if (player_id == -1) {
            better_id = -(num_unit_types * unit_num + unitType_to_index.get(newUnit.getType()));
        } else {
            better_id = num_players * (num_unit_types * unit_num + unitType_to_index.get(newUnit.getType())) + player_id;
        }
        betterID.put(unit_id, better_id);
        unit_from_betterID.put(better_id, newUnit);
    }

    public void update() {
        List<Unit> units = pgs.getUnits();
        for (Unit u: units) {
            if (! betterID.containsKey(u.getID())) addUnit(u);
        }
    }

    public long getBetterID(Unit u) {
        assert (betterID.containsKey(u.getID()));
        return betterID.get(u.getID());
    }

    public long getBetterID(long unit_id) {
        assert (betterID.containsKey(unit_id));
        return betterID.get(unit_id);
    }

    public Unit getUnitFromBetterID(long id) {
        if (!unit_from_betterID.containsKey(id)) {
            update();
        }
        return unit_from_betterID.get(id);
    }

    public void printDEBUG(List<Unit> units) {
        for (Unit u: units) {
            System.out.printf("%s #%d -> #%d\n", u.getType().name, u.getID(), betterID.get(u.getID()));
        }
    }
}
