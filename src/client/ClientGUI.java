package client;

import common.Msg;
import model.Question;
import javax.swing.*;
import java.awt.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class ClientGUI {
    private JFrame frame;
    private JLabel lblQuestion;
    private JButton[] optionButtons;
    private JLabel lblStatus;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private String username;
    private String teamId;
    private String gameId; // NOVO

    public ClientGUI(String serverAddress, int serverPort, String gameId, String teamId, String username) {
        this.username = username;
        this.teamId = teamId;
        this.gameId = gameId; // Armazenar Game ID
        createAndShowGUI();
        connectToServer(serverAddress, serverPort);
    }

    private void createAndShowGUI() {
        // Incluir Game ID no título
        frame = new JFrame("IsKahoot - " + username + " (Jogo " + gameId + " | Equipa " + teamId + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        lblQuestion = new JLabel("A ligar ao servidor...", SwingConstants.CENTER);
        lblQuestion.setFont(new Font("SansSerif", Font.BOLD, 16));
        frame.add(lblQuestion, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(2, 2, 10, 10));
        optionButtons = new JButton[4];
        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JButton("Opção " + (i + 1));
            optionButtons[i].setEnabled(false);
            final int idx = i + 1; // 1-based index
            optionButtons[i].addActionListener(e -> submitAnswer(idx));
            center.add(optionButtons[i]);
        }
        frame.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout());
        lblStatus = new JLabel("Estado: À espera");
        south.add(Box.createHorizontalStrut(20));
        south.add(lblStatus);
        frame.add(south, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void connectToServer(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Envia LOGIN concatenado: GameID|TeamID|Username
            // Este formato permite ao servidor extrair todos os dados necessários.
            String loginContent = gameId + "|" + teamId + "|" + username;
            out.writeObject(new Msg(Msg.Type.LOGIN, loginContent));

            new Thread(new ServerListener()).start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Erro de Conexão: " + e.getMessage());
            System.exit(1);
        }
    }

    private void submitAnswer(int optionIdx) {
        try {
            out.writeObject(new Msg(Msg.Type.SEND_ANSWER, optionIdx));

            // Bloqueia botões após responder
            for (JButton b : optionButtons) b.setEnabled(false);
            lblStatus.setText("Resposta enviada!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Object received = in.readObject();
                    if (received instanceof Msg) {
                        SwingUtilities.invokeLater(() -> processMessage((Msg) received));
                    }
                }
            } catch (Exception e) {
                System.out.println("Desconectado do servidor.");
            }
        }
    }

    private void processMessage(Msg msg) {
        switch (msg.type) {
            case LOGIN_OK:
                lblQuestion.setText("Login OK! À espera que o jogo (" + gameId + ") comece...");
                break;

            case LOGIN_ERROR:
                JOptionPane.showMessageDialog(frame, "Erro de Login: " + (String) msg.content);
                System.exit(1);
                break;

            case NEW_QUESTION:
                if (msg.content instanceof Question) {
                    Question q = (Question) msg.content;
                    lblQuestion.setText("<html><div style='text-align: center;'>" + q.getQuestion() + "</div></html>");

                    List<String> opts = q.getOptions();
                    for (int i = 0; i < 4; i++) {
                        if (i < opts.size()) {
                            optionButtons[i].setText(opts.get(i));
                            optionButtons[i].setEnabled(true);
                        } else {
                            optionButtons[i].setText("");
                            optionButtons[i].setEnabled(false);
                        }
                    }
                    lblStatus.setText("Responde rápido!");
                }
                break;

            case UPDATE_SCORE:
                lblStatus.setText((String) msg.content);
                lblQuestion.setText("À espera da próxima pergunta...");
                break;

            case GAME_OVER:
                lblQuestion.setText("FIM DO JOGO!");
                lblStatus.setText((String) msg.content);
                for (JButton b : optionButtons) b.setEnabled(false);
                break;
        }
    }

    public static void main(String[] args) {
        // Agora precisamos de 5 argumentos: <IP> <PORT> <JOGO> <EQUIPA> <USERNAME>
        if (args.length < 5) {
            System.out.println("Uso correto: java client.ClientGUI <IP> <PORT> <JOGO> <EQUIPA> <USERNAME>");
            // Exemplo de teste (código 'TEST')
            SwingUtilities.invokeLater(() -> new ClientGUI("localhost", 12345, "TEST", "EqA", "Player1"));
            // return;
        } else {
            String ip = args[0];
            int port = Integer.parseInt(args[1]);
            String gameId = args[2]; // Terceiro argumento é o código do jogo
            String teamId = args[3];
            String username = args[4];

            SwingUtilities.invokeLater(() -> new ClientGUI(ip, port, gameId, teamId, username));
        }
    }
}