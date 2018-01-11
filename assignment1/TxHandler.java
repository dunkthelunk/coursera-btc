import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TxHandler {

  private UTXOPool utxoPool;
  private Function<Transaction, List<UTXO>> getUTXOsClaimedByTx =
      transaction ->
          transaction
              .getInputs()
              .stream()
              .map(in -> new UTXO(in.prevTxHash, in.outputIndex))
              .collect(Collectors.toList());

  /**
   * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
   * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
   * constructor.
   */
  public TxHandler(UTXOPool utxoPool) {
    this.utxoPool = new UTXOPool(utxoPool);
  }

  /**
   * @return true if: (1) all outputs claimed by {@code tx} are in the current UTXO pool, (2) the
   *     signatures on each input of {@code tx} are valid, (3) no UTXO is claimed multiple times by
   *     {@code tx}, (4) all of {@code tx}s output values are non-negative, and (5) the sum of
   *     {@code tx}s input values is greater than or equal to the sum of its output values; and
   *     false otherwise.
   */
  public boolean isValidTx(Transaction tx) {

    // 1
    Predicate<Transaction> utxosClaimedByTxAreInPool =
        transaction -> utxoPool.getAllUTXO().containsAll(getUTXOsClaimedByTx.apply(transaction));

    // 2
    Predicate<Transaction> allInputsHaveValidSignature =
        transaction ->
            transaction
                .getInputs()
                .stream()
                .allMatch(
                    in ->
                        Crypto.verifySignature(
                            utxoPool.getTxOutput(new UTXO(in.prevTxHash, in.outputIndex)).address,
                            transaction.getRawDataToSign(transaction.getInputs().indexOf(in)),
                            in.signature));

    // 3
    Predicate<Transaction> noUTXOisClaimedMultipleTimes =
        transaction ->
            getUTXOsClaimedByTx.apply(transaction).size()
                == getUTXOsClaimedByTx.andThen(HashSet::new).apply(transaction).size();

    // 4
    Predicate<Transaction> allOutputValuesAreNonNegative =
        transaction ->
            transaction.getOutputs().stream().map(o -> o.value).noneMatch(value -> value < 0.0);

    // 5
    Predicate<Transaction> totalOutputValueLessThanTotalInputValue =
        transaction ->
            transaction.getOutputs().stream().mapToDouble(o -> o.value).sum()
                < getUTXOsClaimedByTx
                    .apply(transaction)
                    .stream()
                    .map(utxoPool::getTxOutput)
                    .mapToDouble(o -> o.value)
                    .sum();

    return utxosClaimedByTxAreInPool
        .and(allInputsHaveValidSignature)
        .and(noUTXOisClaimedMultipleTimes)
        .and(allOutputValuesAreNonNegative)
        .and(totalOutputValueLessThanTotalInputValue)
        .test(tx);
  }

  /**
   * Handles each epoch by receiving an unordered array of proposed transactions, checking each
   * transaction for correctness, returning a mutually valid array of accepted transactions, and
   * updating the current UTXO pool as appropriate.
   */
  public Transaction[] handleTxs(Transaction[] possibleTxs) {
    List<Transaction> validTransactions = new ArrayList<>();
    List<Transaction> possibleTransactions = new ArrayList<>(Arrays.asList(possibleTxs));
    List<Transaction> validTxInThisIter;
    while (!(validTxInThisIter = handleTxsIter(possibleTransactions)).isEmpty()) {
      validTransactions.addAll(validTxInThisIter);
      possibleTransactions.removeAll(validTxInThisIter);
    }
    return validTransactions.toArray(new Transaction[validTransactions.size()]);
  }

  private List<Transaction> handleTxsIter(List<Transaction> possibleTxs) {
    List<Transaction> validTxsInThisIter = new ArrayList<>();
    possibleTxs
        .stream()
        .filter(this::isValidTx)
        .forEach(
            tx -> {
              tx.getInputs()
                  .stream()
                  .map(in -> new UTXO(in.prevTxHash, in.outputIndex))
                  .forEach(utxoPool::removeUTXO);
              IntStream.range(0, tx.getOutputs().size())
                  .forEach(i -> utxoPool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i)));
              validTxsInThisIter.add(tx);
            });
    return validTxsInThisIter;
  }
}
