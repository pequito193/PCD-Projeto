package Server;

import common.Msg;
import model.GameState;
import model.Question;
import model.Quiz;
import utils.JsonLoader;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final int PORT = 12345;
    private static final int TEMPO_ESPERA = 3000;     // 3s
    private GameState gameState;
    private final List<DealWithClient> clients = new ArrayList<>();
    
    // Variável de concorrência para a pergunta atual
    private ModifiedCountDownLatch currentLatch;

    public Server(String jsonPath) {
        try {
            JsonLoader loader = new JsonLoader(jsonPath);
            // Assume que existe pelo menos um quiz
            Quiz quiz = loader.getQuizzes().get(0);
            // Inicializa com 2 equipas (valor fixo para teste, podes mudar depois)
            this.gameState = new GameState(quiz, 2);
            System.out.println("Jogo carregado: " + quiz.getName());

            startConnectionLoop();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startConnectionLoop() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("À espera de jogadores na porta " + PORT + "...");
                while (true) {
                    Socket socket = serverSocket.accept();
                    DealWithClient client = new DealWithClient(socket, this);

                    synchronized (clients) {
                        clients.add(client);
                    }
                    client.start();
                    System.out.println("Novo jogador ligado. Total: " + clients.size());

                    // Para testes: arranca o jogo quando tivermos 2 jogadores
                    if (clients.size() == 2) { 
                        new Thread(this::startGame).start();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startGame() {
        try {
            System.out.println("O jogo vai começar em 3 segundos...");
            Thread.sleep(TEMPO_ESPERA);

            // CICLO DO JOGO (percorre todas as perguntas)
            // Nota: O GameState controla o índice.
            // Vamos fazer um loop enquanto houver perguntas.
            // Para simplificar, vou iterar sobre a lista do quiz diretamente
            List<Question> questions = gameState.getQuiz().getQuestions();
            
            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                System.out.println("A iniciar pergunta: " + q.getQuestion());

                // 1. Criar o Latch para esta ronda (espera por N respostas)
                synchronized (clients) {
                    currentLatch = new ModifiedCountDownLatch(clients.size());
                }

                // 2. Enviar pergunta a todos
                broadcast(new Msg(Msg.Type.NEW_QUESTION, q));

                // 3. BLOQUEAR: Esperar 10 segundos ou até todos responderem
                System.out.println("À espera de respostas...");
                currentLatch.startTimer(); 
                System.out.println("Fim do tempo ou todos responderam.");

                // 4. (Opcional) Enviar placar atualizado aqui
                // broadcast(new Msg(Msg.Type.UPDATE_SCORE, gameState.getTeamScores()));

                // 5. Preparar próxima pergunta
                gameState.nextQuestion();
                
                // Pequena pausa entre perguntas
                Thread.sleep(TEMPO_ESPERA);
            }

            System.out.println("Jogo Terminado. A calcular pontuações...");

            // 1. Construir uma String com a classificação final
            StringBuilder sb = new StringBuilder("<html><div style='text-align: center;'>FIM DO JOGO!<br><br>");
            List<Integer> scores = gameState.getTeamScores();

            for (int i = 0; i < scores.size(); i++) {
                // Nota: Como simplificámos e não temos nomes de equipas guardados, usamos "Equipa X"
                sb.append("Equipa ").append(i).append(": ").append(scores.get(i)).append(" pontos<br>");
            }
            sb.append("</div></html>");

            // 2. Enviar a classificação dentro da mensagem de GAME_OVER
            broadcast(new Msg(Msg.Type.GAME_OVER, sb.toString()));

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void broadcast(Msg msg) {
        synchronized (clients) {
            for (DealWithClient client : clients) {
                client.send(msg);
            }
        }
    }

    public void removeClient(DealWithClient client) {
        synchronized (clients) {
            clients.remove(client);
        }
    }

    // Métodos de acesso para o DealWithClient usar
    public ModifiedCountDownLatch getCurrentLatch() {
        return currentLatch;
    }

    public GameState getGameState() {
        return gameState;
    }
    
    // Método auxiliar para obter ID da equipa de um jogador (lógica simplificada)
    public int getTeamIdForPlayer(DealWithClient client) {
        // Num cenário real, terias um mapa de Jogador -> Equipa.
        // Aqui vou usar o índice da lista para alternar: Jogador 0 -> Equipa 0, Jogador 1 -> Equipa 1...
        // Isto é só para o teste funcionar.
        synchronized(clients) {
            return clients.indexOf(client) % 2; 
        }
    }

    public static void main(String[] args) {
        // Certifica-te que o caminho do JSON está correto
        new Server("data/questions.json"); 
    }
}