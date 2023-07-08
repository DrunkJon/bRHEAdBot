package main.RHEA;

import ai.abstraction.WorkerRush;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.GreedyPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import main.BetterAbstraction.BetterAbstractionAI;
import main.BetterGameState;
import main.DynamicRush;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitTypeTable;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class bRHEAdBot extends BetterAbstractionAI {
    public boolean DEBUG = false;

    public Random r = new Random();
    Plan cur_best_player;
    Plan cur_best_enemy;
    int frame_num = 0;

    int iter_count = 0;
    double hardcoded_rate = 0.5;
    static int POP_SIZE = 40;
    PlanContainer cur_pop = null;
    PlanContainer next_pop =null;

    private SelectionRunner selectionRunner;

    private double runtime_buffer = 0.;
    private double timout_fraction = 0.9;

    private long call_start_time;

    private boolean final_rush = false;

    public String readWriteFolder = null;

    public bRHEAdBot(UnitTypeTable utt) {
        this(utt, false);
    }
    public bRHEAdBot(UnitTypeTable utt, boolean debug) {
        super(new GreedyPathFinding(), utt);
        this.DEBUG = debug;
        this.selectionRunner = new SelectionRunner(debug, utt);
    }

    public double getTimeInCall() {
        return (System.currentTimeMillis() - call_start_time);
    }

    @Override
    public PlayerAction getAction(int player, GameState gs) throws Exception {
        call_start_time = System.currentTimeMillis();
        if (final_rush) {
            return fullRush(player, gs);
        }
        runtime_buffer = Math.min(TIME_BUDGET, runtime_buffer + TIME_BUDGET);
        BetterGameState bgs = new BetterGameState(gs);
        PhysicalGameState pgs = bgs.getPhysicalGameState();

        if (cur_pop == null) {
            setupGlobals(pgs);
            if (readWriteFolder != null) {
                FileReader reader = new FileReader(readWriteFolder + "/start_pop.json");
                JsonObject start_pop_json = Json.parse(reader).asObject();
                cur_pop = new PlanContainer(start_pop_json, utt);
                reader.close();
                cur_best_player = cur_pop.getBestPlayer();
                cur_best_enemy = cur_pop.getBestEnemy();
                if (POP_SIZE >= 10) {
                    cur_pop.add(cur_pop.mutate(POP_SIZE - 10, bgs));
                } else {
                    cur_pop = cur_pop.reduceToBestN(POP_SIZE);
                }
                cur_pop.trim();
            } else {
                cur_pop = PlanContainer.startPop(player, POP_SIZE, bgs);
                cur_best_player = cur_pop.player_plans.get(0);
                cur_best_enemy = cur_pop.enemy_plans.get(0);
            }
        }

        // MAIN LOOP
        while (true) {
            if (getTimeInCall() >= runtime_buffer * 0.85) break;
            if (!selectionRunner.running) {
                iter_count++;
                BetterGameState simul_bgs = predictNextGameState(player, bgs);
                next_pop = cur_pop.clone();
                // PlanContainer randoms = new PlanContainer(player, POP_SIZE , simul_bgs);
                PlanContainer kids = next_pop.crossoverAll(POP_SIZE / 4, simul_bgs);
                PlanContainer mutants = next_pop.mutate(POP_SIZE / 4, simul_bgs);
                // cur_pop.add(randoms);
                next_pop.add(kids);
                next_pop.add(mutants);
                try {
                    cur_pop = selectionRunner.startRun(next_pop, POP_SIZE, simul_bgs,
                            call_start_time + (long) Math.floor(runtime_buffer * timout_fraction),
                            r.nextDouble() <= hardcoded_rate);
                } catch (TimeoutException e) {
                    break;
                }
            } else {
                try {
                    cur_pop = selectionRunner.continueRun(call_start_time + (long) Math.floor(runtime_buffer * timout_fraction));
                } catch (TimeoutException e) {
                    break;
                }
            }
        }

        if (getTimeInCall() > runtime_buffer) {
            if (timout_fraction > 0.75) timout_fraction -= 0.05;
            if (DEBUG) {
                double time = getTimeInCall();
                double budget = runtime_buffer;
                System.out.printf("TIME %f | %f BUFFER\n", time, budget);
            }
        }



        frame_num++;
        if (frame_num % Plan.step_frames == 0) {
            cur_best_player = cur_pop.getBestPlayer();
            cur_best_enemy = cur_pop.getBestEnemy();
            if (cur_best_player.times_scored == 0) {
                System.out.println("???");
            }
            if (DEBUG) {
                System.out.printf("iterations in frame %d: %d\n", frame_num, iter_count);
                System.out.printf("Best Player: %f (%d)\n", this.cur_best_player.avg_score, this.cur_best_player.times_scored);
                System.out.printf("Best Enemy: %f (%d)\n", this.cur_best_enemy.avg_score, this.cur_best_enemy.times_scored);
                /*for (Long id: cur_best_player.action_map.keySet()) {
                    Unit u = bgs.getUnit(id);
                    PlanAction action = cur_best_player.action_map.get(id).get(0);
                    System.out.printf("%s [%d]: %s\n", (u != null)? u.getType().name : "X _ X", id, (action != null)? action.toString() : "O _ O\"");
                }*/
                System.out.println();
            }
            cur_pop.next_step(bgs);
            if (frame_num >= 3000 && EvaluationAI.econScore(player, bgs) > 2 * EvaluationAI.econScore(1-player, bgs)) {
                final_rush = true;
            }
        }

        cur_best_player.assignActions(bgs.getPlayerUnits(player), this, player, bgs);
        PlayerAction out = translateActions(player, gs);

        runtime_buffer -= getTimeInCall();
        return out;
    }

    public PlayerAction fullRush(int player, GameState gs) {
        if (!(pf instanceof AStarPathFinding)) {
            pf = new AStarPathFinding();
        }
        BetterGameState bgs = new BetterGameState(gs);
        for (Unit u: bgs.getPlayerUnits(player)) {
            switch (u.getType().name) {
                case "Worker":
                case "Light":
                case "Heavy":
                case "Ranged":
                    attack_closest(u, bgs);
                    break;
                case "Base":
                    train(u, utt.getUnitType("Worker"));
                    break;
                case "Barracks":
                    train(u, utt.getUnitType("Ranged"));
                    break;
                default:
                    idle(u);
                    break;
            }
        }
        return translateActions(player, gs);
    }

    public void setupGlobals(PhysicalGameState pgs) {
        int map_size = pgs.getWidth() * pgs.getHeight();
        if (map_size <= 8*8) {
            Plan.lookahead_steps = 25;
            EvaluationAI.worker_worth = 1.5f;
            BuildingAction.worker_chance = 0.5f;
            EvaluationAI.end_falloff = 0.7f;
            EvaluationAI.econ_scale = 2f;
            hardcoded_rate = 0.7;
        } else if (map_size <= 16*16) {
            Plan.lookahead_steps = 30;
            EvaluationAI.worker_worth = 1.f;
            BuildingAction.worker_chance = 0.2f;
            EvaluationAI.end_falloff = 0.8f;
            EvaluationAI.econ_scale = 1.75f;
            hardcoded_rate = 0.5;
        } else if (map_size < 64 * 64) {
            Plan.lookahead_steps = 40;
            BuildingAction.worker_chance = 0.2f;
            EvaluationAI.end_falloff = 0.8f;
            EvaluationAI.econ_scale = 1.5f;
            hardcoded_rate = 0.3;
        } else {
            Plan.lookahead_steps = 50;
            BuildingAction.worker_chance = 0.2f;
            EvaluationAI.end_falloff = 0.8f;
            EvaluationAI.econ_scale = 1.5f;
            hardcoded_rate = 0.2;
        }
    }

    public Plan getBestEnemy() {
        return cur_best_enemy;
    }

    public AI createEnemy() {
        return new EnemyAI();
    }

    public class EnemyAI extends BetterAbstractionAI {

        public EnemyAI() {
            super(new GreedyPathFinding(), bRHEAdBot.this.utt);
        }

        @Override
        public PlayerAction getAction(int player, GameState gs) throws Exception {
            BetterGameState bgs = new BetterGameState(gs);
            bRHEAdBot.this.getBestEnemy().assignActions(bgs.getPlayerUnits(player), this, player, bgs);
            return translateActions(player, gs);
        }

        @Override
        public AI clone() {
            return null;
        }

        @Override
        public List<ParameterSpecification> getParameters() {
            return null;
        }
    }

    private BetterGameState predictNextGameState(int player, BetterGameState bgs) throws Exception {
        BetterGameState simul_bgs = bgs.clone();
        EvaluationAI ai1 = new EvaluationAI(new GreedyPathFinding(), utt, cur_best_player);
        // EvaluationAI ai2 = new EvaluationAI(new GreedyPathFinding(), utt, cur_best_enemy);
        AI ai2 = new DynamicRush(utt, new GreedyPathFinding());
        int simul_frame = frame_num;
        boolean gameover = false;
        while (!(simul_frame % Plan.step_frames == 0) && !gameover) {
            simul_bgs.issue(ai1.getAction(player, simul_bgs));
            simul_bgs.issue(ai2.getAction(1-player, simul_bgs));
            gameover = simul_bgs.cycle();
            simul_frame++;
        }
        return simul_bgs;
    }

    /*
    public PlanContainer environmentSelection(PlanContainer container, BetterGameState bgs, int slice_index, int slice_length) throws Exception {
        // might want to copy lists first
        container.shuffle();
        assert container.size() % 2 ==0;
        PlanContainer out_container = new PlanContainer(container.player);

        List<Future<PlanContainer>> results = new ArrayList<>();

        for (int i = slice_index; i < slice_index + slice_length; i++ ){
            PlanContainer selection_container = container.subContainer(i, 1);
            Callable<PlanContainer> call_me = new SelectionCaller(selection_container, bgs, utt);
            Future<PlanContainer> future = executor.submit(call_me);
            results.add(future);
        }
        for (Future<PlanContainer> future_result: results) {
            PlanContainer new_container = future_result.get();
            out_container.add(new_container);
        }
        return out_container;
    }

    public class SelectionCaller implements Callable<PlanContainer> {
        PlanContainer container;
        BetterGameState bgs;
        UnitTypeTable utt;
        public SelectionCaller(PlanContainer container, BetterGameState bgs, UnitTypeTable utt) {
            this.container = container;
            this.bgs = bgs;
            this.utt = utt;
        }
        @Override
        public PlanContainer call() throws Exception {
            return simpleMatch(container, bgs);
        }

        public PlanContainer simpleMatch(PlanContainer container, BetterGameState bgs) throws Exception {
            BetterGameState bgs2 = bgs.clone();
            EvaluationAI ai1 = new EvaluationAI(new GreedyPathFinding(), this.utt, container.player_plans.get(0));
            AI ai2;
            if (frame_num % 2 == 0) {
                ai2 = new EvaluationAI(new GreedyPathFinding(), this.utt, container.enemy_plans.get(0));
            } else {
                ai2 = new WorkerRush(utt, new GreedyPathFinding());
            }
            // RandomBiasedAI ai2 = new RandomBiasedAI(this.utt);
            boolean gameover = false;
            while(!gameover && !ai1.timeout()) {
                bgs2.issue(ai1.getAction(container.player, bgs2));
                bgs2.issue(ai2.getAction(1-container.player, bgs2));
                gameover = bgs2.cycle();
            }
            ai1.scorePlan(container.player,bgs2);
            if (frame_num % 2 == 0) {
                assert ai2 instanceof EvaluationAI;
                ((EvaluationAI) ai2).scorePlan(1-container.player,bgs2);
            }
            return container;
        }

        public PlanContainer selectionTournament(PlanContainer container, BetterGameState bgs) throws Exception {
            assert container.size() % 2 ==0;
            HashMap<Plan, Float> player_scores = new HashMap<>();
            HashMap<Plan, Float> enemy_scores = new HashMap<>();
            for (Plan pp: container.player_plans) {
                player_scores.put(pp, 0.f);
                for (Plan ep: container.enemy_plans) {
                    if (!enemy_scores.containsKey(ep)) enemy_scores.put(ep, 0.f);
                    float p_score = simulatePlans(container.player, pp, ep, bgs);
                    player_scores.put(pp, player_scores.get(pp) + p_score);
                    player_scores.put(pp, player_scores.get(pp) + p_score);
                }
            }
            container.player_plans.sort(Comparator.comparing(player_scores::get));
            container.enemy_plans.sort(Comparator.comparing(enemy_scores::get));
            return new PlanContainer(
                    container.player_plans.subList(0, container.player_plans.size() / 2),
                    container.enemy_plans.subList(0, container.enemy_plans.size() / 2),
                    container.player
            );
        }

        public float simulatePlans(int player, Plan plan1, Plan plan2, BetterGameState bgs) throws Exception {
            // returned score is for plan1 (player)
            // score for plan2 (enemy) is -score
            BetterGameState bgs2 = bgs.clone();
            EvaluationAI ai1 = new EvaluationAI(new GreedyPathFinding(), this.utt, plan1);
            EvaluationAI ai2 = new EvaluationAI(new GreedyPathFinding(), this.utt, plan2);
            boolean gameover = false;
            while(!gameover && !ai1.timeout()) {
                bgs2.issue(ai1.getAction(player, bgs2));
                bgs2.issue(ai2.getAction(1-player, bgs2));
                gameover = bgs2.cycle();
            }
            float score = EvaluationAI.scoreGameState(player, bgs2);
            plan1.add_score(score);
            plan2.add_score(-score);
            return score;
        }

        public PlanContainer passiveSelection(PlanContainer container, BetterGameState bgs) throws Exception {
            assert container.size() % 2 ==0;
            HashMap<Plan, Float> player_scores = new HashMap<>();
            for (Plan pp: container.player_plans) {
                player_scores.put(pp, 0.f);
                float p_score = simulateAgainstPassive(container.player, pp, bgs);
                player_scores.put(pp, player_scores.get(pp) + p_score);
            }
            container.player_plans.sort(Comparator.comparing(player_scores::get));
            return new PlanContainer(
                    container.player_plans.subList(0, container.player_plans.size() / 2),
                    container.enemy_plans.subList(0, container.enemy_plans.size() / 2),
                    container.player
            );
        }

        public float simulateAgainstPassive(int player, Plan plan, BetterGameState bgs) throws Exception {
            BetterGameState bgs2 = bgs.clone();
            EvaluationAI ai1 = new EvaluationAI(new GreedyPathFinding(), this.utt, plan);
            PassiveAI ai2 = new PassiveAI(this.utt);
            boolean gameover = false;
            while(!gameover && !ai1.timeout()) {
                bgs2.issue(ai1.getAction(player, bgs2));
                bgs2.issue(ai2.getAction(1-player, bgs2));
                gameover = bgs2.cycle();
            }
            float score = EvaluationAI.scoreGameState(player, bgs2);
            plan.add_score(score);
            return score;
        }
    }
     */

    @Override
    public AI clone() {
        return new bRHEAdBot(this.utt);
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        return null;
    }

    @Override
    public String toString() {
        return "bRHEAdBot";
    }
    /*
    public void preGameAnalysis(GameState gs, long milliseconds, String readWriteFolder) throws Exception
    {
        // SETUP
        this.readWriteFolder = readWriteFolder;
        long start_time = System.currentTimeMillis();
        PhysicalGameState pgs = gs.getPhysicalGameState();
        BetterGameState bgs = new BetterGameState(gs);
        setupGlobals(pgs);
        Plan.lookahead_steps = 150;
        // EvaluationAI.score_falloff = 0.97f;
        // EvaluationAI.end_falloff = 0.96f;
        int pregame_POP_SIZE = 80;
        double avg_loop_time = 0;
        int loop_times = 0;
        frame_num = 0;

        // INIT POP
        PlanContainer pop = PlanContainer.startPop(0, pregame_POP_SIZE, bgs);

        // MAIN LOOP
        while (System.currentTimeMillis() - start_time + avg_loop_time < 0.8 * milliseconds) {
            long loop_start = System.currentTimeMillis();

            PlanContainer kids = pop.crossover(pregame_POP_SIZE / 8, bgs);
            PlanContainer mutants = pop.mutate(pregame_POP_SIZE / 8, bgs);
            pop.add(kids);
            pop.add(mutants);

            pop = selectionRunner.startRun(pop, pregame_POP_SIZE, bgs, (long) (start_time + milliseconds), true);
            System.out.printf("best score: %f (%d)\n", pop.getBestPlayer().avg_score, pop.getBestPlayer().times_scored);
            System.out.printf("best enemy: %f (%d)\n", pop.getBestEnemy().avg_score, pop.getBestEnemy().times_scored);

            loop_times++;
            long loop_time = System.currentTimeMillis();
            avg_loop_time += (loop_time - loop_start) / (double) loop_times;
            if (DEBUG) System.out.printf("iteration #%d in %dms (%dms total)\n", loop_times, loop_time - loop_start, loop_time - start_time);
        }

        System.out.printf("finished main loop\n");

        // SAVE RESULTS
        JsonObject json_pop = pop.reduceToBestN(10).toJson();
        try {
            System.out.printf("writing\n");
            FileWriter file_writer = new FileWriter(readWriteFolder + "/start_pop.json");
            json_pop.writeTo(file_writer);
            file_writer.close();
        } catch (IOException e) {
            System.out.println("IO EXCEPTION FML ;_;");
        }

        System.out.printf("finished writing\n");

        // RESET GLOBALS
        EvaluationAI.score_falloff = 0.95f;
        EvaluationAI.end_falloff = 0.9f;
    }
    */
}
