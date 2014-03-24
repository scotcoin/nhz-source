package nhz;

import nhz.peer.Peer;
import nhz.peer.Peers;
import nhz.util.Convert;
import nhz.util.JSON;
import nhz.util.Listener;
import nhz.util.Listeners;
import nhz.util.Logger;
import nhz.util.ThreadPool;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class TransactionProcessorImpl implements TransactionProcessor {

    private static final TransactionProcessorImpl instance = new TransactionProcessorImpl();

    static TransactionProcessorImpl getInstance() {
        return instance;
    }

    private final ConcurrentMap<Long, TransactionImpl> doubleSpendingTransactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, TransactionImpl> unconfirmedTransactions = new ConcurrentHashMap<>();
    private final Collection<TransactionImpl> allUnconfirmedTransactions = Collections.unmodifiableCollection(unconfirmedTransactions.values());
    private final ConcurrentMap<Long, TransactionImpl> nonBroadcastedTransactions = new ConcurrentHashMap<>();
    private static class TransactionHashInfo {
        private final Long transactionId;
        private final int expiration;
        private TransactionHashInfo(Transaction transaction) {
            this.transactionId = transaction.getId();
            this.expiration = transaction.getExpiration();
        }
    }
    private final ConcurrentMap<String, TransactionHashInfo> transactionHashes = new ConcurrentHashMap<>();
    private final Listeners<List<Transaction>,Event> transactionListeners = new Listeners<>();

    private final Runnable removeUnconfirmedTransactionsThread = new Runnable() {

        @Override
        public void run() {

            try {
                try {

                    int curTime = Convert.getEpochTime();
                    List<Transaction> removedUnconfirmedTransactions = new ArrayList<>();

                    synchronized (BlockchainImpl.getInstance()) {
                        Iterator<TransactionImpl> iterator = unconfirmedTransactions.values().iterator();
                        while (iterator.hasNext()) {
                            TransactionImpl transaction = iterator.next();
                            boolean isNotValid = false;
                            try {
                                transaction.validateAttachment();
                            } catch (NhzException.ValidationException e) {
                                isNotValid = true;
                            }
                            if (transaction.getExpiration() < curTime || isNotValid) {
                                iterator.remove();
                                transaction.undoUnconfirmed();
                                removedUnconfirmedTransactions.add(transaction);
                            }
                        }
                    }

                    if (removedUnconfirmedTransactions.size() > 0) {
                        transactionListeners.notify(removedUnconfirmedTransactions, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
                    }

                } catch (Exception e) {
                    Logger.logDebugMessage("Error removing unconfirmed transactions", e);
                }
            } catch (Throwable t) {
                Logger.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }

        }

    };

    private final Runnable rebroadcastTransactionsThread = new Runnable() {

        @Override
        public void run() {

            try {
                try {
                    JSONArray transactionsData = new JSONArray();

                    int curTime = Convert.getEpochTime();
                    for (TransactionImpl transaction : nonBroadcastedTransactions.values()) {
                        boolean isNotValid = false;
                        try {
                            transaction.validateAttachment();
                        } catch (NhzException.ValidationException e) {
                            isNotValid = true;
                        }
                        if (TransactionDb.hasTransaction(transaction.getId()) || transaction.getExpiration() < curTime
                                || isNotValid) {
                            nonBroadcastedTransactions.remove(transaction.getId());
                        } else if (transaction.getTimestamp() < curTime - 30) {
                            transactionsData.add(transaction.getJSONObject());
                        }
                    }

                    if (transactionsData.size() > 0) {
                        JSONObject peerRequest = new JSONObject();
                        peerRequest.put("requestType", "processTransactions");
                        peerRequest.put("transactions", transactionsData);
                        Peers.sendToSomePeers(peerRequest);
                    }

                } catch (Exception e) {
                    Logger.logDebugMessage("Error in transaction re-broadcasting thread", e);
                }
            } catch (Throwable t) {
                Logger.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }

        }

    };

    private final Runnable processTransactionsThread = new Runnable() {

        private final JSONStreamAware getUnconfirmedTransactionsRequest;
        {
            JSONObject request = new JSONObject();
            request.put("requestType", "getUnconfirmedTransactions");
            getUnconfirmedTransactionsRequest = JSON.prepareRequest(request);
        }

        @Override
        public void run() {
            try {
                try {
                    Peer peer = Peers.getAnyPeer(Peer.State.CONNECTED, true);
                    if (peer == null) {
                        return;
                    }
                    JSONObject response = peer.send(getUnconfirmedTransactionsRequest);
                    if (response == null) {
                        return;
                    }
                    JSONArray transactionsData = (JSONArray)response.get("unconfirmedTransactions");
                    if (transactionsData == null || transactionsData.size() == 0) {
                        return;
                    }
                    processPeerTransactions(transactionsData, false);
                } catch (Exception e) {
                    Logger.logDebugMessage("Error processing unconfirmed transactions from peer", e);
                }
            } catch (Throwable t) {
                Logger.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }
        }

    };

    private TransactionProcessorImpl() {
        ThreadPool.scheduleThread(processTransactionsThread, 5);
        ThreadPool.scheduleThread(removeUnconfirmedTransactionsThread, 1);
        ThreadPool.scheduleThread(rebroadcastTransactionsThread, 60);
    }

    @Override
    public boolean addListener(Listener<List<Transaction>> listener, Event eventType) {
        return transactionListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<List<Transaction>> listener, Event eventType) {
        return transactionListeners.removeListener(listener, eventType);
    }

    @Override
    public Collection<TransactionImpl> getAllUnconfirmedTransactions() {
        return allUnconfirmedTransactions;
    }

    @Override
    public Transaction getUnconfirmedTransaction(Long transactionId) {
        return unconfirmedTransactions.get(transactionId);
    }

    @Override
    public void broadcast(Transaction transaction) {
        processTransactions(Arrays.asList((TransactionImpl)transaction), true);
        nonBroadcastedTransactions.put(transaction.getId(), (TransactionImpl) transaction);
        Logger.logDebugMessage("Accepted new transaction " + transaction.getStringId());
    }

    @Override
    public void processPeerTransactions(JSONObject request) {
        JSONArray transactionsData = (JSONArray)request.get("transactions");
        processPeerTransactions(transactionsData, true);
    }

    @Override
    public Transaction parseTransaction(byte[] bytes) throws NhzException.ValidationException {

        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            byte type = buffer.get();
            byte subtype = buffer.get();
            int timestamp = buffer.getInt();
            short deadline = buffer.getShort();
            byte[] senderPublicKey = new byte[32];
            buffer.get(senderPublicKey);
            Long recipientId = buffer.getLong();
            int amount = buffer.getInt();
            int fee = buffer.getInt();
            Long referencedTransactionId = Convert.zeroToNull(buffer.getLong());
            byte[] signature = new byte[64];
            buffer.get(signature);

            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            TransactionImpl transaction = new TransactionImpl(transactionType, timestamp, deadline, senderPublicKey, recipientId, amount,
                    fee, referencedTransactionId, signature);

            transactionType.loadAttachment(transaction, buffer);

            return transaction;

        } catch (RuntimeException e) {
            throw new NhzException.ValidationException(e.toString());
        }
    }

    @Override
    public Transaction newTransaction(short deadline, byte[] senderPublicKey, Long recipientId,
                                      int amount, int fee, Long referencedTransactionId) throws NhzException.ValidationException {
        return new TransactionImpl(TransactionType.Payment.ORDINARY, Convert.getEpochTime(), deadline, senderPublicKey, recipientId, amount, fee, referencedTransactionId, null);
    }

    @Override
    public Transaction newTransaction(short deadline, byte[] senderPublicKey, Long recipientId,
                                      int amount, int fee, Long referencedTransactionId, Attachment attachment)
            throws NhzException.ValidationException {
        TransactionImpl transaction = new TransactionImpl(attachment.getTransactionType(), Convert.getEpochTime(), deadline, senderPublicKey, recipientId, amount, fee,
                referencedTransactionId, null);
        transaction.setAttachment(attachment);
        return transaction;
    }

    TransactionImpl parseTransaction(JSONObject transactionData) throws NhzException.ValidationException {

        try {

            byte type = ((Long)transactionData.get("type")).byteValue();
            byte subtype = ((Long)transactionData.get("subtype")).byteValue();
            int timestamp = ((Long)transactionData.get("timestamp")).intValue();
            short deadline = ((Long)transactionData.get("deadline")).shortValue();
            byte[] senderPublicKey = Convert.parseHexString((String) transactionData.get("senderPublicKey"));
            Long recipientId = Convert.parseUnsignedLong((String) transactionData.get("recipient"));
            if (recipientId == null) recipientId = 0L; // ugly
            int amount = ((Long)transactionData.get("amount")).intValue();
            int fee = ((Long)transactionData.get("fee")).intValue();
            Long referencedTransactionId = Convert.parseUnsignedLong((String) transactionData.get("referencedTransaction"));
            byte[] signature = Convert.parseHexString((String) transactionData.get("signature"));

            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            TransactionImpl transaction = new TransactionImpl(transactionType, timestamp, deadline, senderPublicKey, recipientId, amount, fee,
                    referencedTransactionId, signature);

            JSONObject attachmentData = (JSONObject)transactionData.get("attachment");

            transactionType.loadAttachment(transaction, attachmentData);

            return transaction;

        } catch (RuntimeException e) {
            throw new NhzException.ValidationException(e.toString());
        }
    }

    void clear() {
        unconfirmedTransactions.clear();
        doubleSpendingTransactions.clear();
        nonBroadcastedTransactions.clear();
        transactionHashes.clear();
    }

    void apply(BlockImpl block) {
        block.apply();
        for (TransactionImpl transaction : block.getTransactions()) {
            if (! unconfirmedTransactions.containsKey(transaction.getId())) {
                transaction.applyUnconfirmed();
            }
            transaction.apply();
            transactionHashes.put(transaction.getHash(), new TransactionHashInfo(transaction));
        }
        purgeExpiredHashes(block.getTimestamp());
    }

    void undo(BlockImpl block) throws TransactionType.UndoNotSupportedException {
        List<Transaction> addedUnconfirmedTransactions = new ArrayList<>();
        for (TransactionImpl transaction : block.getTransactions()) {
            TransactionHashInfo transactionHashInfo = transactionHashes.get(transaction.getHash());
            if (transactionHashInfo != null && transactionHashInfo.transactionId.equals(transaction.getId())) {
                transactionHashes.remove(transaction.getHash());
            }
            unconfirmedTransactions.put(transaction.getId(), transaction);
            transaction.undo();
            addedUnconfirmedTransactions.add(transaction);
        }
        if (addedUnconfirmedTransactions.size() > 0) {
            transactionListeners.notify(addedUnconfirmedTransactions, TransactionProcessor.Event.ADDED_UNCONFIRMED_TRANSACTIONS);
        }
    }

    TransactionImpl checkTransactionHashes(BlockImpl block) {
        TransactionImpl duplicateTransaction = null;
        for (TransactionImpl transaction : block.getTransactions()) {
            if (transactionHashes.putIfAbsent(transaction.getHash(), new TransactionHashInfo(transaction)) != null && block.getHeight() != 58294) {
                duplicateTransaction = transaction;
                break;
            }
        }

        if (duplicateTransaction != null) {
            for (TransactionImpl transaction : block.getTransactions()) {
                if (! transaction.equals(duplicateTransaction)) {
                    TransactionHashInfo transactionHashInfo = transactionHashes.get(transaction.getHash());
                    if (transactionHashInfo != null && transactionHashInfo.transactionId.equals(transaction.getId())) {
                        transactionHashes.remove(transaction.getHash());
                    }
                }
            }
        }
        return duplicateTransaction;
    }

    void updateUnconfirmedTransactions(BlockImpl block) {
        List<Transaction> addedConfirmedTransactions = new ArrayList<>();
        List<Transaction> removedUnconfirmedTransactions = new ArrayList<>();

        for (Transaction transaction : block.getTransactions()) {
            addedConfirmedTransactions.add(transaction);
            Transaction removedTransaction = unconfirmedTransactions.remove(transaction.getId());
            if (removedTransaction != null) {
                removedUnconfirmedTransactions.add(removedTransaction);
            }
            // TODO: Remove from double-spending transactions
        }

        if (removedUnconfirmedTransactions.size() > 0) {
            transactionListeners.notify(removedUnconfirmedTransactions, TransactionProcessor.Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
        }
        if (addedConfirmedTransactions.size() > 0) {
            transactionListeners.notify(addedConfirmedTransactions, TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
        }

    }

    private void purgeExpiredHashes(int blockTimestamp) {
        Iterator<Map.Entry<String, TransactionHashInfo>> iterator = transactionHashes.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiration < blockTimestamp) {
                iterator.remove();
            }
        }
    }

    private void processPeerTransactions(JSONArray transactionsData, final boolean sendToPeers) {
        List<TransactionImpl> transactions = new ArrayList<>();
        for (Object transactionData : transactionsData) {
            try {
                transactions.add(parseTransaction((JSONObject) transactionData));
            } catch (NhzException.ValidationException e) {
                if (! (e instanceof TransactionType.NotYetEnabledException)) {
                    Logger.logDebugMessage("Dropping invalid transaction: " + e.toString());
                }
            }
        }
        processTransactions(transactions, sendToPeers);
        for (TransactionImpl transaction : transactions) {
            nonBroadcastedTransactions.remove(transaction.getId());
        }
    }

    private void processTransactions(List<TransactionImpl> transactions, final boolean sendToPeers) {
        JSONArray validTransactionsData = new JSONArray();
        List<Transaction> addedUnconfirmedTransactions = new ArrayList<>();
        List<Transaction> addedDoubleSpendingTransactions = new ArrayList<>();

        for (TransactionImpl transaction : transactions) {

            try {

                int curTime = Convert.getEpochTime();
                if (transaction.getTimestamp() > curTime + 15 || transaction.getExpiration() < curTime
                        || transaction.getDeadline() > 1440) {
                    continue;
                }

                boolean doubleSpendingTransaction;

                synchronized (BlockchainImpl.getInstance()) {

                    Long id = transaction.getId();
                    if (TransactionDb.hasTransaction(id) || unconfirmedTransactions.containsKey(id)
                            || doubleSpendingTransactions.containsKey(id) || !transaction.verify()) {
                        continue;
                    }

                    if (transactionHashes.containsKey(transaction.getHash())) {
                        continue;
                    }

                    doubleSpendingTransaction = !transaction.applyUnconfirmed();

                    if (doubleSpendingTransaction) {
                        doubleSpendingTransactions.put(id, transaction);
                    } else {
                        if (sendToPeers) {
                            if (nonBroadcastedTransactions.containsKey(id)) {
                                Logger.logDebugMessage("Received back transaction " + transaction.getStringId()
                                        + " that we generated, will not forward to peers");
                            } else {
                                validTransactionsData.add(transaction.getJSONObject());
                            }
                        }
                        unconfirmedTransactions.put(id, transaction);
                    }
                }

                if (doubleSpendingTransaction) {
                    addedDoubleSpendingTransactions.add(transaction);
                } else {
                    addedUnconfirmedTransactions.add(transaction);
                }

            } catch (RuntimeException e) {
                Logger.logMessage("Error processing transaction", e);
            }

        }

        if (validTransactionsData.size() > 0) {
            JSONObject peerRequest = new JSONObject();
            peerRequest.put("requestType", "processTransactions");
            peerRequest.put("transactions", validTransactionsData);
            Peers.sendToSomePeers(peerRequest);
        }

        if (addedUnconfirmedTransactions.size() > 0) {
            transactionListeners.notify(addedUnconfirmedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
        }
        if (addedDoubleSpendingTransactions.size() > 0) {
            transactionListeners.notify(addedDoubleSpendingTransactions, Event.ADDED_DOUBLESPENDING_TRANSACTIONS);
        }

    }

}