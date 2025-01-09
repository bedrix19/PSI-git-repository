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

public class PSI_4 extends Agent {
    private State state;
    private AID mainAgent;
    private int myId, opponentId;
    private int N, R, S, I;
    private double F, A, P;
    private ACLMessage msg;
    private boolean hasOpponentDefected = false;  // Memory of opponent's defection
    private Random random = new Random();

    private enum State {
        s0NoConfig, s1AwaitingGame, s2Round, s3AwaitingResult
    }

    @Override
    protected void setup() {
        state = State.s0NoConfig;
        clearLog();
        
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
        writeLog("PSI_4 " + getAID().getName() + " is ready.");
    }

    private class Play extends CyclicBehaviour {
        @Override
        public void action() {
            msg = blockingReceive();
            if (msg != null) {
                writeLog(getAID().getName() + ":" + state.name());
                writeLog(getAID().getName() + "Recibio: " + msg.getContent());
                
                switch (state) {
                    case s0NoConfig:
                        if (msg.getContent().startsWith("Id") && msg.getPerformative() == ACLMessage.INFORM) {
                            if(validateSetupMessage(msg)) {
                                state = State.s1AwaitingGame;
                            }
                        }
                        break;

                    case s1AwaitingGame:
                        if (msg.getContent().startsWith("NewGame")) {
                            if(validateNewGame(msg.getContent())) {
                                hasOpponentDefected = false;  // Reset for new game
                                state = State.s2Round;
                            }
                        } else if (msg.getContent().startsWith("RoundOver")) {
                            processRoundOver(msg.getContent());
                            String decision = decideTransaction(Double.parseDouble(msg.getContent().split("#")[6]));
                            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                            response.addReceiver(mainAgent);
                            response.setContent(decision);
                            send(response);
                        }
                        break;

                    case s2Round:
                        if (msg.getContent().startsWith("Action")) {
                            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                            response.addReceiver(mainAgent);
                            response.setContent("Action#" + decidePrisonerAction());
                            send(response);
                            state = State.s3AwaitingResult;
                        }
                        break;

                    case s3AwaitingResult:
                        if (msg.getContent().startsWith("Results")) {
                            processResults(msg.getContent());
                            state = State.s1AwaitingGame;
                        }
                        break;
                }
            }
        }

        private String decidePrisonerAction() {
            if (hasOpponentDefected) return "D";  // Permanent retaliation
            return "C";  // Initial cooperation
        }

        private void processResults(String content) {
            String[] parts = content.split("#");
            String[] idmsg = parts[1].split(",");
            String[] actions = parts[2].split(",");
            String[] payoffs = parts[3].split(",");
            
            // Check if opponent defected
            String opponentAction;
            if(myId == Integer.parseInt(idmsg[0])) {
                opponentAction = actions[1];
                P += Double.parseDouble(payoffs[0]);
            } else {
                opponentAction = actions[0];
                P += Double.parseDouble(payoffs[1]);
            }
            
            if (opponentAction.equals("D")) {
                hasOpponentDefected = true;
            }
            
            writeLog(getAID().getName() + " accumulated payoff: " + P);
        }

        // Reuse RandomAgent's transaction logic
        private String decideTransaction(double indexValue) {
            StringBuilder decision = new StringBuilder();

            if (random.nextBoolean() && P > 0) {
                double maxAffordable = P / (indexValue * (1 + F));
                if (maxAffordable >= 1) {
                    int buyAmount = random.nextInt((int)maxAffordable) + 1;
                    decision.append("Buy#").append(buyAmount);
                }
            }

            if (random.nextBoolean() && A > 0) {
                if (A >= 1.0) {
                    double sellAmount = 1.0 + random.nextDouble() * (A - 1.0);
                    if (decision.length() > 0) {
                        decision.append(",");
                    }
                    decision.append("Sell#").append(String.format("%.2f", sellAmount));
                }
            }

            return decision.length() == 0 ? "None" : decision.toString();
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

        private void processAccounting(String content) {
            // Format: "Accounting#id#payoff#assets"
            String[] parts = content.split("#");
            if (parts.length == 4) {
                P = Double.parseDouble(parts[2]);
                A = Double.parseDouble(parts[3]);
                writeLog(getAID().getName() + " updated accounting - Payoff: " + P + ", Assets: " + A);
            }
        }

        private void processRoundOver(String content) {
            // Format: "RoundOver#id#roundPayoff#accumulatedPayoff#inflation#assets#index"
            String[] parts = content.split("#");
            if (parts.length == 7) {
                P = Double.parseDouble(parts[3]);
                A = Double.parseDouble(parts[5]);
                writeLog(getAID().getName() + " round over - Payoff: " + P + ", Assets: " + A);
            }
        }
    }

    private void writeLog(String log) {
		FileWriter fichero = null;
		PrintWriter pw = null;
		try {
			fichero = new FileWriter("RandomAgentsLog.out", true);
			pw = new PrintWriter(fichero);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				pw.write(log + "\n");
				if (null != fichero) fichero.close();
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
	}
    
    private void clearLog() {
        try (FileWriter fw = new FileWriter("RandomAgentsLog.out", false)) {
            // Opening with false overwrites the file
            fw.write("");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}