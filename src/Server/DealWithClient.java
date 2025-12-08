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

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Object received = in.readObject();
                if (received instanceof Msg) {
                    Msg message = (Msg) received;
                    handleMessage(message);
                }
            }
        } catch (Exception e) {
            System.out.println("Cliente desconectado: " + username);
            server.removeClient(this);
        }
    }

    public void send(Msg msg) {
        try {
            out.writeObject(msg);
            out.reset();
        } catch (Exception e) {
            // Ignorar erro de envio se cliente já caiu
        }
    }

    private void handleMessage(Msg msg) {
        try {
            switch (msg.type) {
                case LOGIN:
                    this.username = (String) msg.content;
                    System.out.println("Login recebido: " + username);
                    // Responde OK
                    out.writeObject(new Msg(Msg.Type.LOGIN_OK, "Bem-vindo " + username));
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
                        // A pontuação será calculada pelo Servidor quando a barreira desbloquear. [cite: 75]
        
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
                            // O countdown devolve o multiplicador (2x se for rápido, 1x normal) [cite: 78]
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