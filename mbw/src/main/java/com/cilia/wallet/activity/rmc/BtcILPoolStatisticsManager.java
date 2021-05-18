package com.cilia.wallet.activity.rmc;

import android.util.Log;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.cilia.wallet.activity.rmc.model.BitcoinILNetworkStats;
import com.cilia.wallet.external.rmc.remote.StatRmcFactory;
import com.cilia.wallet.external.rmc.remote.StatRmcService;
import com.mycelium.wapi.wallet.colu.ColuAccount;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import retrofit.RetrofitError;

public class BtcILPoolStatisticsManager {
    public static final String TAG = "RMCStatistic";
    private static final String BITCOINIL_NETWORK_STATS_URL = "https://api.blockchain.info/stats";

    private ColuAccount coluAccount;

    public static class PoolStatisticInfo {
        public long totalRmcHashrate;
        public long yourRmcHashrate;
        public long difficulty;
        public long accruedIncome;

        public PoolStatisticInfo(long totalRmcHashrate, long yourRmcHashrate) {
            this.totalRmcHashrate = totalRmcHashrate;
            this.yourRmcHashrate = yourRmcHashrate;
        }
    }

    public BtcILPoolStatisticsManager(ColuAccount coluAccount) {
        this.coluAccount = coluAccount;
    }

    public PoolStatisticInfo getStatistics() {
        StatRmcService service = StatRmcFactory.getService();
        long totalRmcHashrate = -1;
        try {
            totalRmcHashrate = service.getCommonHashrate();
        } catch (Exception e) {
            Log.e(TAG, "service.getCommonHashrate", e);
        }

        String address = coluAccount.getReceiveAddress().toString();
        long yourRmcHashrate = -1;
        try {
            yourRmcHashrate = service.getHashrate(address);
        } catch (RetrofitError e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                yourRmcHashrate = 0;
            } else {
                Log.e(TAG, "service.getHashrate", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "service.getHashrate", e);
        }

        long accruedIncome = -1;
        try {
            accruedIncome = service.getBalance(address);
        } catch (RetrofitError e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                accruedIncome = 0;
            } else {
                Log.e(TAG, "service.getBalance", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "service.getBalance", e);
        }
        try {
            Map<String, List<String>> paidTransactions = service.getPaidTransactions(address);
            if (paidTransactions != null) {
                for (List<String> thx : paidTransactions.values()) {
                    accruedIncome += Long.parseLong(thx.get(0));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "service.getPaidTransactions", e);
        }
        PoolStatisticInfo info = new PoolStatisticInfo(totalRmcHashrate, yourRmcHashrate);
        BitcoinILNetworkStats stats = getBitcoinILNetworkStats();
        if (stats != null) {
            info.difficulty = stats.difficulty;
        }
        info.accruedIncome = accruedIncome;
        return info;
    }

    private BitcoinILNetworkStats getBitcoinILNetworkStats() {
        HttpRequestFactory requestFactory = new NetHttpTransport()
                .createRequestFactory(new HttpRequestInitializer() {
                    @Override
                    public void initialize(HttpRequest request) {
                    }
                });
        try {
            HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(BITCOINIL_NETWORK_STATS_URL));
            HttpResponse response = request.execute();
            InputStream inputStream = response.getContent();
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return objectMapper.readValue(inputStream, BitcoinILNetworkStats.class);
        } catch (Exception e) {
            Log.e("BtcIL Stats", "", e);
        }
        return null;
    }
}
