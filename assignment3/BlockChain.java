import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory
// as it would cause a memory overflow.

public class BlockChain {
  public static final int CUT_OFF_AGE = 10;

  private static final int QUEUE_SIZE = 2 * CUT_OFF_AGE;
  private TransactionPool transactionPool;
  private Map<ByteArrayWrapper, UTXOPool> utxoPoolMap;
  private LinkedBlockingQueue<List<Block>> blocksInMemory;
  private Map<ByteArrayWrapper, Integer> blockHeightMap;
  private int minHeightInQueue;
  private Function<byte[], ByteArrayWrapper> wrapper = ByteArrayWrapper::new;

  private Function<Integer, Consumer<Block>> addBlockAtHeight =
      height ->
          (block -> {
            if (height > minHeightInQueue + blocksInMemory.size() - 1) {
              if (blocksInMemory.size() == QUEUE_SIZE) {
                minHeightInQueue++;
                blocksInMemory.poll();
              }
              blocksInMemory.add(new ArrayList<>());
            }

            Iterator<List<Block>> queueIterator = blocksInMemory.iterator();
            List<Block> currentItem = queueIterator.next();
            for (int i = 0; i < height - minHeightInQueue; i++) {
              currentItem = queueIterator.next();
            }
            currentItem.add(block);
            blockHeightMap.put(wrapper.apply(block.getHash()), height);
          });

  private void addUTXOsOfThisTxToPool(Transaction tx, UTXOPool uPool) {
    IntStream.range(0, tx.getOutputs().size())
        .forEach(i -> uPool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i)));
  }

  /**
   * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
   * block
   */
  public BlockChain(Block genesisBlock) {
    minHeightInQueue = 1;
    transactionPool = new TransactionPool();
    blocksInMemory = new LinkedBlockingQueue<>(QUEUE_SIZE);
    blockHeightMap = new HashMap<>();
    utxoPoolMap = new HashMap<>();
    UTXOPool genesisBlockUtxoPool = new UTXOPool();
    addUTXOsOfThisTxToPool(genesisBlock.getCoinbase(), genesisBlockUtxoPool);
    utxoPoolMap.put(wrapper.apply(genesisBlock.getHash()), genesisBlockUtxoPool);
    addBlockAtHeight.apply(1).accept(genesisBlock);
  }

  /** Get the maximum height block */
  public Block getMaxHeightBlock() {
    int queueSize = blocksInMemory.size();
    return (Block) blocksInMemory.toArray(new List[queueSize])[queueSize - 1].get(0);
  }

  /** Get the UTXOPool for mining a new block on top of max height block */
  public UTXOPool getMaxHeightUTXOPool() {
    return utxoPoolMap.get(wrapper.apply(getMaxHeightBlock().getHash()));
  }

  /** Get the transaction pool to mine a new block */
  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  /**
   * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
   * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
   *
   * <p>For example, you can try creating a new block over the genesis block (block height 2) if the
   * block chain height is {@code <= CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1},
   * you cannot create a new block at height 2.
   *
   * @return true if block is successfully added
   */
  public boolean addBlock(Block block) {
    if (block.getPrevBlockHash() == null) {
      return false;
    }
    ByteArrayWrapper parentHash = wrapper.apply(block.getPrevBlockHash());
    Integer heightOfParent = blockHeightMap.get(parentHash);
    if (heightOfParent == null) {
      return false;
    }
    if (heightOfParent + 1 < minHeightInQueue + blocksInMemory.size() - 1 - CUT_OFF_AGE) {
      return false;
    }

    UTXOPool copyOfParentUTXOPool = new UTXOPool(utxoPoolMap.get(parentHash));
    TxHandler txHandler = new TxHandler(copyOfParentUTXOPool);
    Transaction[] validTransactions =
        txHandler.handleTxs(block.getTransactions().toArray(new Transaction[0]));
    if (validTransactions.length != block.getTransactions().size()) {
      return false;
    }
    copyOfParentUTXOPool = txHandler.getUTXOPool();
    addUTXOsOfThisTxToPool(block.getCoinbase(), copyOfParentUTXOPool);
    utxoPoolMap.put(wrapper.apply(block.getHash()), copyOfParentUTXOPool);
    addBlockAtHeight.apply(heightOfParent + 1).accept(block);
    return true;
  }

  /** Add a transaction to the transaction pool */
  public void addTransaction(Transaction tx) {
    transactionPool.addTransaction(tx);
  }
}
