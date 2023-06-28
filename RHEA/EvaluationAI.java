package main.RHEA;

import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.GreedyPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import main.BetterAbstraction.BetterAbstractionAI;
import main.BetterGameState;
import rts.GameState;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitTypeTable;

import java.util.List;

public class EvaluationAI extends BetterAbstractionAI {
    public Plan plan;
    public int frame = 0;
    public int step = 0;
    public int cum_score = 0;
    public static float score_falloff = 0.95f;
    public static float econ_scale = 1.5f;

    public static float end_falloff = 0.9f;

    public static float worker_worth = 1.f;
    public EvaluationAI(GreedyPathFinding a_pf, UnitTypeTable utt, Plan plan) {
        super(a_pf, utt);
        this.plan = plan;
    }

    @Override
    public PlayerAction getAction(int player, GameState gs) throws Exception {
        if (frame < getTimeLimit()) {
            BetterGameState bgs = new BetterGameState(gs);
            plan.assignActions(bgs.getPlayerUnits(player), this, player, bgs, frame / Plan.step_frames);
            if (frame % Plan.step_frames == Plan.step_frames - 1) {
                cum_score += scoreGameState(player, gs) * Math.pow(score_falloff, step);
                step++;
            }
        }
        this.frame++;
        return translateActions(player, gs);
    }

    public static float scoreGameState(int player, BetterGameState bgs) {
        return (float) ( Math.pow(econScore(player, bgs), econ_scale) / (econScore(1-player, bgs) + 0.1));
    }

    public int getTimeLimit() {
        return Plan.getTimeLimit();
    }

    public static float scoreGameState(int player, GameState gs) {
        return scoreGameState(player, new BetterGameState(gs));
    }

    public static float econScore(int player, BetterGameState bgs) {
        List<Unit> units = bgs.getPlayerUnits(player);
        float score = 1.f * bgs.getPhysicalGameState().getPlayer(player).getResources();
        for (Unit u: units) {
            score += ((u.getType().name.equals("Worker"))? worker_worth : 3.) * u.getType().cost * ((float) u.getHitPoints() / u.getMaxHitPoints());
            score += 0.5 * u.getResources();
        }
        return score;
    }

    public void scorePlan(int player, BetterGameState bgs) {
        if (bgs.gameover()) {
            if (bgs.winner() == player) {
                plan.add_score(cum_score + 100000 * (float) Math.pow(end_falloff, frame / 10f));
            } else {
                plan.add_score(cum_score - 50000 * (float) Math.pow(end_falloff, frame / 10f));
            }
        } else {
            plan.add_score(cum_score);
        }
    }

    public boolean timeout() {
        return this.frame >= this.getTimeLimit();
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
