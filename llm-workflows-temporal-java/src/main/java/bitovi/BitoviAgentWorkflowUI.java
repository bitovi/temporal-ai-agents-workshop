package bitovi;

import java.util.UUID;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

import bitovi.workflows.AgentGoal.AgentGoalTypes.AgentGoalConversationEntry;
import bitovi.workflows.AgentGoal.AgentGoalTypes.AgentGoalConversationHistory;
import bitovi.workflows.AgentGoal.AgentGoalWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Provides a basic Swing UI to sending chat messages to the AgentGoalWorkflow.
 */
public class BitoviAgentWorkflowUI {

    private static AgentGoalWorkflow workflow;
    private static WorkflowServiceStubs service;

    // UI Components
    private static JFrame frame;
    private static JTextArea messageHistoryArea;
    private static JTextField messageInputField;
    private static JButton sendButton;
    private static JButton confirmButton;

    // Polling configuration
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 5; // Default 5 seconds
    private static int pollIntervalSeconds = DEFAULT_POLL_INTERVAL_SECONDS;
    private static Timer pollTimer;

    public static void main(String[] args) {

        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("localhost:7233")
                .build();

        BitoviAgentWorkflowUI.service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

        WorkflowClient client = WorkflowClient.newInstance(service);

        // Run the agent workflow demo
        String workflowId = "agent-workflow" + UUID.randomUUID().toString();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue("default")
                .build();

        BitoviAgentWorkflowUI.workflow = client.newWorkflowStub(AgentGoalWorkflow.class, options);
        // Start the workflow
        WorkflowClient.start(BitoviAgentWorkflowUI.workflow::run,
                new AgentGoalWorkflow.CombinedWorkflowInput(
                        null,
                        null));

        // Signal the workflow with a goal, do this via the Chat UI instead.
        // BitoviAgentWorkflowUI.workflow.prompt("Hello, I need help with my project.");

        BitoviAgentWorkflowUI.createInterface();
    }

    public static void createInterface() {
        // Create the main frame
        frame = new JFrame("Temporal AI Agent Chat");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Add window listener to handle close event
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                BitoviAgentWorkflowUI.exit();
            }
        });

        // Create message history area with scroll pane
        messageHistoryArea = new JTextArea();
        messageHistoryArea.setEditable(false);
        messageHistoryArea.setWrapStyleWord(true);
        messageHistoryArea.setLineWrap(true);
        messageHistoryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        messageHistoryArea.setBackground(Color.WHITE);
        messageHistoryArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(messageHistoryArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setPreferredSize(new Dimension(780, 500));

        // Create input panel for message input and send button
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        messageInputField = new JTextField();
        messageInputField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        messageInputField.setPreferredSize(new Dimension(600, 30));

        sendButton = new JButton("Send");
        sendButton.setPreferredSize(new Dimension(80, 30));
        sendButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        confirmButton = new JButton("Confirm");
        confirmButton.setPreferredSize(new Dimension(80, 30));
        confirmButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        // Add components to input panel
        inputPanel.add(messageInputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.add(confirmButton, BorderLayout.WEST);

        // Add components to main frame
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);

        // Set up event handlers
        setupEventHandlers();

        // Center the frame on screen
        frame.setLocationRelativeTo(null);

        // Show the frame
        frame.setVisible(true);

        // Focus on the input field
        messageInputField.requestFocusInWindow();

        // Start polling for conversation history updates
        startPolling();
    }

    private static void startPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
        }

        // Create timer that polls every pollIntervalSeconds
        int intervalMs = pollIntervalSeconds * 1000;
        pollTimer = new Timer(intervalMs, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Run polling in background thread to avoid blocking UI
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        pollConversationHistory();
                    }
                });
            }
        });

        pollTimer.start();
        System.out.println("Started polling conversation history every " + pollIntervalSeconds + " seconds");
    }

    public static void setPollInterval(int seconds) {
        if (seconds >= 1 && seconds <= 60) { // Reasonable bounds: 1-60 seconds
            pollIntervalSeconds = seconds;
            // Restart polling with new interval if already running
            if (pollTimer != null && pollTimer.isRunning()) {
                startPolling();
            }
            System.out.println("Poll interval set to " + seconds + " seconds");
        } else {
            System.err.println("Poll interval must be between 1 and 60 seconds");
        }
    }

    private static void stopPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
            System.out.println("Stopped polling conversation history");
        }
    }

    private static void setupEventHandlers() {
        // Send button click handler
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Confirm button click handler
        confirmButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmToolCall();
            }
        });

        // Enter key handler for message input field
        messageInputField.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }
        });
    }

    private static void sendMessage() {
        String message = messageInputField.getText().trim();
        if (!message.isEmpty()) {
            // Send message to workflow
            sendWorkflowMessage(message);

            // Clear input field
            messageInputField.setText("");

            // The conversation history will be updated automatically by polling
        }
    }

    private static void addMessageToHistory(String message) {
        if (messageHistoryArea != null) {
            messageHistoryArea.append(message + "\n\n");
            // Auto-scroll to bottom
            messageHistoryArea.setCaretPosition(messageHistoryArea.getDocument().getLength());
        }
    }

    public static void sendWorkflowMessage(String message) {
        if (BitoviAgentWorkflowUI.workflow != null) {
            BitoviAgentWorkflowUI.workflow.prompt(message);
        } else {
            System.out.println("Workflow is not initialized.");
        }
    }

    public static void confirmToolCall() {
        if (BitoviAgentWorkflowUI.workflow != null) {
            BitoviAgentWorkflowUI.workflow.confirm();
        } else {
            System.out.println("Workflow is not initialized.");
        }
    }

    public static void pollConversationHistory() {
        if (BitoviAgentWorkflowUI.workflow != null) {
            try {
                // Query the workflow for the conversation history
                AgentGoalConversationHistory history = BitoviAgentWorkflowUI.workflow
                        .getConversationHistory();
                if (history != null) {
                    // Clear the message history area
                    messageHistoryArea.setText("");
                    // Add each message to the history area
                    for (AgentGoalConversationEntry message : history.messages()) {
                        String displayMessage = formatMessageContent(message);
                        addMessageToHistory(displayMessage);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error polling conversation history: " + e.getMessage());
            }
        }
    }

    private static String formatMessageContent(AgentGoalConversationEntry message) {
        String actor = message.actor();
        String response = message.response();
        // For normal string responses, return as-is
        return actor + ": " + (response != null ? response : "[No response]");
    }

    public static void exit() {
        try {
            // Stop polling before exit
            stopPolling();

            if (BitoviAgentWorkflowUI.workflow != null) {
                BitoviAgentWorkflowUI.workflow.disconnect();
            }
            BitoviAgentWorkflowUI.service.shutdown();
        } catch (Exception e) {
            System.err.println("Error during exit: " + e.getMessage());
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
        System.out.println("Exiting application...");
        System.exit(0);
    }
}
