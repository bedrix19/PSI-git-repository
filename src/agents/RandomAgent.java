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
     */
    private int N, R, P, A, S, I;
    private double F;
    private ACLMessage msg;

    private enum State {
        s0NoConfig, s1AwaitingGame, s2Round, s3AwaitingResult
    }

    protected void setup() {
        state = State.s0NoConfig;

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
        writeLog("RandomAgent " + getAID().getName() + " is ready.");

    }

    protected void takeDown() {
        //Deregister from the yellow pages
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        writeLog("RandomPlayer: " + getAID().getName() + " deregistering from yellow pages.");
    }

    private class Play extends CyclicBehaviour {
        Random random = new Random(1000);
        @Override
        public void action() {
            writeLog(getAID().getName() + ":" + state.name());
            msg = blockingReceive();
            if (msg != null) {
                //-------- Agent logic
                writeLog("Se recibio: "+msg.getContent());
                switch (state) {
                    case s0NoConfig:
                        //If INFORM Id#_#_,_,_,_ PROCESS SETUP --> go to state 1
                        //Else ERROR
                        if (msg.getContent().startsWith("Id") && msg.getPerformative() == ACLMessage.INFORM) {
                            boolean parametersUpdated = false;
                            try {
                                parametersUpdated = validateSetupMessage(msg);
                            } catch (NumberFormatException e) {
                                writeLog(getAID().getName() + ":" + state.name() + " - Bad message:\n\t"+msg.getContent());
                            }
                            if (parametersUpdated){
                                writeLog("RandomAgent " + getAID().getName() + " is up with ID:" + myId);
                                state = State.s1AwaitingGame;
                            }

                        }  else if (msg.getContent().equals("Removed") && msg.getPerformative() == ACLMessage.INFORM) {
                            doDelete();
                        } else {
                            writeLog(getAID().getName() + ":" + state.name() + " - Unexpected message:\n\t"+msg.getContent());
                        }
                        break;
                    case s1AwaitingGame:
                        //If INFORM NEWGAME#_,_ PROCESS NEWGAME --> go to state 2
                        //If INFORM Id#_#_,_,_,_ PROCESS SETUP --> stay at s1
                        //Else ERROR
                        //TODO I probably should check if the new game message comes from the main agent who sent the parameters
                        if (msg.getPerformative() == ACLMessage.INFORM) {
                            if (msg.getContent().startsWith("Id")) { //Game settings updated
                                try {
                                    validateSetupMessage(msg);
                                } catch (NumberFormatException e) {
                                    writeLog(getAID().getName() + ":" + state.name() + " - Bad message:\n\t"+msg.getContent());
                                }
                            } else if (msg.getContent().startsWith("NewGame")) {
                                boolean gameStarted = false;
                                try {
                                    gameStarted = validateNewGame(msg.getContent());
                                } catch (NumberFormatException e) {
                                    writeLog(getAID().getName() + ":" + state.name() + " - Bad message:\n\t" + msg.getContent());
                                }
                                if (gameStarted) state = State.s2Round;
                            }
                        } else {
                            writeLog(getAID().getName() + ":" + state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        }
                        break;
                    case s2Round:
                        //If REQUEST POSITION --> INFORM POSITION --> go to state 3
                        //If INFORM CHANGED stay at state 2
                        //If INFORM ENDGAME go to state 1
                        //Else error
                        if (msg.getContent().startsWith("Action") && msg.getPerformative() == ACLMessage.REQUEST /*&& msg.getContent().startsWith("Position")*/) {
                            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                            msg.addReceiver(mainAgent);
                            msg.setContent("Action#" + randomOption());
                            writeLog(getAID().getName() + " sent " + msg.getContent());
                            send(msg);
                            state = State.s3AwaitingResult;
                        } else if (msg.getContent().startsWith("RoundOver") && msg.getPerformative() == ACLMessage.REQUEST) {
                            /**
                             * 
                             * Decide si comprar, vender o no hacer nada
                             * RoundOver#ID#payoff#payoff_accumulated#inflation#assets#index
                             *
                             **/ 
                            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                            response.addReceiver(mainAgent);
                            String decision = decideTransaction(Double.parseDouble(msg.getContent().split("#")[6]));
                            response.setContent(decision);
                            send(response);

                            writeLog(getAID().getName() + " decided: " + decision);
                        } else if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("GameOver")) {
                            writeLog(getAID().getName() + " Total payoff: " + msg.getContent().split("#")[2]);
                            state = State.s1AwaitingGame;
                        } else {
                            writeLog(getAID().getName() + ":" + state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        }
                        break;
                    case s3AwaitingResult:
                        //If INFORM RESULTS --> go to state 2
                        //Else error
                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Results")) {
                            // Procesar resultados
                            processResults(msg.getContent());
                            state = State.s2Round;
                        } else {
                            writeLog(getAID().getName() + ":" + state.name() + " - Unexpected message:\n\t" + msg.getContent());
                        }
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
            int decision = random.nextInt(3);
            int amount = random.nextInt(5) + 1; // Cantidad aleatoria (1-5)

            if (decision == 1 && P >= amount * indexValue) { // Buy
                A += amount;
                P -= amount * indexValue;
                return "Buy#" + amount;
            } else if (decision == 2 && A >= amount) { // Sell
                A -= amount;
                P += amount * indexValue;
                return "Sell#" + amount;
            }
            return "None"; // No hace nada
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

            writeLog(getAID().getName() + " received payoff: " + myPayoff);
            writeLog(getAID().getName() + " accumulated payoff: " + P);
        }
    }

    private void writeLog(String log) {
		FileWriter fichero = null;
		PrintWriter pw = null;
		try {
			fichero = new FileWriter("log.txt", true);
			pw = new PrintWriter(fichero);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				pw.write(log + "\n");
				if (null != fichero)
					fichero.close();
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
	}
}
