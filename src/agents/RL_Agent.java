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

public class RLAgent extends Agent {
    private State state;
    private AID mainAgent;
    private int myId, opponentId;
    private int N, R, S, I;
    private double F, A, P;
    private ACLMessage msg;
    private LearningTools learningTools;
    private String currentState;
    
    private enum State {
        s0NoConfig, s1AwaitingGame, s2Round, s3AwaitingResult
    }

    @Override
    protected void setup() {
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
        writeLog("RLAgent " + getAID().getName() + " is ready.");
    }

    private class Play extends CyclicBehaviour {
        @Override
        public void action() {
            msg = blockingReceive();
            if (msg != null) {
                switch (state) {
                    case s0NoConfig:
                        handleConfig();
                        break;
                    case s1AwaitingGame:
                        handleNewGame();
                        break;
                    case s2Round:
                        handleRound();
                        break;
                    case s3AwaitingResult:
                        handleResults();
                        break;
                }
            }
        }

        private void handleConfig() {
            if (msg.getContent().startsWith("Id") && msg.getPerformative() == ACLMessage.INFORM) {
                try {
                    if (validateSetupMessage(msg)) {
                        state = State.s1AwaitingGame;
                    }
                } catch (NumberFormatException e) {
                    writeLog(getAID().getName() + ": Bad configuration message");
                }
            }
        }

        private void handleNewGame() {
            if (msg.getContent().startsWith("NewGame") && msg.getPerformative() == ACLMessage.INFORM) {
                try {
                    if (validateNewGame(msg.getContent())) {
                        state = State.s2Round;
                        currentState = "Game" + opponentId; // State identifier for RL
                    }
                } catch (NumberFormatException e) {
                    writeLog(getAID().getName() + ": Bad new game message");
                }
            }
        }

        private void handleRound() {
            if (msg.getContent().startsWith("Action") && msg.getPerformative() == ACLMessage.REQUEST) {
                // Use Q-Learning to decide action
                String action = chooseAction();
                ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                response.addReceiver(mainAgent);
                response.setContent("Action#" + action);
                send(response);
                state = State.s3AwaitingResult;
            } else if (msg.getContent().startsWith("RoundOver")) {
                processRoundOver(msg.getContent());
                String decision = decideTransaction(Double.parseDouble(msg.getContent().split("#")[6]));
                ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                response.addReceiver(mainAgent);
                response.setContent(decision);
                send(response);
            }
        }

        private void handleResults() {
            if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Results")) {
                processResults(msg.getContent());
                state = State.s2Round;
            }
        }

        private String chooseAction() {
            // Use Q-Learning to choose between C and D
            learningTools.vGetNewActionQLearning(currentState, 2, P); // Use accumulated payoff as reward
            int action = learningTools.iNewAction2Play;
            return action == 0 ? "C" : "D";
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

        private String decideTransaction(double indexValue) {
            // Use learning for transaction decisions
            String transactionState = "Transaction" + (int)indexValue;
            learningTools.vGetNewActionQLearning(transactionState, 3, P); // 3 actions: buy, sell, none
            
            int decision = learningTools.iNewAction2Play;
            switch(decision) {
                case 0: // Buy
                    if (P > 0) {
                        double maxAffordable = P / (indexValue * (1 + F));
                        if (maxAffordable >= 1) {
                            return "Buy#1";
                        }
                    }
                    break;
                case 1: // Sell
                    if (A > 0) {
                        return "Sell#1";
                    }
                    break;
            }
            return "None";
        }

        // ... (keep other helper methods from RandomAgent)
    }

    // ... (keep other helper methods from RandomAgent)
    private class StateAction {
        private HashMap<String, double[]> hmQValues;  // Q-values for each state-action
        private HashMap<String, int[]> hmVisits;      // Number of visits for each state-action
        private double[] dQVal;                       // Q-values for current state
        private int[] iVisits;                        // Visits for current state
        
        public StateAction() {
            hmQValues = new HashMap<>();
            hmVisits = new HashMap<>();
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
        private Random random;
        private StateAction stateAction;
        public int iNewAction2Play;
        
        public LearningTools() {
            random = new Random();
            stateAction = new StateAction();
        }
        
        public void vGetNewActionQLearning(String sState, int iNActions, double dReward) {
            if (!stateAction.bIsState(sState)) {
                stateAction.vInitialize(sState, iNActions);
            }
            
            double[] qValues = stateAction.getQValues(sState);
            int[] visits = stateAction.getVisits(sState);
            
            // Exploration vs Exploitation
            if (random.nextDouble() > dEpsilon) {
                // Exploration: choose random action
                iNewAction2Play = random.nextInt(iNActions);
            } else {
                // Exploitation: choose best action
                iNewAction2Play = getBestAction(qValues);
            }
            
            // Update Q-value for the chosen action
            double learningRate = Math.max(dMINLearnRate, 1.0 / (1 + visits[iNewAction2Play]));
            double oldValue = qValues[iNewAction2Play];
            double newValue = oldValue + learningRate * (dReward - oldValue);
            
            stateAction.updateQValue(sState, iNewAction2Play, newValue);
            stateAction.incrementVisits(sState, iNewAction2Play);
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
}