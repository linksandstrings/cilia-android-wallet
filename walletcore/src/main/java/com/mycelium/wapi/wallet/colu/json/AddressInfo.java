package com.mycelium.wapi.wallet.colu.json;

import java.util.List;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;

public class AddressInfo {
	public static class Json extends GenericJson {
		@Key
		public String address;

		@Key
		public List<Utxo.Json> utxos;

		@Key
		public List<AssetBalance.Json> assets;
	}
}
