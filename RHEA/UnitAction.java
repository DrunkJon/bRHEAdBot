package main.RHEA;

import com.eclipsesource.json.JsonObject;
import main.BetterAbstraction.BetterAbstractionAI;
import main.BetterGameState;
import rts.PhysicalGameState;
import rts.units.Unit;

import java.util.Random;

public class UnitAction extends PlanAction{
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
    public final int x;

    public final int y;
    public static final int move_span = 2;
    public static float harvest_chance = 0.5f;

    public UnitAction(UnitActionType type, int x, int y) {
        this.action_type = type;
        this.x = x;
        this.y = y;
    }
    public UnitAction(Unit u, int width, int height) {
        this.action_type = (u.getType().name.equals("Worker"))? random_worker_action_type() : random_warrior_action_type();
        this.x = r.nextInt(2 * move_span + 1) - move_span;
        this.y = r.nextInt(2 * move_span + 1) - move_span;
    }

    public UnitAction(JsonObject jo) {
        this.action_type = type_values[jo.get("action_type").asInt()];
        this.x = jo.get("x").asInt();
        this.y = jo.get("y").asInt();
    }

    @Override
    public void execute(Unit u, BetterAbstractionAI ai, int player, BetterGameState bgs) {
        // System.out.println(action_type.name());
        PhysicalGameState pgs = bgs.getPhysicalGameState();
        int abs_x = Math.max(Math.min(u.getX() + x, pgs.getWidth() - 1), 0);
        int abs_y = Math.max(Math.min(u.getX() + y, pgs.getWidth() - 1), 0);
        switch (action_type) {
            case ATTACK:
                ai.attack_closest(u, bgs, abs_x, abs_y);
                break;
            case MOVE:
                ai.attack_move(u, abs_x, abs_y, bgs);
                break;
            case IDLE:
                ai.idle(u);
                break;
            case BASE:
                ai.build_base(u, abs_x, abs_y);
                break;
            case BARRACKS:
                ai.build_barracks(u, abs_x, abs_y);
                break;
            case HARVEST:
                ai.harvest_closest(u, bgs);
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
        if (r.nextFloat() <= harvest_chance) {
            return UnitActionType.HARVEST;
        }
        int i = r.nextInt(type_values.length);
        return type_values[i];
    }

    public String toString() {
        return String.format("%s [%d|%d]", action_type.name(), x, y);
    }

    @Override
    public UnitAction clone() {
        UnitAction clone = (UnitAction) super.clone();
        return clone;
    }

    public JsonObject toJson() {
        JsonObject jo = new JsonObject();
        jo.add("class", "UnitAction");
        jo.add("action_type", action_type.ordinal());
        jo.add("x", x);
        jo.add("y", y);
        return jo;
    }
}
