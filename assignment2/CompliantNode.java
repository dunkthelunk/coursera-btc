import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
  private double p_graph;
  private double p_malicious;
  private double p_txDistribution;
  private int numRoundsLeft;
  private boolean spamIsOK;

  private Set<Transaction> pendingTransactions;
  private Set<Transaction> txsToTransmit;
  private Map<Integer, Set<Transaction>> mapOfFolloweesAndTheirMessages;
  private Map<Integer, Integer> numOfRoundsFolloweeDidntRespond;
  private boolean[] followees;
  private BiFunction<Double, Double, Boolean> spamPredicate =
      (pgraph, ptxDist) -> pgraph * ptxDist <= 0.001;

  public CompliantNode(
      double p_graph, double p_malicious, double p_txDistribution, int numRoundsLeft) {
    this.p_graph = p_graph;
    this.p_malicious = p_malicious;
    this.p_txDistribution = p_txDistribution;
    this.numRoundsLeft = numRoundsLeft;
    mapOfFolloweesAndTheirMessages = new HashMap<>();
    numOfRoundsFolloweeDidntRespond = new HashMap<>();
    spamIsOK = spamPredicate.apply(p_graph, p_txDistribution);
  }

  @Override
  public void setFollowees(boolean[] followees) {
    this.followees = followees;
    //    IntStream.range(0, followees.length).forEach( i ->
    // numOfRoundsFolloweeDidntRespond.put(i,0));
  }

  @Override
  public void setPendingTransaction(Set<Transaction> pendingTransactions) {
    this.pendingTransactions = pendingTransactions;
    txsToTransmit = new HashSet<>(pendingTransactions);
  }

  @Override
  public Set<Transaction> sendToFollowers() {
    if (numRoundsLeft == 0) {
      return pendingTransactions;
    }
    numRoundsLeft--;
    return txsToTransmit;
  }

  @Override
  public void receiveFromFollowees(Set<Candidate> candidates) {
    txsToTransmit = new HashSet<>();
    Set<Integer> senders = candidates.stream().map(c -> c.sender).collect(Collectors.toSet());
    IntStream.range(0, followees.length)
        .forEach(
            i -> {
              if (followees[i] && !senders.contains(i)) {
                numOfRoundsFolloweeDidntRespond.merge(i, 1, Integer::sum);
                if (numOfRoundsFolloweeDidntRespond.get(i) > 2) {
                  followees[i] = false;
                }
              }
            });
    candidates
        .stream()
        .forEach(
            c -> {
              if (!followees[c.sender]) {
                return;
              }
              mapOfFolloweesAndTheirMessages.putIfAbsent(c.sender, new HashSet<>());
              if (!spamIsOK && mapOfFolloweesAndTheirMessages.get(c.sender).contains(c.tx)) {
                followees[c.sender] = false;
              } else {
                mapOfFolloweesAndTheirMessages.get(c.sender).add(c.tx);
                if (spamIsOK || !pendingTransactions.contains(c.tx)) {
                  txsToTransmit.add(c.tx);
                }
              }
            });
    pendingTransactions.addAll(txsToTransmit);
  }
}
