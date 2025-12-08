package common;

import java.io.Serializable;

public class Msg implements Serializable {
    public enum Type {
        LOGIN,          // Cliente quer entrar (User + Jogo + Equipa)
        LOGIN_OK,       // Servidor aceitou
        LOGIN_ERROR,    // Servidor recusou
        NEW_QUESTION,   // Servidor envia pergunta
        SEND_ANSWER,    // Cliente envia resposta
        UPDATE_SCORE,   // Servidor envia placar atualizado
        GAME_OVER       // Fim de jogo
    }

    public Type type;
    public Object content; // Conteúdo da mensagem
    public int senderId;

    public Msg(Type type, Object content) {
        this.type = type;
        this.content = content;
    }
    
    @Override
    public String toString() {
        return "Msg{" + "type=" + type + ", content=" + content + '}';
    }
}