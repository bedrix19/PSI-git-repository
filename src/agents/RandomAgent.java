package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;

import java.io.FileWriter;
import java.io.PrintWriter;

import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.Random;

public class RandomAgent extends Agent {

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

    private enum State {
        s0NoConfig, s1AwaitingGame, s2Round, s3AwaitingResult
    }

    protected void setup() {
        state = State.s0NoConfig;

        //clean the .out
        clearLog();

        //Register in the yellow pages as a player
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

    protected void takeDown() {
        //Deregister from the yellow pages
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        writeLog("Deregistering from yellow pages.");
    }

    private class Play extends CyclicBehaviour {
        Random random = new Random(1000);
        @Override
        public void action() {
            msg = blockingReceive();
            if (msg != null) {
                //-------- Agent logic
                writeLog(state.name());
                writeLog("Recibio: "+msg.getContent());
                switch (state) {
                    case s0NoConfig:
                        //If INFORM Id#_#_,_,_,_ PROCESS SETUP --> go to state 1
                        //Else ERROR
                        if (msg.getContent().startsWith("Id") && msg.getPerformative() == ACLMessage.INFORM) {
                            boolean parametersUpdated = false;
                            try {
                                if(validateSetupMessage(msg)){
                                    writeLog("RandomAgent is up with ID:" + myId);
                                    state = State.s1AwaitingGame;
                                }
                            } catch (NumberFormatException e) {
                                writeLog(state.name() + " - Bad message:\n\t"+msg.getContent());
                            }
                            // Reset parameters
                            P = 0.0;
                            A = 0.0;
                        }  else if (msg.getContent().equals("Removed") && msg.getPerformative() == ACLMessage.INFORM) {
                            writeLog("Removed");
                            doDelete();
                        } else {
                            writeLog(state.name() + " - Unexpected message:\n\t"+msg.getContent());
                        }
                        break;
                    case s1AwaitingGame:
                        //If INFORM NEWGAME#_,_ PROCESS NEWGAME --> go to state 2
                        //Else ERROR
                        //TODO I probably should check if the new game message comes from the main agent who sent the parameters (?)
                        if (msg.getContent().startsWith("NewGame") && msg.getPerformative() == ACLMessage.INFORM){
                            try {
                                if(validateNewGame(msg.getContent())){
                                    writeLog("is playing against " + opponentId);
                                    state = State.s2Round;
                                }
                            } catch (NumberFormatException e) {
                                writeLog(state.name() + " - Bad message:\n\t" + msg.getContent());
                            }
                        }  else if (msg.getContent().startsWith("RoundOver") && msg.getPerformative() == ACLMessage.REQUEST) {
                            processRoundOver(msg.getContent());
                            /**
                             * 
                             * Choose between buy, sell or none
                             * RoundOver#ID#payoff#payoff_accumulated#inflation#assets#index
                             *
                             **/
                            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                            response.addReceiver(mainAgent);
                            String decision = decideTransaction(Double.parseDouble(msg.getContent().split("#")[6]));
                            response.setContent(decision);
                            send(response);
                            writeLog("decided: " + decision);
                        } else if (msg.getContent().startsWith("Accounting") && msg.getPerformative() == ACLMessage.INFORM) {
                            processAccounting(msg.getContent());
                        } else if (msg.getContent().startsWith("GameOver") && msg.getPerformative() == ACLMessage.INFORM) {
                            writeLog("Total payoff: " + msg.getContent().split("#")[2]);
                            state = State.s0NoConfig;
                        } else writeLog(state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        break;
                    case s2Round:
                        //If REQUEST POSITION --> INFORM POSITION --> go to state 3
                        //If INFORM ENDGAME go to state 0
                        //Else error
                        if (msg.getContent().startsWith("Action") && msg.getPerformative() == ACLMessage.REQUEST /*&& msg.getContent().startsWith("Position")*/) {
                            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                            msg.addReceiver(mainAgent);
                            msg.setContent("Action#" + randomOption());
                            writeLog("sent " + msg.getContent());
                            send(msg);
                            state = State.s3AwaitingResult;
                        } else writeLog(state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        break;
                    case s3AwaitingResult:
                        //If INFORM RESULTS --> go to state 2
                        //Else error
                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Results")) {
                            // Procesar resultados
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

        /**
         * Agent logic to play the game
         */
        private String randomOption() {
			int valorDado = (int) Math.floor(Math.random() * 2 + 1);
			String answer = "";
			switch (valorDado) {
			case 1:
				answer = "D";
				break;
			case 2:
				answer = "C";
				break;
			default:
				answer = "Error";
				break;
			}
			return answer;
		}

        private String decideTransaction(double indexValue) {
            StringBuilder decision = new StringBuilder();

            // Decide to buy
            if (random.nextBoolean() && P > 0) {  // Only try to buy if we have positive payoff
                double maxAffordable = P / (indexValue * (1 + F));  // Consider commission fee
                if (maxAffordable >= 1) {  // Only proceed if we can afford at least 1 unit
                    int buyAmount = random.nextInt((int)maxAffordable) + 1;
                    decision.append("Buy#").append(buyAmount);
                }
            }

            // Decide to sell
            if (random.nextBoolean() && A > 0) {  // Only try to sell if we have assets
                double maxSellable = A;  // Can't sell more than we have
                if (maxSellable >= 1.0) {
                    double sellAmount = 1.0 + random.nextDouble() * (maxSellable - 1.0);
                    if (decision.length() > 0) {
                        decision.append(",");
                    }
                    decision.append("Sell#").append(String.format("%.2f", sellAmount));
                }
            }

            if (decision.length() == 0) return "None"; // If no decision was made, return "None"

            writeLog("Transaction: " + decision.toString());
            return decision.toString();
        }

        private void processResults(String content) {
            /**
             * Results#4,7#D,C#4,0 means that in a round between players 4 and 7, the former
             * chose D and the latter chose C, so they get the payoffs of 4 and 0, respectively.
             */
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
				pw.write(getAID().getName() + " : " + log + "\n");
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
