package main.RHEA;

import ai.abstraction.WorkerRush;
import ai.abstraction.pathfinding.GreedyPathFinding;
import ai.core.AI;
import main.BetterGameState;
import rts.units.UnitTypeTable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class SelectionRunner {
    private boolean DEBUG;
    private final int threads;
    private final ExecutorService executor;

    private final UnitTypeTable utt;
    private boolean use_enemy;
    private PlanContainer out_pop = null;

    private List<Simulator> simulators = new ArrayList<>();
    private int finished_calls = 0;
    private int next_to_unpause = 0;
    private List<Future<PlanContainer>> results = new ArrayList<>();

    public boolean running = false;
    public int selection_size;

    public SelectionRunner(boolean debug, UnitTypeTable utt) {
        this.utt = utt;
        this.DEBUG = debug;
        this.threads = Runtime.getRuntime().availableProcessors() - (DEBUG? 1:0);
        this.executor = Executors.newFixedThreadPool(threads);
    }

    public PlanContainer startRun(PlanContainer old_pop, int selection_size, BetterGameState bgs, long timeout, boolean use_enemy)
            throws TimeoutException, ExecutionException, InterruptedException {
        old_pop.shuffle();
        for (int i = 0; i < old_pop.size(); i++) {
            PlanContainer pair = old_pop.subContainer(i, 1);
            Simulator sim = new Simulator(pair, bgs, use_enemy, false);
            this.simulators.add(sim);
            this.results.add(executor.submit(sim));
        }
        this.selection_size = selection_size;
        this.out_pop = new PlanContainer(old_pop.player);
        this.finished_calls = 0;
        this.next_to_unpause = threads;
        this.running = true;
        return continueRun(timeout);
    }

    public PlanContainer continueRun(long timeout) throws TimeoutException, ExecutionException, InterruptedException {
        //next_to_unpause = Math.max(finished_calls + threads, next_to_unpause);
        for (Simulator sim: simulators.subList(finished_calls, simulators.size())) {
            sim.unpause();
        }
        for (int i = finished_calls; i < results.size(); i++) {
            try {
                out_pop.add(results.get(i).get(timeout - System.currentTimeMillis(), TimeUnit.MILLISECONDS));
                this.finished_calls = i+1;
            } catch (TimeoutException e) {
                for (Simulator sim: simulators.subList(i, simulators.size())) {
                    sim.pause();
                }
                throw e;
            }
        }
        this.running = false;
        simulators = new ArrayList<>();
        results = new ArrayList<>();
        return out_pop.reduceToBestN(selection_size);
    }

    public void tryUnpauseNext() {
        if (next_to_unpause < simulators.size()){
            simulators.get(next_to_unpause).unpause();
            next_to_unpause ++;
        }

    }

    public class Simulator implements Callable<PlanContainer> {
        PlanContainer container;
        BetterGameState bgs;
        boolean use_enemy;
        private boolean paused;
        //private final SelectionRunner parent;
        public Simulator(PlanContainer container, BetterGameState bgs, boolean use_enemy, boolean paused) {
            //this.parent = parent;
            this.container = container;
            this.bgs = bgs.clone();
            this.use_enemy = use_enemy;
            this.paused = paused;
        }

        public void pause() {
            this.paused = true;
        }

        public void unpause() {
            this.paused = false;
        }

        public void waitForUnpause() throws InterruptedException {
            while(paused) {
                TimeUnit.MILLISECONDS.sleep(1);
            }
        }

        @Override
        public PlanContainer call() throws Exception {
            EvaluationAI ai1 = new EvaluationAI(new GreedyPathFinding(), utt, container.player_plans.get(0));
            AI ai2;
            if (use_enemy) {
                ai2 = new EvaluationAI(new GreedyPathFinding(), utt, container.enemy_plans.get(0));
            } else {
                ai2 = new WorkerRush(utt, new GreedyPathFinding());
            }
            boolean gameover = false;
            while(!gameover && !ai1.timeout()) {
                if (paused) {
                    waitForUnpause();
                }
                bgs.issue(ai1.getAction(container.player, bgs));
                bgs.issue(ai2.getAction(1-container.player, bgs));
                gameover = bgs.cycle();
            }
            ai1.scorePlan(container.player,bgs);
            if (use_enemy) {
                assert ai2 instanceof EvaluationAI;
                ((EvaluationAI) ai2).scorePlan(1-container.player,bgs);
            }
            //parent.tryUnpauseNext();
            return container;
        }
    }
}
