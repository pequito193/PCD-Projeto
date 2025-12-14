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
                trip.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void await() throws InterruptedException {
        lock.lock();
        try {
            if (count > 0 && !timeExpired) {
                boolean timeLeft = trip.await(TEMPO_RESPOSTA, java.util.concurrent.TimeUnit.MILLISECONDS);
                
                if (!timeLeft) {
                    timeExpired = true;
                }
            }
        } finally {
            lock.unlock();
        }
    }
    
    public boolean allResponded() {
        lock.lock();
        try {
            return count == 0;
        } finally {
            lock.unlock();
        }
    }
}