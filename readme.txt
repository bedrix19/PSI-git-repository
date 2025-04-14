Student: Renato Bedriñana Cárdenas
PSI4/
│
├── bin/                  # .class
├── src/                  # .java
│   ├── MainAgent.java
│   ├── GUI.java
│   └── agents/
│       ├── RandomAgent.java
│       ├── NN_Agent.java
│       ├── RL_Agent.java
│       └── PSI_4.java
└── jade.jar              # jade.jar

Compile:
javac -d bin -cp .;jade.jar src/*.java src/agents/*.java

Example of use:
java -cp bin;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;agent1:agents.RandomAgent;agent2:agents.RandomAgent;agent3:agents.RL_Agent;agent4:agents.NN_Agent;agent5:agents.PSI_4"

Notes:
- I upload my agent PSI_4 because in GUI_Tournament it was malfunctioning, it is an agent that always does 'C' until it receives a 'D' from that agent and then it always plays 'D' against that agent and uses the transaction method of NN_Agent
- I fixed my first delibery errors.

##################
#    RL_Agent    #
##################
A reinforcement learning agent that uses both Q-Learning and Learning Automata for decision making.

Features:
Uses Q-Learning for Prisoner's Dilemma decisions
Employs Learning Automata for trading decisions
Maintains state-action history
Adapts strategy based on rewards

Key Components:
private LearningTools learningTools;
private String currentState;
private double previousP, previousA, previousIndex;

Decision Making:
1. learningTools.vGetNewActionQLearning(currentState, 2, P):
   - Uses Q-Learning with ε-greedy policy (dEpsilon = 0.95)
   - Updates Q-values based on received payoffs
   - Maintains visit counts for adaptive learning rates
2. learningTools.vGetNewActionAutomata(transactionState, 3, calculateReward()):
   - Uses probabilities that sum to 1.0 for three actions
   - Reinforces successful actions and weakens others
   - Includes dynamic learning rate decay

##################
#    NN_Agent    #
##################
An agent using a Self-Organizing Map (SOM) neural network for pattern recognition and decision making.

Features:
Uses 5x5 SOM grid for pattern learning
4-dimensional input vector for state representation
Adaptive learning rate
Normalized input processing

Key Components:
private SOM neuralNet;
private static final int GRID_SIDE = 5;
private static final int INPUT_SIZE = 4;

Decision Making:
1. Game decisions: 
   - Input vector includes: payoff, assets, opponent ID, game state
   - Uses normalized inputs in range [0,1]
   - BMU updates with decreasing learning rate (0.3 to 0.01)

double[] weights = neuralNet.dGetNeuronWeights(bmuX, bmuY);
return weights[0] > 0.5 ? "C" : "D";

2. Trading decisions:
   - Input vector: payoff, assets, index value, game state
   - Uses dynamic radius for weight updates
   - Transaction sizes proportional to confidence and limited by resources
   
if (weights[0] > 0.7 && P > 0) {  // Buy signal
    double buyAmount = Math.min(maxAffordable * weights[0], maxAffordable * 0.5);
} else if (weights[1] > 0.6 && A > 0) {  // Sell signal
    double sellAmount = Math.min(A, A * weights[1] * 0.5);
}


###################
# Key Differences #
###################
RL_Agent learns through trial and error with explicit rewards
NN_Agent learns patterns in market conditions and opponent behavior
RL_Agent maintains discrete state-action mappings
NN_Agent uses continuous state space representation
