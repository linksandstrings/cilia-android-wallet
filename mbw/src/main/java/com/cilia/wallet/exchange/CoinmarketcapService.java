package com.cilia.wallet.exchange;


import com.cilia.wallet.exchange.model.CoinmarketcapRate;

import retrofit.http.GET;

public interface CoinmarketcapService {
    @GET("/v1/ticker/russian-mining-coin")
    CoinmarketcapRate getRmcRate();
}
