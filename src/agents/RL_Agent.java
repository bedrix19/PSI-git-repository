package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;
import java.util.HashMap;
import java.util.Vector;

public class RL_Agent extends Agent {
    private State state;
    private AID mainAgent;
    private int myId, opponentId;
    /**
     * N = Number of players
     * R = Rounds
     * F = Fee
     * P = Payoff
     * A = Assets
     * S = Score
     * I = Inflation
     **/
    private int N, R, S, I;
    private double F, A, P;
    private ACLMessage msg;

    // Learning Q-Learning variables
    private LearningTools learningTools;
    private String currentState;

    // Learning Automata variables
    private double previousP = 0;
    private double previousA = 0;
    private double previousIndex = 0;
    
    private enum State {
        s0NoConfig, s1AwaitingGame, s2Round, s3AwaitingResult
    }

    @Override
    protected void setup() {

        //clean the .out
        clearLog();

        state = State.s0NoConfig;
        learningTools = new LearningTools();
        
        // Register in yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Player");
        sd.setName("Game");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new Play());
        writeLog("Is ready.");
    }

    private class Play extends CyclicBehaviour {
        @Override
        public void action() {
            msg = blockingReceive();
            if (msg != null) {
                writeLog(state.name());
                writeLog("Recibio: " + msg.getContent());
                
                switch (state) {
                    case s0NoConfig:
                        if (msg.getContent().startsWith("Id") && msg.getPerformative() == ACLMessage.INFORM) {
                            try {
                                if (validateSetupMessage(msg)) {
                                    writeLog("Is up with ID:" + myId);
                                    state = State.s1AwaitingGame;
                                }
                            } catch (NumberFormatException e) {
                                writeLog(state.name() + " - Bad message:\n\t" + msg.getContent());
                            }
                            // Reset parameters
                            P = 0.0;
                            A = 0.0;
                            learningTools = new LearningTools();
                            previousP = 0.0;
                            previousA = 0.0;
                            previousIndex = 0.0;
                            currentState = ""; 
                        } else if (msg.getContent().equals("Removed") && msg.getPerformative() == ACLMessage.INFORM) {
                            writeLog("Removed");
                            doDelete();
                        } else {
                            writeLog(state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        }
                        break;

                    case s1AwaitingGame:
                        if (msg.getContent().startsWith("NewGame") && msg.getPerformative() == ACLMessage.INFORM) {
                            boolean gameStarted = false;
                            try {
                                gameStarted = validateNewGame(msg.getContent());
                            } catch (NumberFormatException e) {
                                writeLog(state.name() + " - Bad message:\n\t" + msg.getContent());
                            }
                            if (gameStarted) {
                                currentState = "Game" + opponentId;
                                state = State.s2Round;
                            }
                        } else if (msg.getContent().startsWith("RoundOver") && msg.getPerformative() == ACLMessage.REQUEST) {
                            processRoundOver(msg.getContent());
                            
                            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                            response.addReceiver(mainAgent);
                            String decision = decideTransaction(Double.parseDouble(msg.getContent().split("#")[6]));
                            response.setContent(decision);
                            send(response);
                            writeLog("Decided: " + decision);
                        } else if (msg.getContent().startsWith("Accounting") && msg.getPerformative() == ACLMessage.INFORM) {
                            processAccounting(msg.getContent());
                        } else if (msg.getContent().startsWith("GameOver") && msg.getPerformative() == ACLMessage.INFORM) {
                            writeLog("Total payoff: " + msg.getContent().split("#")[2]);
                            state = State.s0NoConfig;
                        } else writeLog(state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        break;

                    case s2Round:
                        if (msg.getContent().startsWith("Action") && msg.getPerformative() == ACLMessage.REQUEST) {
                            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                            response.addReceiver(mainAgent);
                            String action = chooseAction();
                            response.setContent("Action#" + action);
                            writeLog(getAID().getName() + " sent " + response.getContent());
                            send(response);
                            state = State.s3AwaitingResult;
                        } else writeLog(state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        break;

                    case s3AwaitingResult:
                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Results")) {
                            processResults(msg.getContent());
                            state = State.s1AwaitingGame;
                        } else writeLog(state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        break;
                }
            }
        }

        /**
         * Validates and extracts the parameters from the setup message
         *
         * @param msg ACLMessage to process
         * @return true on success, false on failure
         */
        private boolean validateSetupMessage(ACLMessage msg) throws NumberFormatException {
            /**
             * Id#ID#N,R,F
             */
            int tN, tR, tMyId;
            double tF;
            String msgContent = msg.getContent();

            String[] contentSplit = msgContent.split("#");
            if ((contentSplit.length != 3) && (!contentSplit[0].equals("Id"))) return false;
            tMyId = Integer.parseInt(contentSplit[1]);

            String[] parametersSplit = contentSplit[2].split(",");
            if (parametersSplit.length != 3) return false;
            tN = Integer.parseInt(parametersSplit[0]);
            tR = Integer.parseInt(parametersSplit[1]);
            tF = Double.parseDouble(parametersSplit[2]);

            //At this point everything should be fine, updating class variables
            mainAgent = msg.getSender();
            N = tN;
            R = tR;
            F = tF;
            myId = tMyId;
            return true;
        }

        /**
         * Processes the contents of the New Game message
         * @param msgContent Content of the message
         * @return true if the message is valid
         */
        public boolean validateNewGame(String msgContent) {
            // NewGame#Id1#Id2
            int msgId0, msgId1;
            String[] contentSplit = msgContent.split("#");
            if (contentSplit.length != 3) return false;
            if (!contentSplit[0].equals("NewGame")) return false;
            msgId0 = Integer.parseInt(contentSplit[1]);
            msgId1 = Integer.parseInt(contentSplit[2]);
            if (myId == msgId0) {
                opponentId = msgId1;
                return true;
            } else if (myId == msgId1) {
                opponentId = msgId0;
                return true;
            }
            return false;
        }

        private void processRoundOver(String content) {
            // Format: "RoundOver#id#roundPayoff#accumulatedPayoff#inflation#assets#index"
            String[] parts = content.split("#");
            if (parts.length == 7) {
                P = Double.parseDouble(parts[3]);
                A = Double.parseDouble(parts[5]);
                writeLog("Round over - Payoff: " + P + ", Assets: " + A);
            }
        }

        private void processAccounting(String content) {
            // Format: "Accounting#id#payoff#assets"
            String[] parts = content.split("#");
            if (parts.length == 4) {
                P = Double.parseDouble(parts[2]);
                A = Double.parseDouble(parts[3]);
                writeLog("Updated accounting - Payoff: " + P + ", Assets: " + A);
            }
        }

        private void processResults(String content) {
            String[] parts = content.split("#");
            String[] idmsg = parts[1].split(",");
            String[] payoffs = parts[3].split(",");
            
            int myPayoff;
            if(myId == Integer.parseInt(idmsg[0])) {
                myPayoff = Integer.parseInt(payoffs[0]);
            } else {
                myPayoff = Integer.parseInt(payoffs[1]);
            }
            P += myPayoff;
            
            // Update Q-values with the received payoff
            learningTools.vGetNewActionQLearning(currentState, 2, myPayoff);
        }

        private String chooseAction() {
            // Use Q-Learning to choose between C and D
            learningTools.vGetNewActionQLearning(currentState, 2, P); // Use accumulated payoff as reward
            int action = learningTools.iNewAction2Play;
            return action == 0 ? "C" : "D";
        }

        private String decideTransaction(double indexValue) {
            learningTools.setIndexValue(indexValue);  // Set current index value
            String transactionState = String.format("T%.2f_P%.2f_A%.2f", indexValue, P, A); // Create a more informative state representation
            learningTools.vGetNewActionAutomata(transactionState, 3, calculateReward(indexValue)); // Use Learning Automata for transaction decisions (3 actions: buy, sell, none)
            
            int decision = learningTools.iNewAction2Play;
            StringBuilder result = new StringBuilder();
            
            switch(decision) {
                case 0: // Buy
                    if (P > 0) {
                        double maxAffordable = P / (indexValue * (1 + F));
                        if (maxAffordable >= 1) {
                            // Calculate buy amount based on learning
                            double confidence = learningTools.oPresentStateAction.dGetQAction(0);
                            int buyAmount = Math.max(1, (int)(maxAffordable * confidence));
                            result.append("Buy#").append(buyAmount);
                        }
                    }
                    break;
                    
                case 1: // Sell
                    if (A > 0) {
                        // Calculate sell amount based on learning
                        double confidence = learningTools.oPresentStateAction.dGetQAction(1);
                        double sellAmount = Math.min(A, A * confidence);
                        if (sellAmount >= 1.0) {
                            result.append("Sell#").append(String.format("%.2f", sellAmount));
                        }
                    }
                    break;
                    
                case 2: // None
                    return "None";
            }
            
            writeLog("Transaction: " + result.toString());
            return result.length() == 0 ? "None" : result.toString();
        }

        private double calculateReward(double indexValue) {
            // Calculate reward based on multiple factors
            double assetValue = A * indexValue;
            double totalValue = P + assetValue;
            double previousTotalValue = previousP + previousA * previousIndex;
            
            // Store current values for next iteration
            previousP = P;
            previousA = A;
            previousIndex = indexValue;
            
            // Return relative change in total value
            return previousTotalValue > 0 ? (totalValue - previousTotalValue) / previousTotalValue : 0.0;
        }
    }

    private class StateAction {
        private HashMap<String, double[]> hmQValues;  // Q-values for each state-action
        private HashMap<String, int[]> hmVisits;      // Number of visits for each state-action
        private double[] dQVal;                       // Q-values for current state
        private int[] iVisits;                        // Visits for current state
        String sState;
        double[] dValAction;
        double[] dQAction;
        
        public StateAction() {
            hmQValues = new HashMap<>();
            hmVisits = new HashMap<>();
        }

        public StateAction(String sAuxState, int iNActions) {
            sState = sAuxState;
            dValAction = new double[iNActions];
            dQAction = new double[iNActions];
        }

        public StateAction(String sAuxState, int iNActions, boolean bLA) {
            this(sAuxState, iNActions);
            if (bLA) {
                for (int i = 0; i < iNActions; i++) {
                    dValAction[i] = 1.0 / iNActions;
                }
            }
        }
        
        public void vInitialize(String sState, int iActions) {
            dQVal = new double[iActions];
            iVisits = new int[iActions];
            for (int i = 0; i < iActions; i++) {
                dQVal[i] = 0.0;
                iVisits[i] = 0;
            }
            hmQValues.put(sState, dQVal);
            hmVisits.put(sState, iVisits);
        }
        
        public boolean bIsState(String sState) {
            return hmQValues.containsKey(sState);
        }
        
        public double[] getQValues(String sState) {
            return hmQValues.get(sState);
        }
        
        public int[] getVisits(String sState) {
            return hmVisits.get(sState);
        }

        public double dGetQAction(int i) {
            return dQAction[i];
        }
        
        public void updateQValue(String sState, int iAction, double dValue) {
            double[] qValues = hmQValues.get(sState);
            qValues[iAction] = dValue;
            hmQValues.put(sState, qValues);
        }
        
        public void incrementVisits(String sState, int iAction) {
            int[] visits = hmVisits.get(sState);
            visits[iAction]++;
            hmVisits.put(sState, visits);
        }
    }

    private class LearningTools {
        private final double dDecFactorLR = 0.99;   // Learning rate decay
        private final double dEpsilon = 0.95;       // Exploration rate
        private final double dMINLearnRate = 0.05;  // Minimum learning rate
        private double dLearnRate = 1.0;
        private double dGamma = 0.9;

        private boolean bAllActions = false;
        private int iNewAction2Play;
        private int iLastAction;
        private StateAction oPresentStateAction;
        private StateAction oLastStateAction;
        private Vector<StateAction> oVStateActions;
        private Random random;
        private StateAction stateAction;
        private double indexValue;
        
        public LearningTools() {
            random = new Random();
            stateAction = new StateAction();
            oVStateActions = new Vector<>();
        }
        
        public void vGetNewActionQLearning(String sState, int iNActions, double dReward) {
            if (!stateAction.bIsState(sState)) stateAction.vInitialize(sState, iNActions);
            
            double[] qValues = stateAction.getQValues(sState);
            int[] visits = stateAction.getVisits(sState);
            
            // Exploration vs Exploitation
            if (random.nextDouble() > dEpsilon) iNewAction2Play = random.nextInt(iNActions); // Exploration: choose random action
            else iNewAction2Play = getBestAction(qValues); // Exploitation: choose best action
            
            // Update Q-value for the chosen action
            double learningRate = Math.max(dMINLearnRate, 1.0 / (1 + visits[iNewAction2Play]));
            double oldValue = qValues[iNewAction2Play];
            double newValue = oldValue + learningRate * (dReward - oldValue);
            
            stateAction.updateQValue(sState, iNewAction2Play, newValue);
            stateAction.incrementVisits(sState, iNewAction2Play);
        }
        
        public void vGetNewActionAutomata (String sState, int iNActions, double dReward) {
            boolean bFound = false;       // Searching if we already have the state

            // Search for existing state
            for (StateAction oStateProbs : oVStateActions) {
                if (oStateProbs.sState.equals(sState)) {
                    oPresentStateAction = oStateProbs;
                    bFound = true;
                    break;
                }
            }

            // If we didn't find it, then we add it                                                                    
            if (!bFound) {
                oPresentStateAction = new StateAction (sState, iNActions, true);
                oVStateActions.add (oPresentStateAction);
            }

            // Adjusting Probabilities 
            if (oLastStateAction != null && dReward > 0) {                  // If reward grows and the previous action was allowed --> reinforce last action
                for (int i=0; i<iNActions; i++)
                    if (i == iLastAction) oLastStateAction.dValAction[i] += dLearnRate * (1.0 - oLastStateAction.dValAction[i]); // Reinforce the last action
                    else oLastStateAction.dValAction[i] *= (1.0 - dLearnRate);  // The rest are weakened
            }
            
            double dValAcc = 0;       // Generating the new action based on probabilities
            double dValRandom = Math.random();
            for (int i=0; i<iNActions; i++) {
                dValAcc += oPresentStateAction.dValAction[i];
                if (dValRandom < dValAcc) {
                    iNewAction2Play = i;
                    break;
                }
            }

            oLastStateAction = oPresentStateAction;   // Updating values for the next time
            dLearnRate *= dDecFactorLR;     // Reducing the learning rate
            if (dLearnRate < dMINLearnRate) dLearnRate = dMINLearnRate;
        }

        public void setIndexValue(double value) {
            this.indexValue = value;
        }
        private int getBestAction(double[] qValues) {
            int bestAction = 0;
            double bestValue = qValues[0];
            
            for (int i = 1; i < qValues.length; i++) {
                if (qValues[i] > bestValue) {
                    bestValue = qValues[i];
                    bestAction = i;
                }
            }
            return bestAction;
        }
    }

    private void clearLog() {
        try (FileWriter fw = new FileWriter("RL_AgentsLog.out", false)) {
            // Opening with false overwrites the file
            fw.write("");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeLog(String log) {
		FileWriter fichero = null;
		PrintWriter pw = null;
		try {
			fichero = new FileWriter("RL_AgentsLog.out", true);
			pw = new PrintWriter(fichero);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				pw.write(getAID().getName() + " : " + log + "\n");
				if (null != fichero) fichero.close();
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
	}
}
