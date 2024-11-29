PSI4/
│
├── bin/                  # .class compilados
├── src/                  # .java
│   ├── MainAgent.java
│   ├── GUI.java
│   └── agents/
│       └── RandomAgent.java
└── jade.jar              # jade.jar (debe estar en la misma carpeta que tu proyecto)

Compilar:
javac -d bin -cp .;jade.jar src/*.java src/agents/*.java

Ejecutar:
java -cp bin;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;agent1:agents.RandomAgent;agent2:agents.RandomAgent;agent3:agents.RandomAgent"