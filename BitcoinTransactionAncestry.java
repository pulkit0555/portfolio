package org.example;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BitcoinTransactionAncestry {

    public static void main(String[] args) {
        BlockDataFetcher blockDataFetcher = new BlockstreamInfoAPIBlockDataFetcher();
        try {
            List<JSONObject> transactions = blockDataFetcher.getBlockTransactions(680000, 100);

            TransactionAncestryCalculator ancestryCalculator = new TransactionAncestryCalculator();
            List<TransactionAncestry> ancestries = ancestryCalculator.calculateAncestries(transactions);

            ancestries.sort(Comparator.comparingInt(TransactionAncestry::getSize).reversed());

            int limit = Math.min(10, ancestries.size());
            for (int i = 0; i < limit; i++) {
                TransactionAncestry ancestry = ancestries.get(i);
                System.out.println(ancestry.getTransactionId() + " " + ancestry.getSize());
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private static class BlockstreamInfoAPIBlockDataFetcher implements BlockDataFetcher {
        private static final String API_ENDPOINT = "https://blockstream.info/api";

        private final OkHttpClient httpClient = new OkHttpClient();

        @Override
        public List<JSONObject> getBlockTransactions(int height, int startIndex) throws IOException, JSONException {
            String blockHash = getBlockHash(height);
            JSONArray transactions = new JSONArray();
            while (true) {
                JSONArray batch = getBlockTransactionsFromAPI(blockHash, startIndex);
                if (batch.length() == 0) {
                    break;
                }
                for (int i = 0; i < batch.length(); i++) {
                    transactions.put(batch.getJSONObject(i));
                }
                startIndex += batch.length();
            }
            List<JSONObject> transactionList = new ArrayList<>();
            for (int i = 0; i < transactions.length(); i++) {
                transactionList.add(transactions.getJSONObject(i));
            }
            return transactionList;
        }

        private JSONArray getBlockTransactionsFromAPI(String blockHash, int startIndex) throws IOException, JSONException {
            String url = API_ENDPOINT + "/block/" + blockHash + "/txs/" + startIndex;
            System.out.println(url);
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }
            JSONArray transactions = new JSONArray(response.body().string());
            response.close();
            return transactions;
        }

        private String getBlockHash(int height) throws IOException {
            String url = API_ENDPOINT + "/block-height/" + height;
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }
            return response.body().string();
        }
    }

    private interface BlockDataFetcher {
        List<JSONObject> getBlockTransactions(int height, int startIndex) throws IOException, JSONException;
    }

    private static class TransactionAncestry {
        private final String transactionId;
        private final List<String> ancestors;

        public TransactionAncestry(String transactionId, List<String> ancestors) {
            this.transactionId = transactionId;
            this.ancestors = ancestors;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public List<String> getAncestors() {
            return ancestors;
        }

        public int getSize() {
            return ancestors.size();
        }
    }

    private static class TransactionAncestryCalculator {
        public List<TransactionAncestry> calculateAncestries(List<JSONObject> transactions) throws JSONException {
            List<TransactionAncestry> ancestries = new ArrayList<>();

            // create a map of all transactions by ID for efficient lookups
            JSONObject[] txById = new JSONObject[transactions.size()];
            for (JSONObject tx : transactions) {
                String txid = tx.getString("txid");
                int index = tx.getInt("txindex");
                txById[index] = tx;
            }

            // calculate the ancestry of each transaction
            for (JSONObject tx : transactions) {
                String txid = tx.getString("txid");
                JSONArray vin = tx.getJSONArray("vin");
                List<String> ancestors = new ArrayList<>();
                for (int i = 0; i < vin.length(); i++) {
                    JSONObject input = vin.getJSONObject(i);
                    String parentTxid = input.getString("txid");
                    int parentOutputIndex = input.getInt("vout");
                    int parentTxIndex = input.getInt("txindex");
                    JSONObject parentTx = txById[parentTxIndex];
                    JSONArray parentOutputs = parentTx.getJSONArray("vout");
                    JSONObject parentOutput = parentOutputs.getJSONObject(parentOutputIndex);
                    String address = "";
                    if (parentOutput.has("scriptpubkey_address")) {
                        address = parentOutput.getString("scriptpubkey_address");
                    }
                    ancestors.add(parentTxid + ":" + parentOutputIndex + ":" + address);
                }
                ancestries.add(new TransactionAncestry(txid, ancestors));
            }

            return ancestries;
        }
    }
}
/*
TransactionAncestry class represents a single transaction's ancestry,
consisting of the transaction ID and a list of all its ancestors.

The TransactionAncestryCalculator class is responsible for calculating the ancestry of
each txn in a list of transactions, and returning a list of TransactionAncestry objects.

The BlockstreamInfoAPIBlockDataFetcher class is responsible for fetching data from the Blockstream API.
It includes a startIndex parameter to fetch up to 25 transactions starting at the given index.

BitcoinTransactionAncestry class is responsible for tying everything together by fetching
the transactions for a given block, calculating their ancestries,
sorting the results by ancestry size, and printing out the top 10 results.
 */
