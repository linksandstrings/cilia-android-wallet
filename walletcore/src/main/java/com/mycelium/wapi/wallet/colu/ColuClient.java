package com.mycelium.wapi.wallet.colu;

import com.google.api.client.http.HttpTransport;
import com.mrd.bitlib.model.BitcoinAddress;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.model.BitcoinTransaction;
import com.mrd.bitlib.util.HexUtils;
import com.mycelium.wapi.wallet.coins.Value;
import com.mycelium.wapi.wallet.colu.json.*;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.security.Security;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for the Colu HTTP API.
 */
public class ColuClient {
    private static final String TAG = "ColuClient";

    private static final boolean coluAutoSelectUtxo = true;

    public NetworkParameters network;

    private AdvancedHttpClient coloredCoinsClient;
    private AdvancedHttpClient blockExplorerClient;

    public ColuClient(NetworkParameters network, String[] apiUrls, String[] explorerUrls, @Nullable SSLSocketFactory socketFactory) {

        this.coloredCoinsClient = new AdvancedHttpClient(apiUrls/*BuildConfig.ColoredCoinsApiURLs*/, socketFactory);
        this.blockExplorerClient = new AdvancedHttpClient(explorerUrls/*BuildConfig.ColuBlockExplorerApiURLs*/, socketFactory);
        this.network = network;

        // Level.CONFIG logs everything but Authorization header
        // Level.ALL also logs Authorization header
        // Type this to really enable: adb shell setprop log.tag.HttpTransport DEBUG
        Logger.getLogger(HttpTransport.class.getName()).setLevel(Level.CONFIG);
        initialize();
    }

    private void initialize() {
        Security.addProvider(new BouncyCastleProvider());
    }

    public AssetMetadata getMetadata(String assetId) throws IOException {
        String endpoint = "assetmetadata/" + assetId;
        return coloredCoinsClient.sendGetRequest(AssetMetadata.class, endpoint);
    }

    public AddressInfo.Json getBalance(BitcoinAddress address) throws IOException {
        String endpoint = "getaddressinfo?address=" + address.toString();
        return blockExplorerClient.sendGetRequest(AddressInfo.Json.class, endpoint);
    }

    public AddressTransactionsInfo.Json getAddressTransactions(BitcoinAddress address) throws IOException {
        return getAddressTransactions(address.toString());
    }

    public AddressTransactionsInfo.Json getAddressTransactions(String address) throws IOException {
        String endpoint = "getaddressinfowithtransactions?address=" + address;
        return blockExplorerClient.sendGetRequest(AddressTransactionsInfo.Json.class, endpoint);
    }

    //TODO: move most of the logic to ColuManager
    public ColuBroadcastTxHex.Json prepareTransaction(BitcoinAddress destAddress, List<BitcoinAddress> src,
                                                      Value nativeAmount,
                                                      long txFee)
            throws IOException {
        if (destAddress == null) {
            //Log.e(TAG, "destAddress is null");
            return null;
        }
        if (src == null || src.size() == 0) {
            //Log.e(TAG, "src is null or empty");
            return null;
        }
        if (nativeAmount == null) {
            //Log.e(TAG, "nativeAmount is null");
            return null;
        }
        ColuTransactionRequest.Json request = new ColuTransactionRequest.Json();
        List<ColuTxDest.Json> to = new LinkedList<>();
        ColuTxDest.Json dest = new ColuTxDest.Json();
        dest.address = destAddress.toString();
        dest.amount = nativeAmount.getValueAsLong();
        dest.assetId = nativeAmount.type.getId();
        to.add(dest);

        request.to = to;
        request.fee = txFee;

        ColuTxFlags.Json flags = new ColuTxFlags.Json();
        flags.splitChange = true;
        request.flags = flags;

        // v1: let colu chose source tx
        if (ColuClient.coluAutoSelectUtxo) {
            LinkedList<String> from = new LinkedList<>();
            for (BitcoinAddress addr : src) {
                from.add(addr.toString());
            }
            request.from = from;
            request.financeOutputTxid = "";
        } else {
            // v2: chose utxo ourselves
//            LinkedList<String> sendutxo = new LinkedList<>();
//            double selectedAmount = 0;
//            double selectedSatoshiAmount = 0;
//            for (Address addr : src) {
//                // get list of address utxo and filter out those who have asset
//                List<Utxo.Json> addressUnspent = coluAccount.getAddressUnspent(addr.toString());
//                for (Utxo.Json utxo : addressUnspent) {
//                    // case 1: this is a BTC/satoshi utxo, we select it for fee finance
//                    // Colu server will only take as much as it needs from the utxo we send it
//                    if (utxo.assets == null || utxo.assets.size() == 0) {
//                        sendutxo.add(utxo.txid + ":" + utxo.index);
//                        selectedSatoshiAmount = selectedSatoshiAmount + utxo.value;
//                    }
//                    // case 2: asset utxo. If it is of the type we care, and we need more, select it.
//                    for (Asset.Json asset : utxo.assets) {
//                        if (asset.assetId.compareTo(nativeAmount.type.getId()) == 0) {
//                            if (selectedAmount < dest.amount) {
//                                sendutxo.add(utxo.txid + ":" + utxo.index);
//                                selectedAmount = selectedAmount + asset.amount;
//                            }
//                        } else if (asset.assetId.isEmpty() || asset.assetId.compareTo("") == 0) {
//                            sendutxo.add(utxo.txid + ":" + utxo.index);
//                            selectedSatoshiAmount = selectedSatoshiAmount + utxo.value;
//                        }
//                    }
//                }
//            }
//            request.sendutxo = sendutxo;
//            // do we need to set this one as well ?
//            request.financeOutputTxid = "";
        }
        return coloredCoinsClient.sendPostRequest(ColuBroadcastTxHex.Json.class, "sendasset", null, request);
    }

    public ColuBroadcastTxId.Json broadcastTransaction(BitcoinTransaction coluSignedTransaction) throws IOException {
        ColuBroadcastTxHex.Json tx = new ColuBroadcastTxHex.Json();
        byte[] signedTr = coluSignedTransaction.toBytes();
        tx.txHex = HexUtils.toHex(signedTr);
        return coloredCoinsClient.sendPostRequest(ColuBroadcastTxId.Json.class, "broadcast", null, tx);
    }

}
