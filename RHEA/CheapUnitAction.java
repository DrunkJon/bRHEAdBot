package main.RHEA;

import com.eclipsesource.json.JsonObject;
import main.BetterAbstraction.BetterAbstractionAI;
import main.BetterGameState;
import rts.UnitAction;
import rts.units.Unit;

import java.util.Random;

public class CheapUnitAction extends PlanAction{
    public enum UnitActionType {
        ATTACK,
        MOVE,
        IDLE,
        HARVEST,
        BASE,
        BARRACKS;

    }
    private static final UnitActionType[] type_values = UnitActionType.values();
    private static final int num_warrior_actions = 3;

    private static final Random r = new Random();

    public final UnitActionType action_type;

    public final int direction;
    public CheapUnitAction(UnitActionType type, int direction) {
        this.action_type = type;
        this.direction = direction;
    }

    public CheapUnitAction(Unit u) {
        this.action_type = (u.getType().name.equals("Worker"))? random_worker_action_type() : random_warrior_action_type();
        this.direction = r.nextInt(5)-1;    // -1 is NO_DIRECTION, above are other directions
    }

    @Override
    public void execute(Unit u, BetterAbstractionAI ai, int player, BetterGameState bgs) {
        // System.out.println(action_type.name());
        switch (action_type) {
            case ATTACK:
                ai.cheap_attack(u, direction);
                break;
            case MOVE:
                ai.cheap_move(u, direction);
                break;
            case IDLE:
                ai.idle(u);
                break;
            case BASE:
                ai.cheap_base(u, direction);
                break;
            case BARRACKS:
                ai.cheap_barracks(u, direction);
                break;
            case HARVEST:
                ai.cheap_harvest(u, direction);
                break;
            default:
                throw new IllegalStateException(String.format("Unit #%d had illegal action_type %s", u.getID(), action_type.name()));
        }
    }

    public static UnitActionType random_warrior_action_type() {
        int i = r.nextInt(num_warrior_actions);
        return type_values[i];
    }

    public static UnitActionType random_worker_action_type() {
        int i = r.nextInt(type_values.length);
        return type_values[i];
    }

    @Override
    public CheapUnitAction clone() {
        CheapUnitAction clone = (CheapUnitAction) super.clone();
        return clone;
    }

    @Override
    public JsonObject toJson() {
        throw new IllegalStateException("CheapUnitAction is not serializeable");
    }


}
