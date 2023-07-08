package main; /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import ai.PassiveAI;
import ai.RandomBiasedAI;
import ai.abstraction.LightRush;
import ai.abstraction.WorkerRush;
import ai.abstraction.pathfinding.GreedyPathFinding;
import ai.core.AI;
import ai.mcts.naivemcts.NaiveMCTS;
import ai.portfolio.PortfolioAI;
import com.eclipsesource.json.Json;
import gui.PhysicalGameStatePanel;
import main.RHEA.EvaluationAI;
import main.RHEA.PlanContainer;
import main.RHEA.bRHEAdBot;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;

import javax.swing.*;
import java.io.FileReader;

/**
 *
 * @author santi
 */
public class GameVisualSimulationTest {
    public static void main(String[] args) throws Exception {
        String[] maps = new String[] {
                //"maps/16x16/basesWorkers16x16A.xml",
                //"maps/8x8/basesWorkers8x8.xml",
                //"maps/BWDistantResources32x32.xml",
                //"maps/BroodWar/(4)BloodBath.scmB.xml",
                //"maps/8x8/FourBasesWorkers8x8.xml",
                //"maps/16x16/TwoBasesBarracks16x16.xml",
                "maps/NoWhereToRun9x8.xml",
                //"maps/DoubleGame24x24.xml"
        };

        for (String map: maps) {
            UnitTypeTable utt = new UnitTypeTable(UnitTypeTable.VERSION_ORIGINAL_FINETUNED);
            PhysicalGameState pgs = PhysicalGameState.load(map, utt);

            GameState gs = new GameState(pgs, utt);
            int MAXCYCLES = 3500;
            int PERIOD = 20;
            boolean gameover = false;

            // AI ai1 = new WorkerRush(utt, new BFSPathFinding());
            bRHEAdBot ai1 = new bRHEAdBot(utt, true);
            // ai1.preGameAnalysis(gs, 1000 * 60 * 2, "C:\\Users\\User\\IdeaProjects\\MicorRTS_Bot\\MicroRTS\\src\\PregameFiles");
            AI ai2 = new NaiveMCTS(utt);

            JFrame w = PhysicalGameStatePanel.newVisualizer(gs,900,900,false,PhysicalGameStatePanel.COLORSCHEME_BLACK);

            long nextTimeToUpdate = System.currentTimeMillis() + PERIOD;
            do{
                if (System.currentTimeMillis()>=nextTimeToUpdate) {
                    PlayerAction pa1 = ai1.getAction(0, gs);
                    PlayerAction pa2 = ai2.getAction(1, gs);
                    gs.issueSafe(pa1);
                    gs.issueSafe(pa2);

                    // simulate:
                    gameover = gs.cycle();
                    w.repaint();
                    nextTimeToUpdate+=PERIOD;
                } else {
                    try {
                        Thread.sleep(1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }while(!gameover && gs.getTime()<MAXCYCLES);
            ai1.gameOver(gs.winner());
            ai2.gameOver(gs.winner());

            System.out.println("Game Over");
        }
    }    
}
