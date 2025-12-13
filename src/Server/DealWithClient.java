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
    private String username; // Nome do jogador
    private boolean lastAnswerCorrect = false;

    public DealWithClient(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    // Método de acesso para o Server verificar se o nome está ocupado
    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // O cliente envia o LOGIN assim que se conecta.
            // A primeira mensagem é tratada aqui fora do loop para validar o login antes de começar a loop
            Object received = in.readObject();
            if (received instanceof Msg) {
                Msg message = (Msg) received;
                // Se a primeira mensagem não for LOGIN, rejeita.
                if (message.type != Msg.Type.LOGIN) {
                    send(new Msg(Msg.Type.LOGIN_ERROR, "Protocolo inválido: Primeira mensagem não é LOGIN."));
                    closeConnection(); // Rejeita e fecha
                    return; // Login falhou, thread termina
                }

                // Validação de Login antes de entrar no loop principal
                if (!handleLogin(message)) {
                    return; // Login falhou, thread termina
                }
            }

            // Loop principal após login bem-sucedido
            while (true) {
                received = in.readObject();
                if (received instanceof Msg) {
                    Msg message = (Msg) received;
                    handleMessage(message); // Trata todas as mensagens exceto LOGIN
                }
            }
        } catch (Exception e) {
            // Se a exceção for devido a fecho de socket após login falhado, a thread termina aqui.
            // Se for após login bem-sucedido ou a meio do jogo
            if (!socket.isClosed()) {
                System.out.println("Cliente desconectado: " + (username != null ? username : "N/A"));
            }
            server.removeClient(this); // Remove da lista de clientes ativos
            closeConnection();
        }
    }

    // Lógica de tratamento de LOGIN separada para validar e rejeitar se necessário
    private boolean handleLogin(Msg msg) throws Exception {
        String attemptedUsername = (String) msg.content;

        // 1. Verificar se o username já está em uso
        if (server.isUsernameTaken(attemptedUsername)) {
            System.out.println("Login Rejeitado: Username '" + attemptedUsername + "' já em uso.");
            send(new Msg(Msg.Type.LOGIN_ERROR, "Username já em uso."));
            closeConnection(); // Fecha a socket
            return false;
        }

        // 2. Se OK, regista o username
        this.username = attemptedUsername;
        System.out.println("Login OK: " + username);

        // 3. NOVO: Avisa o servidor que o login foi bem-sucedido. Isto adiciona o cliente à lista principal
        // e verifica se o jogo deve começar.
        server.onClientLoggedIn(this);

        // 4. Responde OK ao cliente
        send(new Msg(Msg.Type.LOGIN_OK, "Bem-vindo " + username));

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

    // Fecha a conexão do cliente
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
            switch (msg.type) {
                case LOGIN:
                    // Já tratado no handleLogin() antes do loop principal
                    break;

                case SEND_ANSWER:
                    // 1. Verificar se o jogo está ativo e a pergunta existe
                    Question currentQ = server.getGameState().getCurrentQuestion();
                    if (currentQ == null) return;

                    // 2. Ler a resposta do cliente (índice da opção)
                    int answerIndex = (int) msg.content;

                    // 3. Verificar se acertou (comparar com o índice correto do JSON)
                    // Guardamos na variável global para o Servidor consultar nas rondas de equipa
                    this.lastAnswerCorrect = (answerIndex == currentQ.getCorrect());

                    if (server.isTeamRound()) {
                        // --- MODO EQUIPA ---
                        // Nas rondas de equipa, NÃO calculamos pontos aqui.
                        // Apenas registamos que acabámos e esperamos na barreira.
                        // A pontuação será calculada pelo Servidor quando a barreira desbloquear.

                        TeamBarrier barrier = server.getCurrentBarrier();
                        if (barrier != null) {
                            barrier.playerFinished(); // Avisa a barreira que este jogador já respondeu

                            String status = this.lastAnswerCorrect ? "CERTO (aguarda equipa)" : "ERRADO (aguarda equipa)";
                            System.out.println("Equipa: Jogador " + username + " respondeu: " + status);
                        }
                    } else {
                        // --- MODO INDIVIDUAL ---
                        // Nas rondas individuais, calculamos logo os pontos com bónus de rapidez.

                        ModifiedCountDownLatch latch = server.getCurrentLatch();
                        if (latch != null) {
                            // O countdown devolve o multiplicador (2x se for rápido, 1x normal)
                            int bonus = latch.countDown();

                            if (this.lastAnswerCorrect) {
                                // Calcula pontos: Base * Bónus
                                int points = currentQ.getPoints() * bonus;

                                // Adiciona imediatamente aos pontos da equipa
                                int myTeamId = server.getTeamIdForPlayer(this);

                                // Sincronizar porque várias threads podem escrever no GameState ao mesmo tempo
                                synchronized (server.getGameState()) {
                                    server.getGameState().addPointsToTeam(myTeamId, points);
                                }

                                System.out.println("Individual: " + username + " ganhou " + points + " pontos (Bónus: " + bonus + ")");
                            } else {
                                System.out.println("Individual: " + username + " errou.");
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