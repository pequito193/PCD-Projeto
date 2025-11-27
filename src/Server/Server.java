package Server;

import model.GameState;
import model.Quiz;
import utils.JsonLoader;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final int PORT = 12345;
    private GameState gameState;

    public Server(String jsonPath) {
        try {
            // 1. Carregar perguntas (código que estava no ClientGUI)
            JsonLoader loader = new JsonLoader(jsonPath);
            Quiz quiz = loader.getQuizzes().get(0);
            
            // 2. Inicializar o estado do jogo (Exemplo: 2 equipas)
            // Nota: No futuro isto será dinâmico via TUI
            this.gameState = new GameState(quiz, 2);
            System.out.println("Servidor iniciado. Jogo carregado: " + quiz.getName());
            
            startServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("À espera de jogadores na porta " + PORT + "...");
            
            while (true) {
                // Bloqueia à espera de uma conexão
                Socket socket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + socket.getInetAddress());
                
                // Cria um thread para lidar com o cliente
                DealWithClient worker = new DealWithClient(socket, this);
                worker.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String path = "data/questions.json"; // Caminho por defeito
        if (args.length > 0) path = args[0];
        new Server(path);
    }
}