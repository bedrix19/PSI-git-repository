
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public final class GUI extends JFrame implements ActionListener {
    JLabel leftPanelRoundsLabel;
    JLabel leftPanelExtraInformation;
    JList<String> list;
    private MainAgent mainAgent;
    private JPanel rightPanel;
    private JTextArea rightPanelLoggingTextArea;
    private LoggingOutputStream loggingOutputStream;

    // For the central bottom panel
    private JTable resultsTable;
    private JTable playerStatusTable;

    public GUI() {
        initUI();
    }

    public GUI (MainAgent agent) {
        mainAgent = agent;
        initUI();
        loggingOutputStream = new LoggingOutputStream (rightPanelLoggingTextArea);
    }

    public void log (String s) {
        Runnable appendLine = () -> {
            rightPanelLoggingTextArea.append('[' + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] - " + s);
            rightPanelLoggingTextArea.setCaretPosition(rightPanelLoggingTextArea.getDocument().getLength());
        };
        SwingUtilities.invokeLater(appendLine);
    }

    public OutputStream getLoggingOutputStream() {
        return loggingOutputStream;
    }

    public void logLine (String s) {
        log(s + "\n");
    }

    public void setPlayersUI (String[] players) {
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String s : players) {
            listModel.addElement(s);
        }
        list.setModel(listModel);
    }

    public void initUI() {
        setTitle("GUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(600, 400));
        setPreferredSize(new Dimension(1000, 600));
        setJMenuBar(createMainMenuBar());
        setContentPane(createMainContentPane());
        pack();
        setVisible(true);
    }

    public void addGameResult(int round, String player1, String action1, 
                            String player2, String action2, double payoff1,
                            double payoff2, double indexValue, double inflation) {
        // Get the table model directly from our stored reference
        DefaultTableModel model = (DefaultTableModel) resultsTable.getModel();
        
        model.addRow(new Object[]{
            round,
            player1,
            action1,
            player2,
            action2,
            String.format("%.2f", payoff1),
            String.format("%.2f", payoff2),
            String.format("%.2f", indexValue),
            String.format("%.2f%%", inflation)
        });
    }

    public void updatePlayerStatus(ArrayList<MainAgent.PlayerInformation> players) {
        // To avoid java.lang.ArrayIndexOutOfBoundsException
        // ensure proper synchronization
        SwingUtilities.invokeLater(() -> {
            DefaultTableModel model = (DefaultTableModel) playerStatusTable.getModel();
            model.setRowCount(0); // Clear existing rows
            
            for (MainAgent.PlayerInformation player : players) {
                model.addRow(new Object[]{
                    player.aid.getLocalName(),
                    player.id,
                    player.accumulatedPayoff,
                    String.format("%.2f", (double)player.assets),
                    player.roundPayoff
                });
            }
        });
    }

    private Container createMainContentPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.anchor = GridBagConstraints.FIRST_LINE_START;
        gc.gridy = 0;
        gc.weightx = 0.5;
        gc.weighty = 0.5;

        //LEFT PANEL
        gc.gridx = 0;
        gc.weightx = 1;
        pane.add(createLeftPanel(), gc);

        //CENTRAL PANEL
        gc.gridx = 1;
        gc.weightx = 8;
        pane.add(createCentralPanel(), gc);

        //RIGHT PANEL
        gc.gridx = 2;
        gc.weightx = 8;
        pane.add(createRightPanel(), gc);
        return pane;
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();

        leftPanelRoundsLabel = new JLabel("Round 0 / null");
        JButton leftPanelNewButton = new JButton("New");
        leftPanelNewButton.addActionListener(actionEvent -> {
            mainAgent.newGame();
            
            // Clear the results table
            DefaultTableModel resultsTableModel = (DefaultTableModel) resultsTable.getModel();
            resultsTableModel.setRowCount(0); // Remove all rows from the table
        });
        JButton leftPanelStopButton = new JButton("Stop");
        leftPanelStopButton.addActionListener(actionEvent -> mainAgent.setStop());
        JButton leftPanelContinueButton = new JButton("Continue");
        leftPanelContinueButton.addActionListener(actionEvent -> mainAgent.setResume());

        leftPanelExtraInformation = new JLabel("Parameters:");
        String[] pairs = mainAgent.getGameParameters().split(";");

        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.FIRST_LINE_START;
        gc.gridx = 0;
        gc.weightx = 0.5;
        gc.weighty = 0.5;

        gc.gridy = 0;
        leftPanel.add(leftPanelRoundsLabel, gc);
        gc.gridy = 1;
        leftPanel.add(leftPanelNewButton, gc);
        gc.gridy = 2;
        leftPanel.add(leftPanelStopButton, gc);
        gc.gridy = 3;
        leftPanel.add(leftPanelContinueButton, gc);
        gc.gridy = 4;
        gc.weighty = 10;
        leftPanel.add(leftPanelExtraInformation, gc);
        StringBuilder sb = new StringBuilder();
        for (String pair : pairs) {
            String[] keyValue = pair.split("#");
            String key = keyValue[0];
            String value = keyValue[1];
            sb.append(key).append(" = ").append(value).append("\n");  // Añadir cada parámetro en una nueva línea
        }

        // Crear un JTextArea para mostrar la información
        JTextArea parametersTextArea = new JTextArea(5, 20);  // 5 filas y 20 columnas
        parametersTextArea.setText(sb.toString());  // Asignar el texto formateado
        parametersTextArea.setEditable(false);  // No editable
        parametersTextArea.setLineWrap(true);  // Habilitar el ajuste de línea
        parametersTextArea.setWrapStyleWord(true);  // Ajuste de palabra

        // Colocamos el JTextArea dentro de un JScrollPane
        JScrollPane scrollPane = new JScrollPane(parametersTextArea);

        // Añadir el JScrollPane al panel
        gc.gridy = 5;  // A partir de la fila 5 (debajo de 'Parameters')
        gc.weighty = 0.5;
        gc.fill = GridBagConstraints.HORIZONTAL;
        leftPanel.add(scrollPane, gc);

        return leftPanel;
    }

    private JPanel createCentralPanel() {
        JPanel centralPanel = new JPanel(new GridBagLayout());

        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 0.5;

        gc.fill = GridBagConstraints.BOTH;
        gc.anchor = GridBagConstraints.FIRST_LINE_START;
        gc.gridx = 0;

        gc.gridy = 0;
        gc.weighty = 1;
        centralPanel.add(createCentralTopSubpanel(), gc);
        gc.gridy = 1;
        gc.weighty = 4;
        centralPanel.add(createCentralBottomSubpanel(), gc);

        return centralPanel;
    }

    private JPanel createCentralTopSubpanel() {
        JPanel centralTopSubpanel = new JPanel(new GridBagLayout());

        DefaultListModel<String> listModel = new DefaultListModel<>();
        listModel.addElement("Empty");
        list = new JList<>(listModel); // Con list = new JList<>(new DefaultListModel<>()); iría igual
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(5);
        JScrollPane listScrollPane = new JScrollPane(list);

        JLabel info1 = new JLabel("Selected player info");
        JButton updatePlayersButton = new JButton("Update players");
        JButton removePlayersButton = new JButton("Remove player");
        JButton resetPlayersButton = new JButton("Reset player statistics");

        // Listerners config
        updatePlayersButton.addActionListener(actionEvent -> {
            mainAgent.updatePlayers();
        });
        removePlayersButton.addActionListener(actionEvent -> {
            mainAgent.printAgents();
            String selectedPlayerName = list.getSelectedValue();
            if (selectedPlayerName != null) {
                logLine("Buscando "+selectedPlayerName);
                // mainAgent.printAgentInfoByName(selectedPlayerName.split("@")[0]);
                mainAgent.removePlayer(selectedPlayerName.split("@")[0]);
            } else {
                logLine("No player selected.");
            }
        });
        resetPlayersButton.addActionListener(actionEvent -> {
            mainAgent.printAgents();
            String selectedPlayerName = list.getSelectedValue();
            if (selectedPlayerName != null) {
                logLine("Buscando "+selectedPlayerName);
                // mainAgent.printAgentInfoByName(selectedPlayerName.split("@")[0]);
                mainAgent.resetPlayer(selectedPlayerName.split("@")[0]);
            } else {
                logLine("No player selected.");
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // Evitar múltiples eventos para una sola selección
                String selectedPlayer = list.getSelectedValue();
                if (selectedPlayer != null) {
                    //String playerInfo = mainAgent.getPlayerInfoById(playerId);
                    info1.setText("Info: " + selectedPlayer); // Actualizar la etiqueta con la información del jugador seleccionado
                }
            }
        });

        // Layout config
        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 0.5;
        gc.weighty = 0.5;
        gc.anchor = GridBagConstraints.CENTER;

        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridheight = 666;
        gc.fill = GridBagConstraints.BOTH;
        centralTopSubpanel.add(listScrollPane, gc);
        gc.gridx = 1;
        gc.gridheight = 1;
        gc.fill = GridBagConstraints.NONE;
        centralTopSubpanel.add(info1, gc);
        gc.gridy = 1;
        centralTopSubpanel.add(updatePlayersButton, gc);
        gc.gridy = 2;
        centralTopSubpanel.add(removePlayersButton, gc);
        gc.gridy = 3;
        centralTopSubpanel.add(resetPlayersButton, gc);

        return centralTopSubpanel;
    }

    private JPanel createCentralBottomSubpanel() {
        JPanel centralBottomSubpanel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 0.5;
        gc.fill = GridBagConstraints.BOTH;
        gc.anchor = GridBagConstraints.FIRST_LINE_START;
        gc.gridx = 0;

        // Game results panel (top section)
        gc.gridy = 0;
        gc.weighty = 3; // Give more weight to the game results panel
        JPanel gameResultsPanel = new JPanel(new BorderLayout());
        
        // Create game results table
        String[] gameColumns = {
            "Round", "Player 1", "Action", "Player 2", "Action", 
            "P1 Payoff", "P2 Payoff", "Index Value", "Inflation"
        };
        DefaultTableModel gameTableModel = new DefaultTableModel(gameColumns, 0);
        resultsTable = new JTable(gameTableModel);
        
        // Create detailed view
        JTextArea detailedView = new JTextArea();
        detailedView.setEditable(false);
        
        // Create split pane for game results
        JSplitPane gamesSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(resultsTable),
            new JScrollPane(detailedView)
        );
        gamesSplitPane.setResizeWeight(0.7);
        
        // Add table selection listener for game results
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = resultsTable.getSelectedRow();
                if (row != -1) {
                    StringBuilder detail = new StringBuilder();
                    detail.append("Detailed Game Information:\n\n");
                    detail.append("Round: ").append(gameTableModel.getValueAt(row, 0)).append("\n");
                    detail.append("Player 1: ").append(gameTableModel.getValueAt(row, 1)).append("\n");
                    detail.append("Action 1: ").append(gameTableModel.getValueAt(row, 2)).append("\n");
                    detail.append("Player 2: ").append(gameTableModel.getValueAt(row, 3)).append("\n");
                    detail.append("Action 2: ").append(gameTableModel.getValueAt(row, 4)).append("\n");
                    detail.append("Payoff 1: ").append(gameTableModel.getValueAt(row, 5)).append("\n");
                    detail.append("Payoff 2: ").append(gameTableModel.getValueAt(row, 6)).append("\n");
                    detail.append("Index Value: ").append(gameTableModel.getValueAt(row, 7)).append("\n");
                    detail.append("Inflation Rate: ").append(gameTableModel.getValueAt(row, 8)).append("\n");
                    
                    detailedView.setText(detail.toString());
                }
            }
        });
        
        gameResultsPanel.add(gamesSplitPane, BorderLayout.CENTER);
        centralBottomSubpanel.add(gameResultsPanel, gc);

        // Player status panel (bottom section)
        gc.gridy = 1;
        gc.weighty = 1; // Less weight than the game results panel
        JPanel playerStatusPanel = new JPanel(new BorderLayout());
        
        // Create player status table
        String[] playerColumns = {
            "Player Name", "ID", "Accumulated Payoff", "Assets", "Round Payoff"
        };
        DefaultTableModel playerTableModel = new DefaultTableModel(playerColumns, 0);
        playerStatusTable = new JTable(playerTableModel);
        
        // Add a title for the player status section
        JLabel statusLabel = new JLabel("Player Status", SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        playerStatusPanel.add(statusLabel, BorderLayout.NORTH);
        playerStatusPanel.add(new JScrollPane(playerStatusTable), BorderLayout.CENTER);
        
        // Add the player status panel
        centralBottomSubpanel.add(playerStatusPanel, gc);

        return centralBottomSubpanel;
    }

    private JPanel createRightPanel() {
        rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.weighty = 1d;
        c.weightx = 1d;

        rightPanelLoggingTextArea = new JTextArea("");
        rightPanelLoggingTextArea.setEditable(false);
        JScrollPane jScrollPane = new JScrollPane(rightPanelLoggingTextArea);
        rightPanel.add(jScrollPane, c);
        return rightPanel;
    }

    private JMenuBar createMainMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu menuFile = new JMenu("File");
        JMenuItem exitFileMenu = new JMenuItem("Exit");
        exitFileMenu.setToolTipText("Exit application");
        exitFileMenu.addActionListener(this);

        JMenuItem newGameFileMenu = new JMenuItem("New Game");
        newGameFileMenu.setToolTipText("Start a new game");
        newGameFileMenu.addActionListener(this);

        menuFile.add(newGameFileMenu);
        menuFile.add(exitFileMenu);
        menuBar.add(menuFile);

        JMenu menuEdit = new JMenu("Edit");
        JMenuItem resetPlayerEditMenu = new JMenuItem("Reset Players");
        resetPlayerEditMenu.setToolTipText("Reset all player");
        resetPlayerEditMenu.setActionCommand("reset_players");
        resetPlayerEditMenu.addActionListener(actionEvent -> mainAgent.resetPlayers());

        JMenuItem parametersEditMenu = new JMenuItem("Parameters");
        parametersEditMenu.setToolTipText("Modify the parameters of the game");
        String[] pairs = mainAgent.getGameParameters().split(";");
        parametersEditMenu.addActionListener(actionEvent -> {
            // Crear un panel para los campos de entrada
            JPanel panel = new JPanel(new GridLayout(2, 2));

            // Crear etiquetas y campos para las entradas
            JLabel roundsLabel = new JLabel("Number of rounds (R): ");
            JTextField roundsField = new JTextField(String.valueOf(pairs[2].split("#")[1]));
            JLabel commissionLabel = new JLabel("Commission fee (F): ");
            JTextField commissionField = new JTextField(String.valueOf(pairs[5].split("#")[1]));

            panel.add(roundsLabel);
            panel.add(roundsField);
            panel.add(commissionLabel);
            panel.add(commissionField);

            // Mostrar cuadro de diálogo
            int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Edit Parameters",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );

            // Validar y actualizar parámetros si el usuario presionó OK
            if (result == JOptionPane.OK_OPTION) {
                try {
                    int newRounds = Integer.parseInt(roundsField.getText());
                    double newCommission = Double.parseDouble(commissionField.getText());

                    // Validar las restricciones
                    if (newRounds > 100 && newCommission >= 0 && newCommission <= 1) {
                        mainAgent.updateNumberOfRounds(newRounds);
                        mainAgent.updateCommissionFee(newCommission);
                        logLine("Parameters updated: R = " + newRounds + ", F = " + newCommission);
                    } else {
                        logLine("Invalid parameters: R must be > 100 and F must be between 0 and 1.");
                    }
                } catch (NumberFormatException e) {
                    logLine("Error: Invalid input. Please enter numeric values for R and F.");
                }
            }
        });

        menuEdit.add(resetPlayerEditMenu);
        menuEdit.add(parametersEditMenu);
        menuBar.add(menuEdit);

        JMenu menuRun = new JMenu("Run");

        JMenuItem newRunMenu = new JMenuItem("New");
        newRunMenu.setToolTipText("Starts a new series of games");
        newRunMenu.addActionListener(this);

        JMenuItem stopRunMenu = new JMenuItem("Stop");
        stopRunMenu.setToolTipText("Stops the execution of the current round");
        stopRunMenu.addActionListener(actionEvent -> mainAgent.setStop());

        JMenuItem continueRunMenu = new JMenuItem("Continue");
        continueRunMenu.setToolTipText("Resume the execution");
        continueRunMenu.addActionListener(actionEvent -> mainAgent.setResume());

        JMenuItem roundNumberRunMenu = new JMenuItem("Number of rounds");
        roundNumberRunMenu.setToolTipText("Change the number of rounds");
        roundNumberRunMenu.addActionListener(actionEvent ->
            logLine(JOptionPane.showInputDialog(new Frame("Configure rounds"), "How many rounds?") + " rounds")
        );

        JMenu myName = new JMenu("Student");
        JMenuItem menuHelpButton = new JMenuItem("Autor: Renato Josue Bedriñana Cárdenas");
        myName.add(menuHelpButton);

        menuRun.add(newRunMenu);
        menuRun.add(stopRunMenu);
        menuRun.add(continueRunMenu);
        menuRun.add(roundNumberRunMenu);
        menuBar.add(menuRun);
        menuBar.add(myName);

        JMenu menuWindow = new JMenu("Window");

        JCheckBoxMenuItem toggleVerboseWindowMenu = new JCheckBoxMenuItem("Verbose", true);
        toggleVerboseWindowMenu.addActionListener(actionEvent -> rightPanel.setVisible(toggleVerboseWindowMenu.getState()));

        menuWindow.add(toggleVerboseWindowMenu);
        menuBar.add(menuWindow);

        return menuBar;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JButton) {
            JButton button = (JButton) e.getSource();
            logLine("Button " + button.getText());
        } else if (e.getSource() instanceof JMenuItem) {
            JMenuItem menuItem = (JMenuItem) e.getSource();
            logLine("Menu " + menuItem.getText());
        }
    }

    public class LoggingOutputStream extends OutputStream {
        private JTextArea textArea;

        public LoggingOutputStream(JTextArea jTextArea) {
            textArea = jTextArea;
        }

        @Override
        public void write(int i) throws IOException {
            textArea.append(String.valueOf((char) i));
            textArea.setCaretPosition(textArea.getDocument().getLength());
        }
    }
}
