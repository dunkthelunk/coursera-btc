import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.IntStream;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory
// as it would cause a memory overflow.

public class BlockChain {
  public static final int CUT_OFF_AGE = 10;
  private TransactionPool transactionPool;
  private Map<ByteArrayWrapper, UTXOPool> utxoPoolMap;
  private LinkedBlockingQueue<List<Block>> blocksInMemory;
  private Map<ByteArrayWrapper, Integer> blockHeightMap;
  private int minHeightInMem;
  private Function<byte[], ByteArrayWrapper> wrapper = ByteArrayWrapper::new;

  private void addUTXOsOfThisTxToPool(Transaction tx, UTXOPool uPool) {
    IntStream.range(0, tx.getOutputs().size())
        .forEach(i -> uPool.addUTXO(new UTXO(tx.getHash(), i), tx.getOutput(i)));
  }
  /**
   * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
   * block
   */
  public BlockChain(Block genesisBlock) {
    minHeightInMem = 1;
    transactionPool = new TransactionPool();
    blocksInMemory = new LinkedBlockingQueue<>(CUT_OFF_AGE);
    List<Block> blocksAtHeight0 = Collections.singletonList(genesisBlock);
    blocksInMemory.offer(blocksAtHeight0);
    utxoPoolMap = new HashMap<>();
    UTXOPool genesisBlockUtxoPool = new UTXOPool();
    addUTXOsOfThisTxToPool(genesisBlock.getCoinbase(), genesisBlockUtxoPool);
    utxoPoolMap.put(wrapper.apply(genesisBlock.getHash()), genesisBlockUtxoPool);
    blockHeightMap = new HashMap<>();
    blockHeightMap.put(wrapper.apply(genesisBlock.getHash()), minHeightInMem);
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
    if (blockHeightMap.containsKey(wrapper.apply(block.getHash()))) {
      return true;
    }
    ByteArrayWrapper parentHash = wrapper.apply(block.getPrevBlockHash());
    Integer heightOfParent = blockHeightMap.get(parentHash);
    if (heightOfParent == null || heightOfParent < minHeightInMem) {
      return false;
    }

    UTXOPool copyOfParentUTXOPool = new UTXOPool(utxoPoolMap.get(parentHash));

    addUTXOsOfThisTxToPool(block.getCoinbase(), copyOfParentUTXOPool);
    TxHandler txHandler = new TxHandler(copyOfParentUTXOPool);
    List<Transaction> validTransactions =
        Arrays.asList(txHandler.handleTxs(block.getTransactions().toArray(new Transaction[0])));
    if (validTransactions.size() == block.getTransactions().size()) {
      utxoPoolMap.put(wrapper.apply(block.getHash()), txHandler.getUTXOPool());
      blockHeightMap.put(wrapper.apply(block.getHash()), heightOfParent + 1);
    } else {
      return false;
    }
    if (heightOfParent == minHeightInMem + CUT_OFF_AGE - 1) {
      minHeightInMem++;
      try {
        blocksInMemory.take();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    if (heightOfParent == minHeightInMem + blocksInMemory.size() - 1) {
      List<Block> latestBlocks = new ArrayList<>();
      latestBlocks.add(block);
      blocksInMemory.offer(latestBlocks);
      return true;
    }
    Iterator<List<Block>> it = blocksInMemory.iterator();
    List<Block> currentItem = it.next();
    for (int i = 0; i <= heightOfParent - minHeightInMem; i++) {
      currentItem = it.next();
    }
    currentItem.add(block);
    return true;
  }

  /** Add a transaction to the transaction pool */
  public void addTransaction(Transaction tx) {
    transactionPool.addTransaction(tx);
  }
}
