package Server;

public class ModifiedCountDownLatch {
    private int waitPeriod;  // 10s
    private int count;
    private int bonusCount;
    private int bonusFactor;

    public ModifiedCountDownLatch(int bonusFactor, int bonusCount, int waitPeriod, int count) {
        this.bonusFactor = bonusFactor;
        this.waitPeriod = waitPeriod;
        this.bonusCount = bonusCount;
        this.count = count;
        }

    // Chamado pelos clientes quando respondem
    public synchronized int countDown() {
        if (count <= 0) return 1; 

        count--;
        
        if (bonusCount > 0) {
            bonusCount--;
        }

        if (count == 0) {
            notifyAll();
        }

        return bonusFactor;
    }

    // Chamado pelo Servidor para esperar pelas respostas
    public synchronized void await() throws InterruptedException {
        if (count > 0) {
            wait(waitPeriod);

            notifyAll();
        }

        // Se acordar aqui, ou o tempo acabou ou count chegou a 0
    }
}