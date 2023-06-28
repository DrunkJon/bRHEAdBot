package main.RHEA;

import com.eclipsesource.json.JsonObject;
import jdk.nashorn.internal.ir.annotations.Immutable;
import main.BetterAbstraction.BetterAbstractionAI;
import main.BetterGameState;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitTypeTable;

public abstract class PlanAction implements Cloneable {

    public abstract void execute(Unit u, BetterAbstractionAI ai, int player, BetterGameState bgs);

    @Override
    public PlanAction clone() {
        try {
            return (PlanAction) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public abstract JsonObject toJson();

    public static PlanAction fromJson(JsonObject jo, UnitTypeTable utt) {
        String class_string = jo.get("class").asString();
        switch (class_string) {
            case "UnitAction":
                return new UnitAction(jo);
            case "BuildingAction":
                return new BuildingAction(jo, utt);
            default:
                throw new IllegalArgumentException();
        }
    }
}
