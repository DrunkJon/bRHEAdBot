package main.RHEA;

import com.eclipsesource.json.JsonObject;
import main.BetterAbstraction.BetterAbstractionAI;
import main.BetterGameState;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import java.util.List;
import java.util.Random;

public class BuildingAction extends PlanAction{
    public enum BuildingActionType {
        TRAIN,
        IDLE;

    }
    private static final BuildingActionType[] type_values = BuildingActionType.values();
    private static final Random r = new Random();
    public final BuildingActionType action_type;
    public final UnitType unit_type;

    public static float worker_chance = 0.2f;
    public BuildingAction(BuildingActionType type, UnitType unit_type) {
        this.action_type = type;
        this.unit_type = unit_type;
    }

    public BuildingAction(Unit u, BuildingActionType type) {
        this(type, u.getType().produces.get(r.nextInt(u.getType().produces.size())));
    }

    public BuildingAction(Unit u) {
        List<UnitType> u_produces = u.getType().produces;
        assert !u_produces.isEmpty();
        int i = r.nextInt(u_produces.size());
        this.unit_type = u_produces.get(i);
        this.action_type = random_action_type(u);
    }

    public BuildingAction(JsonObject jo, UnitTypeTable utt) {
        this.action_type = BuildingActionType.values()[jo.get("action_type").asInt()];
        this.unit_type = utt.getUnitType(jo.get("unit_type").asString());
    }

    @Override
    public void execute(Unit u, BetterAbstractionAI ai, int player, BetterGameState bgs) {
        switch (action_type) {
            case TRAIN:
                ai.train(u, unit_type);
                break;
            case IDLE:
                ai.idle(u);
                break;
            default:
                throw new IllegalStateException(String.format("Unit #%d had illegal action_type %s", u.getID(), action_type.name()));
        }
    }
    private static BuildingActionType random_action_type() {
        int i = r.nextInt(type_values.length);
        return type_values[i];
    }

    public static BuildingActionType random_action_type(Unit u) {
        if (u.getType().name.equals("Base")) {
            if (r.nextFloat() < worker_chance) {
                return BuildingActionType.TRAIN;
            } else {
                return BuildingActionType.IDLE;
            }
        }
        int i = r.nextInt(type_values.length);
        return type_values[i];
    }

    public String toString() {
        return String.format("%s [%s]", action_type.name(), unit_type.name);
    }

    @Override
    public BuildingAction clone() {
        BuildingAction clone = (BuildingAction) super.clone();
        return clone;
    }

    @Override
    public JsonObject toJson() {
        JsonObject jo = new JsonObject();
        jo.add("class", "BuildingAction");
        jo.add("action_type", action_type.ordinal());
        jo.add("unit_type", unit_type.name);
        return jo;
    }
}
