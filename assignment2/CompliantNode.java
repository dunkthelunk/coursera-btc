import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
  private double p_graph;
  private double p_malicious;
  private double p_txDistribution;
  private int numRoundsLeft;

  private Set<Transaction> pendingTransactions;

  public CompliantNode(
      double p_graph, double p_malicious, double p_txDistribution, int numRoundsLeft) {
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
  }

  @Override
  public Set<Transaction> sendToFollowers() {
    return pendingTransactions;
  }

  @Override
  public void receiveFromFollowees(Set<Candidate> candidates) {
    candidates.stream().map(candidate -> candidate.tx).forEach(pendingTransactions::add);
  }
}
