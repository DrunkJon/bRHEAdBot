package main.RHEA;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import main.BetterGameState;
import rts.GameState;
import rts.units.UnitTypeTable;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class PlanContainer implements Serializable {
    public List<Plan> player_plans;
    public List<Plan> enemy_plans;
    public int player;
    public PlanContainer(List<Plan> player_plans, List<Plan> enemy_plans, int player) {
        assert player_plans.size() == enemy_plans.size();
        this.player_plans = player_plans;
        this.enemy_plans = enemy_plans;
        this.player = player;
    }

    public PlanContainer(int player) {
        this(new ArrayList<>(), new ArrayList<>(), player);
    }

    public PlanContainer(int player, int length, BetterGameState bgs) {
        this(player);
        for (int i = 0; i < length; i++) {
            this.player_plans.add(new Plan(player, bgs));
            this.enemy_plans.add(new Plan(1-player, bgs));
        }
    }

    public PlanContainer(JsonObject jo, UnitTypeTable utt) {
        this(jo.get("player").asInt());
        jo.get("player_plans").asArray().forEach(v -> player_plans.add(new Plan(v.asObject(), utt)));
        jo.get("player_plans").asArray().forEach(v -> enemy_plans.add(new Plan(v.asObject(), utt)));
    }

    public static PlanContainer startPop(int player, int length, BetterGameState bgs) {
        PlanContainer container = new PlanContainer(player);
        Plan player_start = Plan.startPlan(player, bgs);
        Plan enemy_start = Plan.startPlan(1-player, bgs);
        container.player_plans.add(player_start);
        container.enemy_plans.add(enemy_start);
        for (int i = 0; i < length-1; i++) {
            container.player_plans.add(player_start.mutate(bgs));
            container.enemy_plans.add(enemy_start.mutate(bgs));
        }
        return container;
    }

    public void add(PlanContainer other) {
        assert other.player == this.player || other.player == 1 - this.player;
        List<Plan> other_player;
        List<Plan> other_enemy;
        if (this.player == other.player) {
            other_player = other.player_plans;
            other_enemy = other.enemy_plans;
        } else {
            other_player = other.enemy_plans;
            other_enemy = other.player_plans;
        }
        this.player_plans.addAll(other_player);
        this.enemy_plans.addAll(other_enemy);
    }

    public int size() {
        assert player_plans.size() == enemy_plans.size();
        return player_plans.size();
    }

    public PlanContainer subContainer(int start, int length) {
        return new PlanContainer(
                this.player_plans.subList(start, start+length),
                this.enemy_plans.subList(start, start+length),
                this.player
        );
    }

    public void shuffle() {
        Collections.shuffle(this.player_plans);
        Collections.shuffle(this.enemy_plans);
    }

    public Plan getBestPlayer() {
        return this.player_plans.stream().max(Comparator.comparing(p -> p.avg_score * Math.log(p.times_scored))).get();
    }

    public Plan getBestEnemy() {
        return this.enemy_plans.stream().max(Comparator.comparing(
                p -> p.avg_score * Math.log(p.times_scored) - ((p.times_scored <= 0)? 500000 : 0)
        )).get();
    }

    public PlanContainer reduceToBestN(int n) {
        player_plans.sort(Comparator.comparing(p -> -p.avg_score));
        player_plans = player_plans.subList(0, n);
        enemy_plans.sort(Comparator.comparing(p -> -p.avg_score + ((p.times_scored <= 0)? 500000 : 0)));
        enemy_plans = enemy_plans.subList(0,n);
        return this;
    }
    public void updateGameSate(BetterGameState bgs) {
        for (Plan pp: player_plans) {
            pp.updateGameState(bgs);
        }
        for (Plan ep: enemy_plans) {
            ep.updateGameState(bgs);
        }
    }

    public void next_step(BetterGameState bgs) {
        for (Plan pp: player_plans) {
            pp.next_step(bgs);
        }
        for (Plan ep: enemy_plans) {
            ep.next_step(bgs);
        }
    }

    public PlanContainer mutate(int num, BetterGameState bgs) {
        shuffle();
        List<Plan> new_player = new ArrayList<>(num);
        List<Plan> new_enemy = new ArrayList<>(num);
        int size = this.size();
        for (int i = 0; i < num; i++) {
            new_player.add(player_plans.get(i % size).mutate(bgs));
            new_enemy.add(enemy_plans.get(i % size).mutate(bgs));
        }
        return new PlanContainer(new_player, new_enemy, this.player);
    }

    public PlanContainer crossover(int num, BetterGameState bgs) {
        List<Plan> best_player = clonePlanListShallow(player_plans);
        best_player.sort(Comparator.comparing(p -> p.last_score));
        List<Plan> oldest_player = clonePlanListShallow(player_plans);
        oldest_player.sort(Comparator.comparing(p -> p.times_scored));

        List<Plan> best_enemy = clonePlanListShallow(enemy_plans);
        best_enemy.sort(Comparator.comparing(p -> p.last_score));
        List<Plan> oldest_enemy = clonePlanListShallow(enemy_plans);
        oldest_enemy.sort(Comparator.comparing(p -> p.times_scored));

        List<Plan> selected_player = switchingSelect(num, best_player, oldest_player);;
        List<Plan> selected_enemy = switchingSelect(num, best_enemy, oldest_enemy);

        Collections.shuffle(selected_player);
        Collections.shuffle(selected_enemy);

        List<Plan> player_kids = new ArrayList<>();
        List<Plan> enemy_kids = new ArrayList<>();

        for(int i = 0; i<num; i+=2) {
            player_kids.addAll(Plan.crossover(selected_player.get(i), selected_player.get(i+1), bgs));
            enemy_kids.addAll(Plan.crossover(selected_enemy.get(i), selected_enemy.get(i+1), bgs));
        }

        return new PlanContainer(player_kids, enemy_kids, player);
    }

    public PlanContainer crossoverAll(int num, BetterGameState bgs) {
        List<Plan> player_kids = new ArrayList<>();
        List<Plan> enemy_kids = new ArrayList<>();

        assert num == this.size();
        for(int i = 0; i<num; i+=2) {
            player_kids.addAll(Plan.crossover(player_plans.get(i).clone(), player_plans.get(i+1).clone(), bgs));
            enemy_kids.addAll(Plan.crossover(enemy_plans.get(i).clone(), enemy_plans.get(i+1).clone(), bgs));
        }

        return new PlanContainer(player_kids, enemy_kids, player);
    }

    private List<Plan> switchingSelect(int num, List<Plan> list1, List<Plan> list2) {
        Set<Plan> selected = new HashSet<>();
        int i1 =0;
        int i2 = 0;
        boolean best = false;
        while (selected.size() < num) {
            int i = best? i1 : i2;
            Plan plan = best? list1.get(i) : list2.get(i);
            i1 += best? 1 : 0;
            i2 += best? 0 : 1;
            if (!selected.contains(plan)) {
                selected.add(plan.clone());
                best = !best;
            }
        }
        return new ArrayList<>(selected);
    }

    public List<Plan> clonePlanListShallow(List<Plan> list) {
        return new ArrayList<>(list);
    }

    public List<Plan> clonePlanListDeep(List<Plan> list) {
        List<Plan> out = new ArrayList<>(list.size());
        for (Plan p: list) {
            out.add(p.clone());
        }
        return out;
    }

    public PlanContainer clone() {
        List<Plan> new_players = clonePlanListShallow(player_plans);
        List<Plan> new_enemies = clonePlanListShallow(enemy_plans);
        return new PlanContainer(new_players, new_enemies, player);
    }

    public JsonObject toJson() {
        JsonObject jo = new JsonObject();
        JsonArray player_array = new JsonArray();
        JsonArray enemy_array = new JsonArray();
        for (int i=0; i< size(); i++) {
            player_array.add(player_plans.get(i).toJson());
            enemy_array.add(enemy_plans.get(i).toJson());
        }
        jo.add("player_plans", player_array);
        jo.add("enemy_plans", enemy_array);
        jo.add("player", player);
        return jo;
    }

    public void adjustPlayer(int new_player) {
        if (new_player != player) {
            player = new_player;
            List<Plan> temp = player_plans;
            player_plans = enemy_plans;
            enemy_plans = temp;
        }
    }

    public void trim() {
        player_plans.forEach(Plan::trim);
        enemy_plans.forEach(Plan::trim);
    }
}
