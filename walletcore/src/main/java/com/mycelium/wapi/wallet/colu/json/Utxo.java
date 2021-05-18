package com.mycelium.wapi.wallet.colu.json;

import java.util.List;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

public class Utxo {
    public static class Json extends GenericJson {
        @Key
        public String _id;

        @Key
        public String txid;

        @Key
        public int index;

        @Key
        public int value;

        @Key
        public int blockheight;

        @Key
        public boolean used;

        @Key
        public List<Asset.Json> assets;

        @Key
        public ScriptPubKey.Json scriptPubKey;
    }
}
