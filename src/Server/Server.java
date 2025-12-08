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
    private ModifiedCountDownLatch currentLatch;
    private TeamBarrier currentBarrier;
    private boolean isTeamRound = false;
    

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
                    if (clients.size() == 3) { 
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

            List<Question> questions = gameState.getQuiz().getQuestions();

            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                
                // Regra do Enunciado: Alternar entre Individual e Equipa
                // Índice Par (0, 2...) = Individual
                // Índice Ímpar (1, 3...) = Equipa
                this.isTeamRound = (i % 2 != 0); 

                System.out.println("\n--- PERGUNTA " + (i + 1) + " (" + (isTeamRound ? "EQUIPA" : "INDIVIDUAL") + ") ---");

                // DEBUG: Ver quantas pessoas o servidor "vê"
                synchronized(clients) {
                    System.out.println("Jogadores ativos: " + clients.size() + " jogadores.");
                    
                    if (isTeamRound) {
                        currentLatch = null;
                        currentBarrier = new TeamBarrier(clients.size());
                    } else {
                        currentBarrier = null;
                        currentLatch = new ModifiedCountDownLatch(clients.size());
                    }
                }
                // 2. Enviar a pergunta a todos
                // Podes adicionar "(EQUIPA)" ao texto da pergunta para avisar os jogadores
                if (isTeamRound) {
                    // Pequeno truque visual para o cliente saber que é de equipa
                    Question qTeam = new Question(
                        "[EQUIPA] " + q.getQuestion(), 
                        q.getPoints(), 
                        q.getCorrect(), 
                        q.getOptions()
                    );
                    broadcast(new Msg(Msg.Type.NEW_QUESTION, qTeam));
                } else {
                    broadcast(new Msg(Msg.Type.NEW_QUESTION, q));
                }

                // 3. BLOQUEAR: Esperar pelas respostas (30 segundos)
                // O servidor fica preso aqui até o tempo acabar ou todos responderem
                if (isTeamRound) {
                    System.out.println("Servidor à espera na Barreira (Modo Equipa)...");
                    currentBarrier.startTimer(); 
                    // AQUI entra a lógica de calcular pontos de equipa (todos certos = dobro)
                    // Podes implementar o cálculo aqui ou deixar o DealWithClient tratar
                    System.out.println("Barreira libertada (ou tempo esgotou).");
                    // --- CÁLCULO DE PONTOS DE EQUIPA ---
                    // 1. Organizar jogadores por equipa para facilitar a verificação
                    // (Num sistema real usarias um Map<String, List>, aqui usamos arrays simples baseados no ID numérico)
                    int numTeams = gameState.getTeamScores().size();
                    boolean[] teamAllCorrect = new boolean[numTeams];
                    boolean[] teamAtLeastOneCorrect = new boolean[numTeams];
                    
                    // Inicializar arrays como "verdadeiro" para a lógica de verificação
                    for(int t=0; t<numTeams; t++) {
                        teamAllCorrect[t] = true;   // Assumimos que todos acertaram até prova em contrário
                        teamAtLeastOneCorrect[t] = false;
                    }
                    synchronized(clients) {
                        for (DealWithClient client : clients) {
                            int teamId = getTeamIdForPlayer(client);
                            boolean correct = client.isLastAnswerCorrect(); // Método que criámos no passo anterior
                            
                            if (correct) {
                                teamAtLeastOneCorrect[teamId] = true;
                            } else {
                                teamAllCorrect[teamId] = false; // Um falhou, logo a equipa não tem bónus
                            }
                        }
                    }
                    // 2. Distribuir pontos
                    int basePoints = q.getPoints();
                    
                    for (int t = 0; t < numTeams; t++) {
                        int pointsToAdd = 0;
                        
                        if (teamAllCorrect[t]) {
                            // Todos acertaram: DUPLA COTAÇÃO
                            pointsToAdd = basePoints * 2;
                            System.out.println("Equipa " + t + ": TODOS acertaram! (Pontos x2: " + pointsToAdd + ")");
                        } else if (teamAtLeastOneCorrect[t]) {
                            // Alguém falhou, mas pelo menos um acertou: COTAÇÃO NORMAL (sem bónus)
                            pointsToAdd = basePoints;
                            System.out.println("Equipa " + t + ": Acertaram parcialmente. (Pontos normais: " + pointsToAdd + ")");
                        } else {
                            System.out.println("Equipa " + t + ": Ninguém acertou.");
                        }

                        if (pointsToAdd > 0) {
                            gameState.addPointsToTeam(t, pointsToAdd);
                        }
                    }
                } else {
                    System.out.println("Servidor à espera no Latch (Modo Individual)...");
                    currentLatch.startTimer();
                    System.out.println("Latch libertado (ou tempo esgotou).");
                }

                // 4. Pausa antes da próxima pergunta e avança índice
                gameState.nextQuestion();
                
                // Pequena pausa entre perguntas
                Thread.sleep(TEMPO_ESPERA);
            }

            // --- FIM DO JOGO ---
            System.out.println("Todas as perguntas respondidas. A enviar classificações...");
            
            // Construir o Placar Final em HTML para aparecer bonito na GUI
            StringBuilder sb = new StringBuilder("<html><div style='text-align: center;'><h1>FIM DO JOGO!</h1>");
            List<Integer> scores = gameState.getTeamScores();
            
            sb.append("<table border='1' style='margin: auto;'><tr><th>Equipa</th><th>Pontos</th></tr>");
            for (int t = 0; t < scores.size(); t++) {
                sb.append("<tr><td>Equipa ").append(t).append("</td>");
                sb.append("<td>").append(scores.get(t)).append("</td></tr>");
            }
            sb.append("</table></div></html>");

            // Envia mensagem final
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

    public boolean isTeamRound() {
        return isTeamRound;
    }

    public TeamBarrier getCurrentBarrier() {
        return currentBarrier;
    }


    public static void main(String[] args) {
        // Certifica-te que o caminho do JSON está correto
        new Server("data/questions.json"); 
    }
}