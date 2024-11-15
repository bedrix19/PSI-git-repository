
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.PrintStream;
import java.util.ArrayList;

public class MainAgent extends Agent {

    private GUI gui;
    private AID[] playerAgents;
    private GameParametersStruct parameters = new GameParametersStruct();
    private boolean stop = false;

    private double baseIndexValue = 100.0;  // Valor base del índice
    private double baseInflationRate = 2.0; // Tasa de inflación base (%)

    @Override
    protected void setup() {
        gui = new GUI(this);
        System.setOut(new PrintStream(gui.getLoggingOutputStream()));

        updatePlayers();
        gui.logLine("Agent " + getAID().getName() + " is ready.");
    }

    public int updatePlayers() {
        gui.logLine("Updating player list");
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Player");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                gui.logLine("Found " + result.length + " players");
            }
            playerAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                playerAgents[i] = result[i].getName();
            }
        } catch (FIPAException fe) {
            gui.logLine(fe.getMessage());
        }
        //Provisional
        String[] playerNames = new String[playerAgents.length];
        for (int i = 0; i < playerAgents.length; i++) {
            playerNames[i] = playerAgents[i].getName();
        }
        gui.setPlayersUI(playerNames);
        return 0;
    }

    public int removePlayer(String agentName) {
        AID playerToRemove = this.getAgent(agentName);

        if(playerToRemove == null) return 1;

        // Crear una nueva lista sin el jugador a eliminar
        ArrayList<AID> updatedPlayers = new ArrayList<>();
        for (AID player : playerAgents) {
            if (!player.equals(playerToRemove)) {
                updatedPlayers.add(player);
            }
        }

        // Actualizar el array playerAgents con la nueva lista
        playerAgents = updatedPlayers.toArray(new AID[0]);

        // Actualizar la interfaz gráfica
        String[] playerNames = new String[playerAgents.length];
        for (int i = 0; i < playerAgents.length; i++) {
            playerNames[i] = playerAgents[i].getName();
        }
        gui.setPlayersUI(playerNames);

        // Log de la eliminación
        gui.logLine("Player " + playerToRemove.getName() + " has been removed.");
        return 0;
    }

    public void printAgents(){
        gui.logLine("Printing all player agent information:");
        if (playerAgents == null || playerAgents.length == 0) {
            gui.logLine("No players found.");
            return;
        }
        
        for (int i = 0; i < playerAgents.length; i++) {
            String agentInfo = "Agent " + playerAgents[i].getName();
            // Si los IDs están asignados y deseas mostrar los ID:
            // Considera que quizás tengas que obtener el ID de otro lado o gestionarlo con PlayerInformation.
            gui.logLine(agentInfo);
        }
    }

    public void printAgentInfoByName(String agentName) {
        AID foundAgent = this.getAgent(agentName);

        if(foundAgent != null)
            gui.logLine("Agent found: " + foundAgent.getName());
        else
            gui.logLine("Agent " + agentName + " not found.");
    }

    public int newGame() {
        addBehaviour(new GameManager());
        return 0;
    }

    public void setStop() {
        gui.logLine("Stop the game");
		stop = true;
	}

	public void setResume() {
        gui.logLine("Resume the game");
		stop = false;
		doWake();
	}

    public double getIndexValue(int round) {
        return baseIndexValue + round * 1.5; // Aumento de 1.5 por ronda
    }

    public double getInflationRate(int round) {
        return baseInflationRate + (round * 0.1); // Aumento de 0.1% por ronda
    }

    // getters
    public AID getAgent(String agentName){
        for (AID a : playerAgents)
            if (a.getLocalName().equals(agentName)) return a;
        return null;
    }

    public String getGameParameters(){
        return this.parameters.toString();
    }
    /**
     * In this behavior this agent manages the course of a match during all the
     * rounds.
     */
    private class GameManager extends SimpleBehaviour {

        @Override
        public void action() {
            //Assign the IDs
            ArrayList<PlayerInformation> players = new ArrayList<>();
            int lastId = 0;
            for (AID a : playerAgents) {
                players.add(new PlayerInformation(a, lastId++));
            }
            for (PlayerInformation player : players) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setContent("Id#" + player.id + "#" + parameters.N + "," + parameters.S + "," + parameters.R + "," + parameters.I + "," + parameters.P);
                msg.addReceiver(player.aid);
                send(msg);
				gui.logLine("Id#" + player.id + "#" + parameters.N + "," + parameters.R);
            }
            //Organize the matches
			for (int i = 0; i < players.size(); i++) {
                for (int j = i + 1; j < players.size(); j++) {
                    for (int k = 0; k < parameters.R; k++) {
                        if(!stop)
                            playGame(players.get(i), players.get(j),k);
                        else
                            doWait();
                    }
                }
            }
            this.endGame();
        }

        private void playGame(PlayerInformation player1, PlayerInformation player2, int round) {
            //Assuming player1.id < player2.id
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            if(round == 0){
                msg.addReceiver(player1.aid);
                msg.addReceiver(player2.aid);
                msg.setContent("NewGame#" + player1.id + "," + player2.id);
                send(msg);
                gui.logLine("NewGame#" + player1.id + "#" + player2.id);
            }else{
                msg = new ACLMessage(ACLMessage.REQUEST);
                msg.setContent("Action");
                msg.addReceiver(player1.aid);
                send(msg);

                gui.logLine("Main Waiting for movement");
                ACLMessage move1 = blockingReceive();
				String action1 = move1.getContent().split("#")[1];
                gui.logLine("Main Received " + move1.getContent() + " from " + move1.getSender().getName());

                msg = new ACLMessage(ACLMessage.REQUEST);
                msg.setContent("Action");
                msg.addReceiver(player2.aid);
                send(msg);

                gui.logLine("Main Waiting for movement");
                ACLMessage move2 = blockingReceive();
				String action2 = move2.getContent().split("#")[1];
                gui.logLine("Main Received " + move1.getContent() + " from " + move1.getSender().getName());

                msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(player1.aid);
                msg.addReceiver(player2.aid);
				msg.setContent("Results#" + player1.id + "," + player2.id + "#" + action1 + "," + action2);
                send(msg);
				gui.log("Results#" + player1.id + "," + player2.id + "#" + action1 + "," + action2);
                gui.logLine("|\t"+getPayoff(player1, player2, action1, action2));
            }   
        }

        private void endGame(){
            gui.logLine("End of the game");
            // Logica para rellenar la tabla
        }

        private String getPayoff(PlayerInformation player1, PlayerInformation player2, String action1, String action2){
            switch (action1 + action2) {
                case "CC": // (3,3)
                    player1.payoff += 3;
                    player2.payoff += 3;
                    return "3,3";
                case "CD": // (0,5)
                    player2.payoff += 5;
                    return "0,5";
                case "DC": // (5,0)
                    player1.payoff += 5;
                    return "5,0";
                case "DD": // (1,1)
                    player1.payoff += 1;
                    player2.payoff += 1;
                    return "1,1";
                default:
                    return null;
            }
        }

        @Override
        public boolean done() {
            return true;
        }
    }

    public class PlayerInformation {

        AID aid;
        int id;
		int payoff;

        public PlayerInformation(AID a, int i) {
            aid = a;
            id = i;
			payoff = 0;
        }

        @Override
        public boolean equals(Object o) {
            return aid.equals(o);
        }
    }

    public class GameParametersStruct {

        int N;
        int S;
        int R;
        int I;
        int P;

        public GameParametersStruct() {
            N = 2;
            S = 4;
            R = 50;
            I = 0;
            P = 10;
        }

        @Override
        public String toString(){
            return "N#"+this.N+";S#"+this.S+";R#"+this.R+";I#"+this.I+";P#"+this.P;
        }
    }
}

/*
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.PrintStream;
import java.util.ArrayList;

public class MainAgent extends Agent {
    private MainAgentGUI gui;
    private int numRounds = 500;
    private double commissionFee = 0.01;
    private Map<String, Integer> scores = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("MainAgent " + getAID().getName() + " is ready.");

        SwingUtilities.invokeLater(() -> {
            gui = new MainAgentGUI();
            gui.setVisible(true);
            gui.log("MainAgent initialized.");
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Action")) {
                        // Procesar la acción recibida y actualizar puntuaciones
                        processAction(msg);
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void processAction(ACLMessage msg) {
        String sender = msg.getSender().getLocalName();
        String action = msg.getContent().split("#")[1];
        
        // Actualizar puntuaciones según la acción
        int score = action.equals("D") ? 4 : 2;
        scores.put(sender, scores.getOrDefault(sender, 0) + score);
        
        String logMessage = sender + " chose action: " + action + " - Current Score: " + scores.get(sender);
        System.out.println(logMessage);
        
        // Registrar en la GUI
        if (gui != null) {
            gui.log(logMessage);
        }
    }

    @Override
    protected void takeDown() {
        System.out.println("MainAgent " + getAID().getName() + " terminating.");
        if (gui != null) {
            gui.dispose();
        }
    }
}
*/