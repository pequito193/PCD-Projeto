package client;

import model.Question;
import model.Quiz;
import model.GameState;
import utils.JsonLoader;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ClientGUI {
    private JFrame frame;
    private JLabel lblQuestion;
    private JButton[] optionButtons;
    private JLabel lblTimer;
    private JLabel lblScore;
    private Timer swingTimer;
    private int timeLeft = 30; // segundos
    private GameState gameState;
    private int myTeam = 0; // testamos como se o jogador pertencesse à equipa 0


    public ClientGUI(GameState gs) {
        this.gameState = gs;
        createAndShowGUI();
        loadQuestionToUI();
    }

    private void createAndShowGUI() {
        frame = new JFrame("IsKahoot - Fase 1-3 (cliente)"); 
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700,400);
        frame.setLayout(new BorderLayout());

        lblQuestion = new JLabel("Pergunta", SwingConstants.CENTER);
        lblQuestion.setFont(new Font("SansSerif", Font.BOLD, 18));
        frame.add(lblQuestion, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(2,2,10,10));
        optionButtons = new JButton[4];
        for (int i=0;i<4;i++) {
            optionButtons[i] = new JButton("Opção " + (i+1));
            final int idx = i+1;
            optionButtons[i].addActionListener(e -> submitAnswer(idx));
            center.add(optionButtons[i]);
        }
        frame.add(center, BorderLayout.CENTER);


        JPanel south = new JPanel(new FlowLayout());
        lblTimer = new JLabel("Tempo: 30");
        lblScore = new JLabel("Placar: " + gameState.getTeamScores());
        JButton btnNext = new JButton("Proxima Pergunta");
        btnNext.addActionListener(e -> {
            if (gameState.nextQuestion()) {
                loadQuestionToUI();
            } else {
                JOptionPane.showMessageDialog(frame, "Fim do quiz! Placar final: " + gameState.getTeamScores());
            }
        });
        south.add(lblTimer);
        south.add(lblScore);
        south.add(btnNext);
        frame.add(south, BorderLayout.SOUTH);

        frame.setVisible(true);
    }



    private void loadQuestionToUI() {
        Question q = gameState.getCurrentQuestion();

        if (q == null) {
            lblQuestion.setText("Sem mais perguntas.");
            for (JButton b: optionButtons) b.setEnabled(false);
            if (swingTimer != null) swingTimer.stop();
            return;
        }

        lblQuestion.setText(String.format("[%d/%d] %s", gameState.getCurrentIndex()+1, gameState.getTotalQuestions(), q.getQuestion()));
        List<String> opts = q.getOptions();

        for (int i=0;i<4;i++) {
            optionButtons[i].setBackground(Color.LIGHT_GRAY);
            optionButtons[i].setText((i < opts.size()) ? opts.get(i) : "---");
            optionButtons[i].setEnabled(i < opts.size());
        }

        // Fazemos reset do timer
        if (swingTimer != null) {
            swingTimer.stop();
        }

        timeLeft = 30;
        lblTimer.setText("Tempo: " + timeLeft);

        swingTimer = new Timer(1000, ev -> {
            timeLeft--;
            lblTimer.setText("Tempo: " + timeLeft);
            if (timeLeft <= 0) {
                swingTimer.stop();
                JOptionPane.showMessageDialog(frame, "Tempo esgotado para a pergunta.");
            }
        });

        swingTimer.start();
    }

    private void submitAnswer(int optionIdx) {
        Question q = gameState.getCurrentQuestion();
        JButton chosen = optionButtons[optionIdx - 1];
        JButton answer = optionButtons[q.getCorrect() - 1];

        if (q == null) return;
        if (optionIdx == q.getCorrect()) {
            chosen.setBackground(Color.GREEN);
            gameState.addPointsToTeam(myTeam, q.getPoints());
            JOptionPane.showMessageDialog(frame, q.getPoints() + " pontos.");
            lblScore.setText("Placar: " + gameState.getTeamScores());
        } else {
            chosen.setBackground(Color.RED);
            answer.setBackground(Color.GREEN);
            JOptionPane.showMessageDialog(frame, "0 pontos.");
        }

        // Esperar pela próxima pergunta
        if (swingTimer != null) swingTimer.stop();
        for (JButton b: optionButtons) b.setEnabled(false);
    }

    public static void main(String[] args) throws Exception {
        String jsonPath = "data/questions.json";
        if (args.length > 0) jsonPath = args[0];
        JsonLoader loader = new JsonLoader(jsonPath);
        if (loader.getQuizzes().isEmpty()) {
            System.err.println("Ficheiro JSON sem quizzes."); System.exit(1);
        }
        Quiz quiz = loader.getQuizzes().get(0);
        GameState gs = new GameState(quiz, 2); // testamos com 2 equipas
        SwingUtilities.invokeLater(() -> new ClientGUI(gs));
    }
}
