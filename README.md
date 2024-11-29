# Git-repository
 

This is a set of basic commands to compile and execute the code:

Linux & Mac:
- To compile: javac -cp .:jade.jar *.java
- To execute: java -cp .:jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;RandomAgent:RandomAgent;"


Windows (do not use the PowerShell):
- To compile: javac -cp .;jade.jar *.java
- To execute: java -cp .;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;RandomAgent:RandomAgent;"
- To execute: java -cp .;jade.jar jade.Boot -notmp -gui -agents "MainAgent:MainAgent;Renato:RandomAgent;Chocla:RandomAgent;Hugo:RandomAgent;Nicolas:RandomAgent;"


* Note that you need to have the "jade.jar" library in the present folder

* delibery 29/11
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