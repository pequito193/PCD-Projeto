package Server;

public class ModifiedCountDownLatch {
    private int count;          // Quantos jogadores faltam responder
    private int bonusCount;     // Quantos bónus ainda há disponíveis
    private final int totalPlayers;

    public ModifiedCountDownLatch(int totalPlayers) {
        this.totalPlayers = totalPlayers;
        this.count = totalPlayers;
        this.bonusCount = 1; // Os 2 primeiros a responder ganham bónus
    }

    // Chamado pelos clientes quando respondem
    public synchronized int countDown() {
        if (count <= 0) return 1; // Já acabou

        count--;
        
        int bonusMultiplier = 1;
        if (bonusCount > 0) {
            bonusMultiplier = 2; // Dobro dos pontos
            bonusCount--;
        }

        // Se todos responderam, acorda o servidor
        if (count == 0) {
            notifyAll();
        }

        return bonusMultiplier;
    }

    // Chamado pelo Servidor para esperar pelas respostas
    public synchronized void await(long timeoutMillis) throws InterruptedException {
        if (count > 0) {
            wait(timeoutMillis);
        }
        // Se acordar aqui, ou o tempo acabou ou count chegou a 0
    }
}