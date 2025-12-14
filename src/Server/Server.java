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
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

public class Server {
    private static final int PORT = 12345;
    private static final int TEMPO_ESPERA = 3000;     // 3s
    private final Map<String, GameInfo> activeGames = new HashMap<>();
    private Quiz defaultQuiz;

    private class GameInfo {
        final String gameId;
        final int numTeamsExpected;
        final int playersPerTeamExpected;
        final GameState gameState;
        final List<DealWithClient> clients = new ArrayList<>();
        ModifiedCountDownLatch currentLatch;
        TeamBarrier currentBarrier;
        boolean isTeamRound = false;

        GameInfo(String gameId, int numTeams, int playersPerTeam, Quiz quiz) {
            this.gameId = gameId;
            this.numTeamsExpected = numTeams;
            this.playersPerTeamExpected = playersPerTeam;
            this.gameState = new GameState(quiz, numTeams);
        }

        int getTotalPlayersNeeded() {
            return numTeamsExpected * playersPerTeamExpected;
        }
    }


    public Server(String jsonPath) {
        try {
            JsonLoader loader = new JsonLoader(jsonPath);
            // Carregar o quiz uma vez
            this.defaultQuiz = loader.getQuizzes().get(0);
            System.out.println("Quiz carregado: " + defaultQuiz.getName());

            startConnectionLoop();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Gerador de códigos (4 letras maiúsculas)
    private String generateUniqueGameCode() {
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        // A geração do código é feita até ser único na lista de jogos ativos
        synchronized (activeGames) {
            do {
                sb.setLength(0);
                for (int i = 0; i < 4; i++) {
                    sb.append((char) ('A' + rnd.nextInt(26)));
                }
            } while (activeGames.containsKey(sb.toString()));
        }
        return sb.toString();
    }

    public void runTUI() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        System.out.println("Servidor pronto. Comandos disponíveis:");
        System.out.println(" > new <nEquipas> <nJogadoresPorEquipa>");
        System.out.println(" > list (vê jogos/jogadores ligados)");

        while (true) {
            String line = scanner.nextLine();
            String[] parts = line.split(" ");

            if (parts[0].equalsIgnoreCase("new") && parts.length == 3) {
                try {
                    int numTeams = Integer.parseInt(parts[1]);
                    int playersPerTeam = Integer.parseInt(parts[2]);

                    if (numTeams <= 0 || playersPerTeam <= 0) {
                        System.out.println("Erro: Os números de equipas/jogadores têm de ser positivos.");
                        continue;
                    }

                    String gameCode = generateUniqueGameCode();

                    synchronized (activeGames) {
                        GameInfo newGame = new GameInfo(gameCode, numTeams, playersPerTeam, defaultQuiz);
                        activeGames.put(gameCode, newGame);
                    }

                    System.out.println("Novo jogo configurado!");
                    System.out.println("Código do jogo: " + gameCode);
                    System.out.println("À espera de " + (numTeams * playersPerTeam) + " jogadores.");

                } catch (NumberFormatException e) {
                    System.out.println("Erro: Os argumentos têm de ser números inteiros.");
                }
            }
            else if (parts[0].equalsIgnoreCase("list")) {
                synchronized (activeGames) {
                    if (activeGames.isEmpty()) {
                        System.out.println("Nenhum jogo ativo.");
                        continue;
                    }
                    System.out.println("--- JOGOS ATIVOS ---");
                    for (GameInfo game : activeGames.values()) {
                        System.out.printf("JOGO %s: %d/%d jogadores ligados. (Status: %s)\n",
                                game.gameId,
                                game.clients.size(),
                                game.getTotalPlayersNeeded(),
                                game.clients.size() < game.getTotalPlayersNeeded() ? "À espera" : "A decorrer"
                        );
                    }
                }
            }
            else {
                System.out.println("Comando inválido.");
            }
        }
    }

    private void startConnectionLoop() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                while (true) {
                    Socket socket = serverSocket.accept();

                    DealWithClient client = new DealWithClient(socket, this);
                    client.start();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // Chamado por DealWithClient APÓS um login bem-sucedido.
    public void onClientLoggedIn(DealWithClient client, String gameId) {
        GameInfo game;

        synchronized (activeGames) {
            game = activeGames.get(gameId);
        }

        if (game == null) {
            client.send(new Msg(Msg.Type.LOGIN_ERROR, "Jogo " + gameId + " não encontrado."));
            client.closeConnection();
            return;
        }

        synchronized (game.clients) {
            if (game.clients.size() >= game.getTotalPlayersNeeded()) {
                client.send(new Msg(Msg.Type.LOGIN_ERROR, "Jogo " + gameId + " está cheio ou a decorrer."));
                client.closeConnection();
                return;
            }

            game.clients.add(client);
            client.send(new Msg(Msg.Type.LOGIN_OK, "Bem-vindo " + client.getUsername()));

            System.out.println("JOGO " + gameId + " | Jogadores: " + game.clients.size() + "/" + game.getTotalPlayersNeeded());

            if (game.clients.size() == game.getTotalPlayersNeeded()) {
                System.out.println("JOGO " + gameId + " COMPLETO. A INICIAR...");
                new Thread(() -> startGame(game)).start();
            }
        }
    }

    private void startGame(GameInfo game) {
        try {
            System.out.println("O jogo " + game.gameId + " vai começar em 3 segundos...");
            Thread.sleep(TEMPO_ESPERA);

            List<Question> questions = game.gameState.getQuiz().getQuestions();

            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);

                game.isTeamRound = (i % 2 != 0);

                System.out.println("\n--- PERGUNTA " + (i + 1) + " (" + (game.isTeamRound ? "EQUIPA" : "INDIVIDUAL") + ") para o JOGO " + game.gameId + " ---");

                synchronized(game.clients) {
                    System.out.println("Jogadores ativos: " + game.clients.size() + " jogadores.");

                    if (game.isTeamRound) {
                        game.currentLatch = null;
                        game.currentBarrier = new TeamBarrier(game.clients.size());
                    } else {
                        game.currentBarrier = null;
                        game.currentLatch = new ModifiedCountDownLatch(2, 1, 10000, game.clients.size());
                    }
                }

                // Enviar a pergunta a todos
                if (game.isTeamRound) {
                    Question qTeam = new Question(
                            "[EQUIPA] " + q.getQuestion(),
                            q.getPoints(),
                            q.getCorrect(),
                            q.getOptions()
                    );
                    broadcast(game, new Msg(Msg.Type.NEW_QUESTION, qTeam));
                } else {
                    broadcast(game, new Msg(Msg.Type.NEW_QUESTION, q));
                }

                // Esperar pelas respostas
                if (game.isTeamRound) {
                    System.out.println("Servidor à espera na Barreira (Modo Equipa) para " + game.gameId + "...");
                    game.currentBarrier.await();

                    // Calcular resultados por equipa
                    int numTeams = game.gameState.getTeamScores().size();
                    boolean[] teamAllCorrect = new boolean[numTeams];
                    boolean[] teamAtLeastOneCorrect = new boolean[numTeams];

                    for(int t=0; t<numTeams; t++) {
                        teamAllCorrect[t] = true;
                        teamAtLeastOneCorrect[t] = false;
                    }

                    synchronized(game.clients) {
                        for (DealWithClient client : game.clients) {
                            int teamId = getTeamIdForPlayer(client, game.gameId);
                            boolean correct = client.isLastAnswerCorrect();

                            if (correct) {
                                teamAtLeastOneCorrect[teamId] = true;
                            } else {
                                teamAllCorrect[teamId] = false;
                            }
                        }
                    }

                    int basePoints = q.getPoints();

                    for (int t = 0; t < numTeams; t++) {
                        int pointsToAdd = 0;
                        if (teamAllCorrect[t]) {
                            pointsToAdd = basePoints * 2;
                            System.out.println("Equipa " + (t+1) + " do jogo " + game.gameId + ": TODOS acertaram! (Pontos x2: " + pointsToAdd + ")");
                        } else if (teamAtLeastOneCorrect[t]) {
                            pointsToAdd = basePoints;
                            System.out.println("Equipa " + (t+1) + " do jogo " + game.gameId + ": Acertaram parcialmente. (Pontos normais: " + pointsToAdd + ")");
                        } else {
                            System.out.println("Equipa " + (t+1) + " do jogo " + game.gameId + ": Ninguém acertou.");
                        }

                        if (pointsToAdd > 0) {
                            synchronized (game.gameState) {
                                game.gameState.addPointsToTeam(t, pointsToAdd);
                            }
                        }
                    }
                    System.out.println("Barreira libertada (ou tempo esgotou) para " + game.gameId + ".");
                } else {
                    System.out.println("Servidor à espera no Latch (Modo Individual) para " + game.gameId + "...");
                    game.currentLatch.await();
                    System.out.println("Latch libertado (ou tempo esgotou) para " + game.gameId + ".");
                }

                game.gameState.nextQuestion();

                // Enviar Placar Intermédio
                if (i < questions.size() - 1) {
                    System.out.println("A enviar placar intermédio para " + game.gameId + "...");
                    broadcast(game, new Msg(Msg.Type.UPDATE_SCORE, getScoreSummary(game)));

                    Thread.sleep(TEMPO_ESPERA);
                } else {
                    Thread.sleep(TEMPO_ESPERA);
                }
            }

            // Fim do Jogo
            System.out.println("JOGO " + game.gameId + " TERMINADO.");

            // Placar Final
            StringBuilder sb = new StringBuilder("<html><div style='text-align: center;'><h1>FIM DO JOGO! (ID: " + game.gameId + ")</h1>");
            List<Integer> scores = game.gameState.getTeamScores();

            sb.append("<table border='1' style='margin: auto;'><tr><th>Equipa</th><th>Pontos</th></tr>");
            for (int t = 0; t < scores.size(); t++) {
                sb.append("<tr><td>Equipa ").append(t + 1).append("</td>");
                sb.append("<td>").append(scores.get(t)).append("</td></tr>");
            }
            sb.append("</table></div></html>");

            // Envia mensagem final
            broadcast(game, new Msg(Msg.Type.GAME_OVER, sb.toString()));

            // Fechar conexões e remover o jogo
            closeAllClientConnections(game.gameId);

            synchronized (activeGames) {
                activeGames.remove(game.gameId);
                System.out.println("Jogo " + game.gameId + " removido da lista de ativos.");
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Gera a string HTML com o placar atual para UPDATE_SCORE
    public String getScoreSummary(GameInfo game) {
        StringBuilder sb = new StringBuilder("<html><div style='text-align: center;'><h3>Placar Atual (Pergunta " + (game.gameState.getCurrentIndex() + 1) + "/" + game.gameState.getTotalQuestions() + ")</h3>");
        List<Integer> scores = game.gameState.getTeamScores();

        sb.append("<table border='1' style='margin: auto;'><tr><th>Equipa</th><th>Pontos</th></tr>");
        for (int t = 0; t < scores.size(); t++) {
            sb.append("<tr><td>Equipa ").append(t + 1).append("</td>");
            sb.append("<td>").append(scores.get(t)).append("</td></tr>");
        }
        sb.append("</table></div></html>");

        return sb.toString();
    }

    // Procura o username em todos os jogos ativos
    public boolean isUsernameTaken(String username) {
        synchronized (activeGames) {
            for (GameInfo game : activeGames.values()) {
                synchronized (game.clients) {
                    for (DealWithClient client : game.clients) {
                        if (client.getUsername() != null && client.getUsername().equalsIgnoreCase(username)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    // Broadcast por Jogo
    public void broadcast(GameInfo game, Msg msg) {
        synchronized (game.clients) {
            List<DealWithClient> activeClients = new ArrayList<>(game.clients);
            for (DealWithClient client : activeClients) {
                client.send(msg);
            }
        }
    }

    // Fecha todas as conexões para um Jogo
    public void closeAllClientConnections(String gameId) {
        GameInfo game;
        synchronized (activeGames) {
            game = activeGames.get(gameId);
        }
        if (game == null) return;

        synchronized (game.clients) {
            List<DealWithClient> clientsToClose = new ArrayList<>(game.clients);
            for (DealWithClient client : clientsToClose) {
                try {
                    client.closeConnection();
                } catch (Exception e) {}
            }
            game.clients.clear();
        }
    }

    // Remove cliente do jogo
    public void removeClient(DealWithClient client) {
        String gameId = client.getGameId();
        if (gameId == null) return;

        synchronized (activeGames) {
            GameInfo game = activeGames.get(gameId);
            if (game != null) {
                synchronized (game.clients) {
                    game.clients.remove(client);
                    System.out.println("Cliente " + client.getUsername() + " removido do JOGO " + gameId);
                }
            }
        }
    }


    public GameState getGameState(String gameId) {
        synchronized (activeGames) {
            GameInfo game = activeGames.get(gameId);
            return game != null ? game.gameState : null;
        }
    }

    public ModifiedCountDownLatch getCurrentLatch(String gameId) {
        synchronized (activeGames) {
            GameInfo game = activeGames.get(gameId);
            return game != null ? game.currentLatch : null;
        }
    }

    public boolean isTeamRound(String gameId) {
        synchronized (activeGames) {
            GameInfo game = activeGames.get(gameId);
            return game != null && game.isTeamRound;
        }
    }

    public TeamBarrier getCurrentBarrier(String gameId) {
        synchronized (activeGames) {
            GameInfo game = activeGames.get(gameId);
            return game != null ? game.currentBarrier : null;
        }
    }

    public int getTeamIdForPlayer(DealWithClient client, String gameId) {
        GameInfo game;
        synchronized(activeGames) {
            game = activeGames.get(gameId);
        }

        if (game == null) return -1;

        synchronized(game.clients) {
            int playerIndex = game.clients.indexOf(client);

            int divisor = (game.numTeamsExpected > 1) ? game.numTeamsExpected : 1;

            return playerIndex % divisor;
        }
    }

    public static void main(String[] args) {
        Server s = new Server("data/questions.json");
        s.runTUI();
    }
}