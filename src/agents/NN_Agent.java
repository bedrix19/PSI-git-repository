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
import java.util.Locale;

public class NN_Agent extends Agent {
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
    private SOM neuralNet;
    private static final int GRID_SIDE = 5;
    private static final int INPUT_SIZE = 4;
    private String currentState;
    
    private enum State {
        s0NoConfig, s1AwaitingGame, s2Round, s3AwaitingResult
    }

    @Override
    protected void setup() {
        clearLog();
        state = State.s0NoConfig;
        neuralNet = new SOM(GRID_SIDE, INPUT_SIZE); // 5x5 grid, 4 input dimensions
        
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
                                writeLog("Bad configuration message");
                            }
                            // Reset parameters
                            P = 0.0;
                            A = 0.0;
                            neuralNet = new SOM(GRID_SIDE, INPUT_SIZE);
                        } else if (msg.getContent().equals("Removed") && msg.getPerformative() == ACLMessage.INFORM) {
                            writeLog("Removed");
                            doDelete();
                        }
                        break;

                    case s1AwaitingGame:
                        if (msg.getContent().startsWith("NewGame") && msg.getPerformative() == ACLMessage.INFORM) {
                            try {
                                if (validateNewGame(msg.getContent())) {
                                    writeLog("is playing against " + opponentId);
                                    state = State.s2Round;
                                }
                            } catch (NumberFormatException e) {
                                writeLog("Bad new game message");
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
                        } else {
                            writeLog(state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        }
                        break;

                    case s2Round:
                        if (msg.getContent().startsWith("Action") && msg.getPerformative() == ACLMessage.REQUEST) {
                            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                            response.addReceiver(mainAgent);
                            String action = chooseAction();
                            response.setContent("Action#" + action);
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

        private String chooseAction() {
            double[] input = createInputVector();
            String bmuPos = neuralNet.sGetBMU(input, true);
            double[] weights = neuralNet.dGetNeuronWeights(
                Integer.parseInt(bmuPos.split(",")[0]), 
                Integer.parseInt(bmuPos.split(",")[1])
            );
            return weights[0] > 0.5 ? "C" : "D";
        }

        private String decideTransaction(double indexValue) {
            double[] tradingInput = new double[]{
                P / Math.max(100.0, P),     // Normalized payoff
                A / Math.max(10.0, A),      // Normalized assets
                indexValue / 100.0,         // Normalized index
                (state == State.s2Round ? 1.0 : 0.0)  // Game state
            };

            String bmuPos = neuralNet.sGetBMU(tradingInput, true);
            double[] weights = neuralNet.dGetNeuronWeights(
                Integer.parseInt(bmuPos.split(",")[0]),
                Integer.parseInt(bmuPos.split(",")[1])
            );

            StringBuilder decision = new StringBuilder();
            double performance = P + (A * indexValue);

            // More conservative trading strategy
            if (weights[0] > 0.7 && P > 0) {  // Stronger buy signal threshold
                double maxAffordable = P / (indexValue * (1 + F));
                if (maxAffordable >= 1) {
                    double buyAmount = Math.min(maxAffordable * weights[0], maxAffordable * 0.5);
                    decision.append("Buy#").append(String.format("%.2f", buyAmount));
                }
            } else if (weights[1] > 0.6 && A > 0) {  // More selective selling
                double sellAmount = Math.min(A, A * weights[1] * 0.5);
                if (sellAmount >= 1.0) {
                    if (decision.length() > 0) decision.append(",");
                    decision.append("Sell#").append(String.format("%.2f", sellAmount));
                }
            }

            writeLog("Transaction: " + decision.toString());
            return decision.length() > 0 ? decision.toString() : "None";
        }

        private double[] createInputVector() {
            double[] input = new double[4];
            input[0] = P / 100.0; // Normalized payoff
            input[1] = A / 10.0;  // Normalized assets
            input[2] = opponentId / (double)N; // Normalized opponent ID
            input[3] = state == State.s2Round ? 1.0 : 0.0; // Game state
    
            writeLog(String.format("Input Vector: %.2f, %.2f, %.2f, %.2f", input[0], input[1], input[2], input[3]));
            return input;
        }

        private void processResults(String content) {
            // Format: "Results#id1,id2#action1,action2#result1,result2"
            String[] parts = content.split("#");
            String[] idmsg = parts[1].split(",");
            String[] payoffs = parts[3].split(",");
            
            int myPayoff;
            if(myId == Integer.parseInt(idmsg[0])) myPayoff = Integer.parseInt(payoffs[0]);
            else myPayoff = Integer.parseInt(payoffs[1]);
            P += myPayoff;

            writeLog("received payoff: " + myPayoff);
            writeLog("accumulated payoff: " + P);
        }

        private void processAccounting(String content) {
            // Format: "Accounting#id#payoff#assets"
            String[] parts = content.split("#");
            if (parts.length == 4) {
                P = Double.parseDouble(parts[2]);
                A = Double.parseDouble(parts[3]);
                writeLog("updated accounting - Payoff: " + P + ", Assets: " + A);
            }
        }

        private void processRoundOver(String content) {
            // Format: "RoundOver#id#roundPayoff#accumulatedPayoff#inflation#assets#index"
            String[] parts = content.split("#");
            if (parts.length == 7) {
                P = Double.parseDouble(parts[3]);
                A = Double.parseDouble(parts[5]);
                writeLog("round over - Payoff: " + P + ", Assets: " + A);
            }
        }
    }

    // Include the SOM class here
    private class SOM {
        private int iGridSide;
        private int iInputSize;
        private double dLearnRate = 0.3/*1.0*/;     // Reduced initial learning rate
        private double dDecLearnRate = 0.999;
        private double dMinLearnRate = 0.01;
        private double[] dBMU_Vector = null;
        private double[][][] dGrid;

        public SOM(int gridSize, int inputSize) {
            iGridSide = gridSize;
            iInputSize = inputSize;
            dBMU_Vector = new double[iInputSize];
            dGrid = new double[iGridSide][iGridSide][iInputSize];
            initializeGrid(); //vResetValues
        }

        private void initializeGrid() {
            for (int i = 0; i < iGridSide; i++)
                for (int j = 0; j < iGridSide; j++)
                    for (int k = 0; k < iInputSize; k++)
                        dGrid[i][j][k] = Math.random();
        }

        public String sGetBMU(double[] input, boolean train) {
            int bmuX = 0, bmuY = 0;
            double minDist = Double.MAX_VALUE;

             // Normalize input vector
            double[] normalizedInput = normalizeInput(input);
            
            // Find BMU
            for (int i = 0; i < iGridSide; i++) {
                for (int j = 0; j < iGridSide; j++) {
                    double dist = calculateDistance(normalizedInput, dGrid[i][j]);
                    if (dist < minDist) {
                        minDist = dist;
                        bmuX = i;
                        bmuY = j;
                    }
                }
            }

            // Update weights if training
            if (train) {
                updateWeights(normalizedInput, bmuX, bmuY);

                // Update learning rate with minimum threshold
                dLearnRate = Math.max(dLearnRate * dDecLearnRate, dMinLearnRate);
            }

            return bmuX + "," + bmuY;
        }

        public double[] dGetNeuronWeights(int x, int y) {
            return dGrid[x][y];
        }

        public double[] dvGetBMU_Vector() {
            return dBMU_Vector;
        }

        public double dGetLearnRate() {
            return dLearnRate;
        }

        private double calculateDistance(double[] input, double[] weights) {
            double sum = 0;
            for (int i = 0; i < input.length; i++) {
                sum += Math.pow(input[i] - weights[i], 2);
            }
            return Math.sqrt(sum);
        }

        private void updateWeights(double[] input, int bmuX, int bmuY) {
            int radius = Math.max(1, iGridSide / 4)/*iGridSide / 4*/;   // Dynamic radius
            double sigma = radius / 2.0;
            for (int i = Math.max(0, bmuX - radius); i < Math.min(iGridSide, bmuX + radius); i++) {
                for (int j = Math.max(0, bmuY - radius); j < Math.min(iGridSide, bmuY + radius); j++) {
                    double dist = Math.sqrt(Math.pow(i - bmuX, 2) + Math.pow(j - bmuY, 2));
                    double influence = Math.exp(-dist / (2 * sigma * sigma));
                    for (int k = 0; k < iInputSize; k++) {
                        dGrid[i][j][k] += dLearnRate * influence * (input[k] - dGrid[i][j][k]);
                    }
                }
            }
        }

        /**
         * New funcions for the SOM
         */
        private double[] normalizeInput(double[] input) {
            double[] normalized = input.clone();
            double max = Double.MIN_VALUE;
            double min = Double.MAX_VALUE;
            
            // Find min and max
            for (double v : input) {
                max = Math.max(max, v);
                min = Math.min(min, v);
            }
            
            // Normalize to [0,1]
            if (max > min) {
                for (int i = 0; i < input.length; i++) {
                    normalized[i] = (input[i] - min) / (max - min);
                }
            }
            
            return normalized;
        }
    }

    private void clearLog() {
        try (FileWriter fw = new FileWriter("NN_AgentsLog.out", false)) {
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
			fichero = new FileWriter("NN_AgentsLog.out", true);
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
