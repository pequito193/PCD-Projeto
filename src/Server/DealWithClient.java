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
                    // 1. Verificar se estamos numa ronda válida
                    ModifiedCountDownLatch latch = server.getCurrentLatch();
                    if (latch == null) return; // Jogo não está à espera de resposta

                    // 2. Obter a resposta do cliente (int index da opção)
                    int answerIndex = (int) msg.content;
                    
                    // 3. Obter pergunta atual para validar
                    Question currentQ = server.getGameState().getCurrentQuestion();
                    if (currentQ == null) return;

                    // 4. Verificar se acertou
                    // Nota: Assume-se que correct no JSON é 1-based, e a GUI manda 1-based
                    boolean correct = (answerIndex == currentQ.getCorrect());

                    // 5. Chamar o Latch para obter o bónus (e avisar que respondemos)
                    // O Latch decrementa sempre, independentemente de acertar ou não
                    int bonus = latch.countDown();

                    if (correct) {
                        int points = currentQ.getPoints() * bonus;
                        
                        // 6. Adicionar pontos à equipa
                        int myTeamId = server.getTeamIdForPlayer(this);
                        
                        // Sincronizar acesso ao GameState se necessário (ou o método lá já ser sync)
                        // Como GameState não é sync, fazemos aqui ou no Server
                        synchronized (server.getGameState()) {
                            server.getGameState().addPointsToTeam(myTeamId, points);
                        }
                        
                        System.out.println("Jogador " + username + " acertou! Pontos: " + points + " (Bónus: " + bonus + ")");
                    } else {
                        System.out.println("Jogador " + username + " errou.");
                    }
                    break;

                default:
                    System.out.println("Mensagem desconhecida: " + msg.type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}