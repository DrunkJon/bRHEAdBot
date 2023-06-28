package main.RHEA;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import main.BetterAbstraction.BetterAbstractionAI;
import main.BetterAbstraction.Harvest;
import main.BetterGameState;
import rts.GameState;
import rts.PhysicalGameState;
import rts.units.Unit;
import rts.units.UnitTypeTable;

import java.util.*;

public class Plan {
    private static final Random r = new Random();
    public HashMap<Long, List<PlanAction>> action_map;
    public int player;
    public static int step_frames = 20;
    public static int lookahead_steps = 25;

    public int times_scored = 0;
    public Float avg_score = 0.f;
    public Float last_score = 0.f;

    public static HashSet<String> unit_set = new HashSet<>(Arrays.asList("Worker", "Light", "Ranged", "Heavy"));
    public static HashSet<String> building_set = new HashSet<>(Arrays.asList("Base", "Barracks"));


    public Plan(int player, BetterGameState bgs) {
        this.action_map = new HashMap<>();
        this.player = player;
        generate_random_plan(player, bgs);
    }

    public Plan(int player, HashMap<Long, List<PlanAction>> action_map) {
        this.action_map = action_map;
        this.player = player;
    }

    public Plan(JsonObject jo, UnitTypeTable utt) {
        this.player = jo.get("player").asInt();
        this.action_map = new HashMap<>();
        JsonObject json_action_map = jo.get("action_map").asObject();
        for (JsonObject.Member member: json_action_map) {
            long key = Long.parseLong(member.getName());
            List<PlanAction> actions = new ArrayList<>();
            member.getValue().asArray().forEach(v -> actions.add(PlanAction.fromJson(v.asObject(), utt)));
            action_map.put(key, actions);
        }
    }

    public static Plan startPlan(int player, BetterGameState bgs) {
        HashMap<Long, List<PlanAction>> actions = new HashMap<>();
        List<Unit> units = bgs.getPlayerUnits(player);
        for (Unit u: units) {
            if (building_set.contains(u.getType().name)) {
                PlanAction train_action = new BuildingAction(u, BuildingAction.BuildingActionType.TRAIN);
                List<PlanAction> unit_actions = new ArrayList<>();
                for (int i = 0; i < lookahead_steps; i++) {
                    unit_actions.add(train_action);
                }
                actions.put(bgs.getBetterID(u), unit_actions);
            } else if (Objects.equals(u.getType().name, "Worker")) {
                PlanAction harvest_action = new UnitAction(UnitAction.UnitActionType.HARVEST, u.getX(), u.getY());
                PlanAction build_action = new UnitAction(UnitAction.UnitActionType.BARRACKS, u.getX() + 2, u.getY());
                List<PlanAction> unit_actions = new ArrayList<>();
                for (int i = 0; i < lookahead_steps; i++) {
                    if (i > 5 && i < 10) {
                        unit_actions.add(build_action);
                    } else {
                        unit_actions.add(harvest_action);
                    }
                }
                actions.put(bgs.getBetterID(u), unit_actions);
            } else {
                actions.put(bgs.getBetterID(u), randomActionList(u, bgs));
            }
        }
        return new Plan(player, actions);
    }

    public void generate_random_plan(int player, BetterGameState bgs){
        List<Unit> units = bgs.getPlayerUnits(player);

        for (Unit u: units) {
            if (building_set.contains(u.getType().name)) {
                action_map.put(bgs.getBetterID(u), randomUniformActionList(u, bgs));
            } else {
                action_map.put(bgs.getBetterID(u), randomActionList(u, bgs));
            }
        }
    }

    public static PlanAction randomAction(Unit u, GameState gs) {
        if (building_set.contains(u.getType().name)) {
            return new BuildingAction(u);
        } else if (unit_set.contains(u.getType().name)) {
            PhysicalGameState pgs = gs.getPhysicalGameState();
            return new UnitAction(u, pgs.getWidth(), pgs.getHeight());
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static List<PlanAction> randomActionList(Unit u, GameState gs) {
        List<PlanAction> new_actions = new ArrayList<>();
        int change = 0;
        PlanAction new_action = Plan.randomAction(u, gs);
        for (int i = 0; i < lookahead_steps; i++) {
            if (i == change) {
                new_action = Plan.randomAction(u, gs);
                change = r.nextInt(lookahead_steps - i + 1) + i;
            }
            new_actions.add(new_action.clone());
        }
        return new_actions;
    }

    public static List<PlanAction> randomUniformActionList(Unit u, int length, GameState gs) {
        List<PlanAction> new_actions = new ArrayList<>();
        PlanAction new_action = randomAction(u, gs);
        for (int i = 0; i < length; i++) {
            new_actions.add(new_action.clone());
        }
        return new_actions;
    }

    public static List<PlanAction> randomUniformActionList(Unit u, GameState gs) {
        return randomUniformActionList(u, lookahead_steps, gs);
    }

    public void assignActions(List<Unit> units, BetterAbstractionAI ai, int player, BetterGameState bgs, int i) {
        assert i < step_frames;
        for (Unit u: units) {
            long id = bgs.getBetterID(u);
            List<PlanAction> actions = action_map.get(id);
            if (actions == null) {
                actions = Plan.randomActionList(u, bgs);
                action_map.put(id, actions);
            }
            PlanAction action = actions.get(i);
            action.execute(u, ai, player, bgs);
        }
    }

    public void assignActions(List<Unit> units, BetterAbstractionAI ai, int player, BetterGameState bgs) {
        this.assignActions(units, ai, player, bgs, 0);
    }

    public void add_score(float score) {
        this.times_scored++;
        this.last_score = score;
        float frac = 1f / times_scored;
        this.avg_score = (1f -  frac) * avg_score + frac * (score - avg_score);
    }

    public void next_step(BetterGameState bgs) {
        for (long key: action_map.keySet()) {
            Unit u = bgs.getUnitFromBetterID(key);
            if (u != null) {
                List<PlanAction> actions = action_map.get(key);
                actions.remove(0);
                actions.add(randomAction(bgs.getUnitFromBetterID(key), bgs));
            }
        }
        updateGameState(bgs);
        times_scored /= 2;
    }

    public void updateGameState(BetterGameState bgs) {
        List<Unit> units = bgs.getPlayerUnits(player);
        Set<Long> to_remove = new HashSet<>(action_map.keySet());
        for (Unit u: units) {
            long id = bgs.getBetterID(u);
            if (action_map.containsKey(id)) {
                to_remove.remove(id);
            } else {
                action_map.put(id, randomActionList(u, bgs));
            }
        }
        for (Long id: to_remove) {
            action_map.remove(id);
        }
    }

    public Plan mutate(BetterGameState bgs) {
        Plan mutant = this.clone();
        Set<Long> keys = mutant.action_map.keySet();
        HashMap<Long, List<PlanAction>> mutant_map = mutant.action_map;
        for (Unit u: bgs.getPlayerUnits(player)) {
            if (keys.contains(bgs.getBetterID(u))) {
                float random = r.nextFloat();
                // insertion
                if (random <= 0.33) {
                    int index;
                    if (r.nextBoolean()) {
                        index = r.nextInt(lookahead_steps);
                    } else {
                        index = 0;
                    }
                    int length = r.nextInt(lookahead_steps-index + 1);
                    List<PlanAction> insert_plan = randomUniformActionList(u, length, bgs);
                    mutant.insertActions(bgs.getBetterID(u), insert_plan, index, length);
                    // copy
                } else if (random <= 0.66) {
                    List<Unit> copyable = bgs.getPlayerUnits(player, u.getType());
                    if (!copyable.isEmpty()) {
                        long copy_id = r.nextInt(copyable.size());
                        if (keys.contains(copy_id)) {
                            mutant_map.put(bgs.getBetterID(u), cloneActions(mutant_map.get(copy_id)));
                        }
                    }
                    // swap
                } else {
                    List<Unit> copyable = bgs.getPlayerUnits(player, u.getType());
                    if (!copyable.isEmpty()) {
                        long copy_id = r.nextInt(copyable.size());
                        if (keys.contains(copy_id)) {
                            List<PlanAction> temp = mutant_map.get(bgs.getBetterID(u));
                            mutant_map.put(bgs.getBetterID(u), mutant_map.get(copy_id));
                            mutant_map.put(copy_id, temp);
                        }
                    }
                }
            }
        }
        return mutant;
    }

    public static List<Plan> crossover(Plan p1, Plan p2, BetterGameState bgs) {
        assert p1.player == p2.player;
        Plan c1 = p1.clone();
        Plan c2 = p2.clone();
        Set<Long> keys1 = p1.action_map.keySet();
        Set<Long> keys2 = p2.action_map.keySet();

        float random = r.nextFloat();
        if (random < 0.5) {
            // SWAP
            for (Unit u: bgs.getPlayerUnits(p1.player)) {
                long id = bgs.getBetterID(u);
                if(keys1.contains(id) && keys2.contains(bgs.getBetterID(u))) {
                    if (r.nextFloat() < 0.5) {
                        List<PlanAction> temp = c1.action_map.get(id);
                        c1.action_map.put(id, c2.action_map.get(id));
                        c2.action_map.put(id, temp);
                    }
                } else if (keys1.contains(id)) {
                    c2.action_map.put(id, c1.action_map.get(id));
                } else if (keys2.contains(id)) {
                    c1.action_map.put(id, c2.action_map.get(id));
                } else {
                    c1.action_map.put(id, randomActionList(u, bgs));
                    c2.action_map.put(id, randomActionList(u, bgs));
                }
            }
        } else {
            // N-POINT
            int size = p1.size();
            int n_points = Math.min(size ,r.nextInt(5)+1);
            int offset = (size / n_points);
            boolean swap = r.nextBoolean();
            int last_index = 0;
            boolean filled = false;
            for (int i = 0; i <= n_points; i++) {
                int index;
                if (i != n_points){
                    index = (size / n_points) * i + r.nextInt(offset);
                } else {
                    index = size - 1;
                }
                if (swap) {
                    for (Unit u: bgs.getPlayerUnits(p1.player)) {
                        long id = bgs.getBetterID(u);
                        if(filled || (keys1.contains(id) && keys2.contains(bgs.getBetterID(u))) ) {
                            List<PlanAction> temp = cloneActions(c1.action_map.get(id).subList(last_index, index));
                            c1.insertActions(id, cloneActions(c2.action_map.get(id).subList(last_index, index)), last_index, index - last_index);
                            c2.insertActions(id, temp, last_index, index - last_index);
                        } else if (keys1.contains(id)) {
                            c2.action_map.put(id, cloneActions(c1.action_map.get(id)));
                        } else if (keys2.contains(id)) {
                            c1.action_map.put(id, cloneActions(c2.action_map.get(id)));
                        } else {
                            c1.action_map.put(id, randomActionList(u, bgs));
                            c2.action_map.put(id, randomActionList(u, bgs));
                        }
                    }
                    filled = true;
                }
                last_index = index;
                swap = !swap;
            }
        }

        List<Plan> out = new ArrayList<>();
        out.add(c1);
        out.add(c2);
        return out;     // Are p1 and p2 still the same?
    }

    public void insertActions(long id, List<PlanAction> insert, int index, int length) {
        List<PlanAction> temp = action_map.get(id);
        for (int i = 0; i< length; i++) {
            temp.remove(index + i);
            temp.add(index+i, insert.get(i));
        }
    }

    @Override
    public Plan clone() {
        HashMap<Long, List<PlanAction>> new_map = new HashMap<Long, List<PlanAction>>();
        for (long key: action_map.keySet()) {
            new_map.put(key, cloneActions(action_map.get(key)));
        }
        return new Plan(this.player, new_map);
    }

    public static List<PlanAction> cloneActions(List<PlanAction> actions) {
        List<PlanAction> out = new ArrayList<>(actions.size());
        out.addAll(actions);
        return out;  //don't need to clone PlanAction's because they're immutable
    }

    public int size() {
        for (List<PlanAction> val: action_map.values()) {
            return val.size();
        }
        return -1;
    }

    public static int getTimeLimit() {
        return step_frames * lookahead_steps;
    }

    public JsonObject toJson() {
        JsonObject jo = new JsonObject();
        jo.add("player", player);
        JsonObject json_action_map = new JsonObject();
        for (long key: action_map.keySet()) {
            JsonArray json_actions = new JsonArray();
            action_map.get(key).forEach(act -> json_actions.add(act.toJson()));
            json_action_map.add(String.valueOf(key), json_actions);
        }
        jo.add("action_map", json_action_map);
        return jo;
    }

    public void trim() {
        for (long key: action_map.keySet()) {
            action_map.put(key, new ArrayList<>(action_map.get(key).subList(0, Plan.lookahead_steps)));
        }
    }
}
