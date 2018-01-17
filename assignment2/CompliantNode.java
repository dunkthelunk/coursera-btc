import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
    private double p_graph;
    private double p_malicious;
    private double p_txDistribution;
    private int numRoundsLeft;

    private Set<Transaction> pendingTransactions;
    private Set<Transaction> newTransactionsReceivedInThisRound;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRoundsLeft) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRoundsLeft = numRoundsLeft;
    }

    @Override
    public void setFollowees(boolean[] followees) {
        // IMPLEMENT THIS
    }

    @Override
    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
        this.newTransactionsReceivedInThisRound = new HashSet<>(pendingTransactions);
    }

    @Override
    public Set<Transaction> sendToFollowers() {
        if(numRoundsLeft == 0) {
            return pendingTransactions;
        }
        numRoundsLeft--;
        pendingTransactions.addAll(newTransactionsReceivedInThisRound);
        return newTransactionsReceivedInThisRound;
    }

    @Override
    public void receiveFromFollowees(Set<Candidate> candidates) {
       newTransactionsReceivedInThisRound = candidates.stream().map(candidate -> candidate.tx).filter(tx -> !pendingTransactions.contains(tx)).collect(Collectors.toSet());
    }
}
