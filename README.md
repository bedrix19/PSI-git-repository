Multi-Agent Trading System with Prisoner's Dilemma
Project Overview
A multi-agent system built with JADE framework that combines a Prisoner's Dilemma game with a trading market simulation. Agents compete in repeated games while managing assets and payoffs under dynamic market conditions with inflation.

Core Features
Prisoner's Dilemma tournaments between agents
Asset trading with dynamic index values
Market simulation with inflation and commission fees
Real-time monitoring through GUI
Multiple agent types with different strategies

This is a set of basic commands to compile and execute the code in Windows (do not use the PowerShell):
- To compile:
  ``` bash
    javac -d bin -cp .;jade.jar src/*.java src/agents/*.java
  ```
- To execute:
  ``` bash
  java -cp bin;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;agent1:agents.RandomAgent;agent2:agents.RandomAgent;agent3:agents.RL_Agent;agent4:agents.NN_Agent;agent5:agents.PSI_4"
  ```
  examples:
  ``` bash
  java -cp bin;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;bedrix:agents.RandomAgent;chocla:agents.RandomAgent;camilo:agents.RandomAgent"
  java -cp bin;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;bedrix:agents.PSI_4;NN:agents.NN_Agent;RL:agents.RL_Agent"
  ```

* Note that you need to have the "jade.jar" library in the present folder

## Project Structure
```
PSI4/
│
├── bin/                  # Compiled .class files
├── src/
│   ├── MainAgent.java
│   ├── GUI.java
│   └── agents/
│       └── RandomAgent.java
└── jade.jar              # jade.jar (must be in the root folder of the project)
```
