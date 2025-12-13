package Server;

import common.Msg;
import model.Question;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class DealWithClient extends Thread {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Server server;
    private String username;
    private String gameId; // NOVO
    private boolean lastAnswerCorrect = false;

    public DealWithClient(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    // Métodos de acesso
    public String getUsername() {
        return username;
    }

    public String getGameId() {
        return gameId;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            Object received = in.readObject();
            if (received instanceof Msg) {
                Msg message = (Msg) received;

                if (message.type != Msg.Type.LOGIN) {
                    send(new Msg(Msg.Type.LOGIN_ERROR, "Protocolo inválido: Primeira mensagem não é LOGIN."));
                    closeConnection();
                    return;
                }

                if (!handleLogin(message)) {
                    return;
                }
            }

            // Loop principal após login bem-sucedido
            while (true) {
                received = in.readObject();
                if (received instanceof Msg) {
                    Msg message = (Msg) received;
                    handleMessage(message);
                }
            }
        } catch (Exception e) {
            if (!socket.isClosed()) {
                System.out.println("Cliente desconectado: " + (username != null ? username : "N/A"));
            }
            // Remove da lista de clientes do jogo correspondente
            server.removeClient(this);
            closeConnection();
        }
    }

    // Lógica de tratamento de LOGIN: Espera "GameID|TeamID|Username" no content
    private boolean handleLogin(Msg msg) throws Exception {
        String content = (String) msg.content;
        // Usa \\| para escapar o pipe, pois é um caractere especial em regex
        String[] parts = content.split("\\|");

        if (parts.length < 3) {
            send(new Msg(Msg.Type.LOGIN_ERROR, "Formato de login inválido. Uso: <Jogo>|<Equipa>|<Username>"));
            closeConnection();
            return false;
        }

        String attemptedGameId = parts[0];
        String teamId = parts[1];
        String attemptedUsername = parts[2];

        // 1. Verificar se o username já está em uso (em qualquer jogo)
        if (server.isUsernameTaken(attemptedUsername)) {
            System.out.println("Login Rejeitado: Username '" + attemptedUsername + "' já em uso.");
            send(new Msg(Msg.Type.LOGIN_ERROR, "Username já em uso."));
            closeConnection();
            return false;
        }

        // 2. Armazena o Game ID e Username
        this.username = attemptedUsername;
        this.gameId = attemptedGameId;

        // 3. Avisa o servidor. O servidor verifica se o jogo existe e está cheio, e envia LOGIN_OK ou LOGIN_ERROR/fecha a conexão.
        server.onClientLoggedIn(this, attemptedGameId);

        // Se onClientLoggedIn falhar (jogo cheio/não existe), já fechou a conexão,
        // mas aqui precisamos de garantir que não continua no run() loop.
        if (socket.isClosed()) return false;

        return true;
    }

    public void send(Msg msg) {
        try {
            if (socket.isClosed()) return;
            out.writeObject(msg);
            out.reset();
        } catch (Exception e) {
            // Ignorar erro de envio se cliente já caiu
        }
    }

    public void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception e) {
            // Ignorar
        }
    }

    private void handleMessage(Msg msg) {
        try {
            // Garante que o Game ID está definido e o jogo não terminou
            if (gameId == null || server.getGameState(gameId) == null) return;

            switch (msg.type) {
                case SEND_ANSWER:
                    // Acesso ao GameState via Game ID
                    Question currentQ = server.getGameState(gameId).getCurrentQuestion();
                    if (currentQ == null) return;

                    int answerIndex = (int) msg.content;
                    this.lastAnswerCorrect = (answerIndex == currentQ.getCorrect());

                    if (server.isTeamRound(gameId)) {
                        // --- MODO EQUIPA ---
                        TeamBarrier barrier = server.getCurrentBarrier(gameId);
                        if (barrier != null) {
                            barrier.playerFinished();

                            String status = this.lastAnswerCorrect ? "CERTO (aguarda equipa)" : "ERRADO (aguarda equipa)";
                            System.out.println("Jogo " + gameId + " | Equipa: Jogador " + username + " respondeu: " + status);
                        }
                    } else {
                        // --- MODO INDIVIDUAL ---
                        ModifiedCountDownLatch latch = server.getCurrentLatch(gameId);
                        if (latch != null) {
                            int bonus = latch.countDown();

                            if (this.lastAnswerCorrect) {
                                int points = currentQ.getPoints() * bonus;

                                // Acesso ao GameState e cálculo da equipa com Game ID
                                int myTeamId = server.getTeamIdForPlayer(this, gameId);

                                synchronized (server.getGameState(gameId)) {
                                    server.getGameState(gameId).addPointsToTeam(myTeamId, points);
                                }

                                System.out.println("Jogo " + gameId + " | Individual: " + username + " ganhou " + points + " pontos (Bónus: " + bonus + ")");
                            } else {
                                System.out.println("Jogo " + gameId + " | Individual: " + username + " errou.");
                            }
                        }
                    }
                    break;

                default:
                    System.out.println("Mensagem desconhecida: " + msg.type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isLastAnswerCorrect() {
        return lastAnswerCorrect;
    }
}