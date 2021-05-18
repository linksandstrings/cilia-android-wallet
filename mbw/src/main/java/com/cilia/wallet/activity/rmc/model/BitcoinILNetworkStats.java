package com.cilia.wallet.activity.rmc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BitcoinILNetworkStats {
    public BitcoinILNetworkStats() {
    }

    @JsonProperty("difficulty")
    public long difficulty;
}
