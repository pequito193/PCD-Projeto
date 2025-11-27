package client;

import common.Msg;
import model.Question; // Ainda precisamos disto para o cast
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
    private JLabel lblTimer;
    private JLabel lblScore;
    
    // REDE: Variáveis novas
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    
    // ESTADO LOCAL (Apenas para visualização)
    private int myPoints = 0; 

    public ClientGUI(String serverAddress, int serverPort) {
        createAndShowGUI();
        connectToServer(serverAddress, serverPort);
    }

    private void createAndShowGUI() {
        // (IGUAL AO QUE TINHAS ANTES, só removi o botão "Próxima Pergunta" 
        // porque agora é o servidor que decide quando muda a pergunta)
        
        frame = new JFrame("IsKahoot - Cliente");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 400);
        frame.setLayout(new BorderLayout());

        lblQuestion = new JLabel("À espera do jogo...", SwingConstants.CENTER);
        lblQuestion.setFont(new Font("SansSerif", Font.BOLD, 18));
        frame.add(lblQuestion, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(2, 2, 10, 10));
        optionButtons = new JButton[4];
        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JButton("Opção " + (i + 1));
            optionButtons[i].setEnabled(false); // Começam desativados
            final int idx = i + 1;
            optionButtons[i].addActionListener(e -> submitAnswer(idx));
            center.add(optionButtons[i]);
        }
        frame.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout());
        lblTimer = new JLabel("Tempo: --");
        lblScore = new JLabel("Meus Pontos: 0"); // Simplificado para teste
        south.add(lblTimer);
        south.add(lblScore);
        frame.add(south, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void connectToServer(String host, int port) {
        try {
            socket = new Socket(host, port);
            // Ordem importante: Output primeiro!
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Enviar LOGIN (Exemplo fixo para teste)
            out.writeObject(new Msg(Msg.Type.LOGIN, "JogadorTeste"));

            // Iniciar a Thread que escuta o servidor
            new Thread(new ServerListener()).start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Erro ao ligar ao servidor: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // --- Lógica de Envio ---
    private void submitAnswer(int optionIdx) {
        try {
            // Em vez de verificar se está certo, enviamos a escolha ao servidor
            out.writeObject(new Msg(Msg.Type.SEND_ANSWER, optionIdx));
            
            // Desativar botões para não responder duas vezes
            for (JButton b : optionButtons) b.setEnabled(false);
            lblQuestion.setText("Resposta enviada. À espera...");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Lógica de Receção (Thread separada) ---
    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    // Bloqueia aqui à espera de mensagens do servidor
                    Object received = in.readObject();
                    if (received instanceof Msg) {
                        Msg msg = (Msg) received;
                        
                        // SwingUtilities.invokeLater garante que mexemos na GUI na thread correta
                        SwingUtilities.invokeLater(() -> processMessage(msg));
                    }
                }
            } catch (Exception e) {
                System.out.println("Ligação perdida.");
            }
        }
    }

    // O "Cérebro" do Cliente agora só reage a ordens
    private void processMessage(Msg msg) {
        System.out.println("Cliente recebeu: " + msg); // Debug

        switch (msg.type) {
            case LOGIN_OK:
                lblQuestion.setText("Login aceite! À espera da ronda...");
                break;

            case NEW_QUESTION: // Servidor mandou uma pergunta!
                // Assumindo que msg.content é um objeto Question ou uma String formatada
                // Para simplificar agora, vamos assumir que o servidor manda o objeto Question
                if (msg.content instanceof Question) {
                    Question q = (Question) msg.content;
                    lblQuestion.setText(q.getQuestion());
                    List<String> opts = q.getOptions();
                    for (int i = 0; i < 4; i++) {
                        optionButtons[i].setText((i < opts.size()) ? opts.get(i) : "");
                        optionButtons[i].setEnabled(i < opts.size());
                        optionButtons[i].setBackground(null); // Reset cor
                    }
                }
                break;
                
            case GAME_OVER:
                lblQuestion.setText("Fim do Jogo!");
                for (JButton b : optionButtons) b.setEnabled(false);
                break;
                
            // Outros casos: UPDATE_SCORE, etc.
        }
    }

    public static void main(String[] args) {
        // Agora o main é limpo: só arranca a GUI e liga ao localhost
        SwingUtilities.invokeLater(() -> new ClientGUI("localhost", 12345));
    }
}