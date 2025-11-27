package Server;

import common.Msg;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class DealWithClient extends Thread {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Server server; // Referência para o servidor principal (onde está o GameState)

    public DealWithClient(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Criar streams (A ordem importa: criar Output antes do Input para evitar bloqueio)
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                // Fica bloqueado aqui à espera que o cliente fale
                Object received = in.readObject();
                
                if (received instanceof Msg) {
                    Msg message = (Msg) received;
                    System.out.println("Recebi do cliente: " + message);
                    
                    // AQUI vamos decidir o que fazer com a mensagem
                    handleMessage(message);
                }
            }
        } catch (Exception e) {
            System.out.println("Cliente desconectado.");
        }
    }

    private void handleMessage(Msg msg) {
        // Exemplo simples de resposta
        try {
            if (msg.type == Msg.Type.LOGIN) {
                // Simular um LOGIN OK
                out.writeObject(new Msg(Msg.Type.LOGIN_OK, "Bem-vindo!"));
            }
            // Aqui vamos adicionar a lógica de receber respostas do Quiz depois
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}