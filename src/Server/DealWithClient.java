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
            server.removeClient(this);
            closeConnection();
        }
    }

    private boolean handleLogin(Msg msg) throws Exception {
        String content = (String) msg.content;
        String[] parts = content.split("\\|");

        if (parts.length < 3) {
            send(new Msg(Msg.Type.LOGIN_ERROR, "Formato de login inválido. Uso: <Jogo>|<Equipa>|<Username>"));
            closeConnection();
            return false;
        }

        String attemptedGameId = parts[0];
        String teamId = parts[1];
        String attemptedUsername = parts[2];

        // Verificar se o username já está em uso
        if (server.isUsernameTaken(attemptedUsername)) {
            System.out.println("Login Rejeitado: Username '" + attemptedUsername + "' já em uso.");
            send(new Msg(Msg.Type.LOGIN_ERROR, "Username já em uso."));
            closeConnection();
            return false;
        }

        this.username = attemptedUsername;
        this.gameId = attemptedGameId;

        // Avisa o servidor. O servidor verifica se o jogo existe e está cheio, e envia LOGIN_OK ou LOGIN_ERROR e fecha a conexão.
        server.onClientLoggedIn(this, attemptedGameId);

        // garantir que não continua no run() loop.
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
        } catch (Exception e) {}
    }

    private void handleMessage(Msg msg) {
        try {
            if (gameId == null || server.getGameState(gameId) == null) return;

            switch (msg.type) {
                case SEND_ANSWER:
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