Student: Renato Bedriñana Cárdenas
PSI4/
│
├── bin/                  # .class
├── src/                  # .java
│   ├── MainAgent.java
│   ├── GUI.java
│   └── agents/
│       └── RandomAgent.java
└── jade.jar              # jade.jar

Compile:
javac -d bin -cp .;jade.jar src/*.java src/agents/*.java

Example of use:
java -cp bin;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;agent1:agents.RandomAgent;agent2:agents.RandomAgent;agent3:agents.RandomAgent"
java -cp bin;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;bedrix:agents.RandomAgent;chocla:agents.RandomAgent;camilo:agents.RandomAgent"
java -cp bin;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;bedrix:agents.PSI_4;chocla:agents.NN_Agent;camilo:agents.RandomAgent"