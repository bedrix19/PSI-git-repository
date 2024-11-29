
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
    private ArrayList<PlayerInformation> players;
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

    /******************************************************************
    * 
    *   Players actions
    * 
    ******************************************************************/
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

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setContent("Removed");
        msg.addReceiver(playerToRemove);
        send(msg);

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

    public int resetPlayer(String agentName){
        AID playerToReset = this.getAgent(agentName);
        if(players==null) return 1;
        for (PlayerInformation player : players)
            if (player.aid.equals(playerToReset)) {
                player.roundPayoff = 0;
                player.accumulatedPayoff = 0;
                player.assets = 0;

                gui.logLine("Player " + player.aid.getName() + " has been reset to default values.");
                break;
            }
        return 0;
    }

    public int resetPlayers(){
        if(players==null) return 1;
        for (PlayerInformation player : players){
            player.roundPayoff = 0;
            player.accumulatedPayoff = 0;
            player.assets = 0;

            gui.logLine("Player " + player.aid.getName() + " has been reset to default values.");
        }
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

    /*******************************************************************
     *  getters
     ******************************************************************/
    public AID getAgent(String agentName){
        for (AID a : playerAgents)
            if (a.getLocalName().equals(agentName)) return a;
        return null;
    }

    public PlayerInformation getAgentInfById(int id){
        for (PlayerInformation player : players)
            if (player.id == id) return player;
        return null;
    }

    public String getGameParameters(){
        return this.parameters.toString();
    }

    public String getPlayerInfoById(int id) {
        for (PlayerInformation player : players)
            if (player.id == id) return player.toString();
        return "Player not found"; // Si no se encuentra el jugador
    }
    /*******************************************************************
     *  'setters'
     ******************************************************************/
    public void updateNumberOfRounds(int rounds) {
        parameters.setRounds(rounds);
    }

    public void updateCommissionFee(double commission) {
        parameters.setCommissionFee(commission);
    }

    /**
     * In this behavior this agent manages the course of a match during all the
     * rounds.
     */
    private class GameManager extends SimpleBehaviour {

        @Override
        public void action() {
            //Assign the IDs
            players = new ArrayList<>();
            int lastId = 0;
            for (AID a : playerAgents) {
                players.add(new PlayerInformation(a, lastId++));
            }
            for (PlayerInformation player : players) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                /* Id#ID#N,R,F */
                msg.setContent("Id#" + player.id + "#" + parameters.N + "," + parameters.R + "," + parameters.F);
                msg.addReceiver(player.aid);
                send(msg);
				gui.logLine("Id#" + player.id + "#" + parameters.N + "," + parameters.R + "," + parameters.F);
            }
            //Organize the matches
            for (int i = 0; i < players.size(); i++) {
                for (int j = i + 1; j < players.size(); j++) {
                    if (!stop) playGame(players.get(i), players.get(j));
                    else doWait();
                    
                    try{
                        Thread.sleep(250); //Tiempo en ms
                    }catch(InterruptedException e){
                        //No hacemos nada
                    }
                }
            }
            this.endGame();
        }

        private void playGame(PlayerInformation player1, PlayerInformation player2) {
            //Assuming player1.id < player2.id --> if not then:
            if(player1.id > player2.id){
                PlayerInformation aux = player1;
                player1 = player2;
                player2 = aux;
            }
            /**
             * Round: A round in this tournament represents a complete cycle where every agent has played a match against every other agent once.
             * NewGame: A new game refers to an individual match between two specific agents.
             * EndGame: Once all rounds have been played, the mainAgent will send a "GameOver" message to each agent.
             */
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(player1.aid);
            msg.addReceiver(player2.aid);
            msg.setContent("NewGame#" + player1.id + "#" + player2.id);
            send(msg);
            gui.logLine("NewGame#" + player1.id + "#" + player2.id);
            
            for(int round = 1; round <= parameters.R; round++){
                gui.logLine("Starting round " + round);

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
                msg.setContent("Results#" + player1.id + "," + player2.id + "#" + action1 + "," + action2 + "#" + getPayoff(player1, player2, action1, action2));
                send(msg);
                gui.logLine(msg.getContent());

                // Round manager
                double currentIndex = getIndexValue(round);
                double currentInflation = getInflationRate(round) / 100.0;

                player1.accumulatedPayoff = (int) (player1.accumulatedPayoff * (1 - currentInflation));
                player2.accumulatedPayoff = (int) (player2.accumulatedPayoff * (1 - currentInflation));

                sendRoundOverMessage(player1, currentInflation, currentIndex, round);
                sendRoundOverMessage(player2, currentInflation, currentIndex, round);

                handlePlayerResponse(player1, currentIndex);
                handlePlayerResponse(player2, currentIndex);
            }
            
        }

        /******************************************************************
         * 
         *  Manejar cada ronda
         * 
         ******************************************************************/
        private void sendRoundOverMessage(PlayerInformation player, double inflation, double index, int round) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

            msg.setContent("RoundOver#" + player.id + "#"
                    + player.roundPayoff + "#"
                    + player.accumulatedPayoff + "#"
                    + inflation + "#"
                    + player.assets + "#"
                    + index);

            msg.addReceiver(player.aid);
            send(msg);

            gui.logLine("Sent RoundOver message to " + player.aid.getName() + ": " + msg.getContent());
        }
        private void handlePlayerResponse(PlayerInformation player, double indexValue) {
            ACLMessage response = blockingReceive(); // Esperar la respuesta del jugador
            if (response != null) {
                String content = response.getContent();
                if (content.startsWith("Buy#")) {
                    handleBuyTransaction(player, content, indexValue);
                } else if (content.startsWith("Sell#")) {
                    handleSellTransaction(player, content, indexValue);
                } else if (content.startsWith("None")) {
                    gui.logLine(player.aid.getName() + ": Doesn't want to sell or buy");
                } else {
                    gui.logLine("Invalid transaction message from " + player.aid.getName() + ": " + content);
                }
            } else {
                gui.logLine("No response received from " + player.aid.getName());
            }
        }
        private void handleBuyTransaction(PlayerInformation player, String message, double indexValue) {
            double amountToBuy = Double.parseDouble(message.split("#")[1]); // Monto a comprar
            
            // Verificar que el jugador tenga suficientes unidades de payoff para comprar
            if (player.accumulatedPayoff >= amountToBuy) {
                double fee = amountToBuy * parameters.F; // Comisión por la transacción
                double totalCost = amountToBuy + fee;

                // Actualizar estadísticas del jugador
                player.accumulatedPayoff -= totalCost;
                player.assets += amountToBuy / indexValue;

                // Enviar mensaje de confirmación al jugador
                sendAccountingMessage(player);
                gui.logLine(player.aid.getName() + " successfully bought assets for " + amountToBuy + " units. Fee: " + fee);
            } else {
                gui.logLine(player.aid.getName() + " does not have enough payoff units to buy " + amountToBuy + " units.");
            }
        }
        private void handleSellTransaction(PlayerInformation player, String message, double indexValue) {
            double amountToSell = Double.parseDouble(message.split("#")[1]); // Monto a vender

            // Verificar que el jugador tenga suficientes activos para vender
            if (player.assets >= amountToSell) {
                double saleRevenue = amountToSell * indexValue; // Ganancia de la venta
                double fee = saleRevenue * parameters.F; // Comisión por la transacción

                // Actualizar estadísticas del jugador
                player.assets -= amountToSell;
                player.accumulatedPayoff += (saleRevenue - fee);

                // Enviar mensaje de confirmación al jugador
                sendAccountingMessage(player);
                gui.logLine(player.aid.getName() + " successfully sold " + amountToSell + " assets. Revenue: " + saleRevenue + ", Fee: " + fee);
            } else {
                gui.logLine(player.aid.getName() + " does not have enough assets to sell " + amountToSell + " units.");
            }
        }
        private void sendAccountingMessage(PlayerInformation player) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(player.aid);

            msg.setContent("Accounting#"
                        + player.id + "#"
                        + player.accumulatedPayoff + "#"
                        + player.assets);

            send(msg);
            gui.logLine("Sent Accounting message to " + player.aid.getName() + ": " + msg.getContent());
        }

        /******************************************************************
         * Manejar final del juego
         ******************************************************************/
        private void endGame() {
            gui.logLine("End of the game. Sending GameOver messages to all players.");

            for (PlayerInformation player : players) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

                msg.setContent("GameOver#" + player.id + "#" + player.accumulatedPayoff);

                msg.addReceiver(player.aid);
                send(msg);
                gui.logLine("Sent GameOver message to " + player.aid.getName() + ": " + msg.getContent());
            }
            gui.logLine("GameOver messages sent. Tournament completed.");
        }

        private String getPayoff(PlayerInformation player1, PlayerInformation player2, String action1, String action2) {
            // Variables para almacenar los payoffs individuales
            int payoff1 = 0;
            int payoff2 = 0;

            // Lógica para calcular los payoffs basados en las acciones
            switch (action1 + action2) {
                case "CC":
                    payoff1 = 3;
                    payoff2 = 3;
                    break;
                case "CD":
                    payoff1 = 0;
                    payoff2 = 5;
                    break;
                case "DC":
                    payoff1 = 5;
                    payoff2 = 0;
                    break;
                case "DD":
                    payoff1 = 1;
                    payoff2 = 1;
                    break;
                default:
                    gui.logLine("Invalid actions: " + action1 + " and " + action2);
                    return "Error";
            }

            // Actualizar los payoffs actuales de los jugadores
            player1.roundPayoff += payoff1;
            player2.roundPayoff += payoff2;

            // Actualizar los payoffs acumulados de los jugadores
            player1.accumulatedPayoff += payoff1;
            player2.accumulatedPayoff += payoff2;

            // Log para debugging
            gui.logLine("Payoff calculated for " + player1.aid.getLocalName() + " and " + player2.aid.getLocalName());
            gui.logLine("Actions: " + action1 + ", " + action2 + " | Payoffs: " + payoff1 + ", " + payoff2);

            // Devolver el resultado en el formato esperado
            return payoff1 + "," + payoff2;
        }


        @Override
        public boolean done() {
            return true;
        }
    }

    public class PlayerInformation {

        AID aid;
        int id;
		int roundPayoff;
        int accumulatedPayoff;
        int assets;

        public PlayerInformation(AID a, int i) {
            aid = a;
            id = i;
			roundPayoff = 0;
            accumulatedPayoff = 0;
            assets = 0;
        }

        @Override
        public boolean equals(Object o) {
            return aid.equals(o);
        }

        @Override
        public String toString() {
            // Devolver la información relevante del jugador
            return "ID: " + id + ", Payoff: " + accumulatedPayoff + ", Assets: " + assets;
        }
    }

    public class GameParametersStruct {

        int N;
        int R;
        double F;
        int S;
        int I;
        int P;

        public GameParametersStruct() {
            N = 2;
            R = 50;
            F = 0.01;
            S = 4;
            I = 0;
            P = 10;
        }

        public void setRounds(int round){
            this.R = round;
            gui.logLine("Number of rounds updated to: " + this.R);
        }

        public void setCommissionFee(double commission) {
            if (commission >= 0 && commission <= 1) this.F = commission;
            else gui.logLine("Invalid commission fee. F must be between 0 and 1.");
        }
        @Override
        public String toString(){
            return "N#" + this.N + ";S#" + this.S + ";R#" + this.R + ";I#" + this.I + ";P#" + this.P + ";F#" + this.F;
        }
    }
}

/**
 * Primera idea de hacer que las rondas terminen para todos
 * 
 * private void endRound(PlayerInformation player1, PlayerInformation player2, int round){
            gui.logLine("End of the round. Sending RoundOver messages to all players.");

            double currentIndex = getIndexValue(round);
            double currentInflation = getInflationRate(round);

            // un for-each para enviar el mensaje de RoundOver a cada jugador
            for (PlayerInformation player : players) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

                msg.setContent("RoundOver#" + player.id + "#" 
                                + player.roundPayoff + "#" 
                                + player.accumulatedPayoff + "#" 
                                + currentInflation + "#" 
                                + player.assets + "#" 
                                + currentIndex);

                msg.addReceiver(player.aid);
                send(msg);

                gui.logLine("Sent RoundOver message to " + player.aid.getName() + ": " + msg.getContent());

                // logica para manejar las respuestas
                ACLMessage response = blockingReceive(); // Esperar respuesta del agente

                if (response.getContent().startsWith("Buy#")) {
                        handleBuyTransaction(player, response.getContent(), currentIndex);
                } else if (response.getContent().startsWith("Sell#")) {
                    handleSellTransaction(player, response.getContent(), currentIndex);
                } else {
                    gui.logLine("Invalid transaction message: " + response.getContent());
                }
            }
        }
 */