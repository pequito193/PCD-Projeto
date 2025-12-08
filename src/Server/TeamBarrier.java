package Server;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TeamBarrier {
    private static final int TEMPO_RESPOSTA = 10000;  // 10s

    private final int totalMembers;
    private int count;
    private final Lock lock = new ReentrantLock();
    private final Condition trip = lock.newCondition();
    
    // Variável para saber se o tempo acabou (o teu colega vai precisar disto)
    private boolean timeExpired = false;

    public TeamBarrier(int PlayerSize) {
        this.totalMembers = PlayerSize;
        this.count = PlayerSize;
    }

    // Chamado pelo DealWithClient quando recebe uma resposta
    public void playerFinished() {
        lock.lock();
        try {
            count--;
            if (count == 0) {
                trip.signalAll(); // Acorda a thread do Servidor que está à espera
            }
        } finally {
            lock.unlock();
        }
    }

    // Chamado pelo Server para esperar que a equipa responda
    public void await() throws InterruptedException {
        lock.lock();
        try {
            // Espera enquanto ainda faltam respostas E o tempo não acabou
            if (count > 0 && !timeExpired) {
                // O método await da Condition retorna false se o tempo esgotar
                boolean timeLeft = trip.await(TEMPO_RESPOSTA, java.util.concurrent.TimeUnit.MILLISECONDS);
                
                if (!timeLeft) {
                    timeExpired = true;
                    // Se o tempo acabou, libertamos tudo o que for necessário
                    // (Lógica adicional de "partir a barreira" entra aqui)
                }
            }
        } finally {
            lock.unlock();
        }
    }
    
    // Método para verificar se todos responderam (útil para o cálculo de pontos)
    public boolean allResponded() {
        lock.lock();
        try {
            return count == 0;
        } finally {
            lock.unlock();
        }
    }
}