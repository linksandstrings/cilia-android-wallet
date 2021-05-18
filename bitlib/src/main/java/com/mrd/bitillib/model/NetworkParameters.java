/*
 * Copyright 2013, 2014 Megion Research & Development GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mrd.bitillib.model;

import com.mrd.bitlib.util.HexUtils;

import java.io.Serializable;

/**
 * Settings for the network used. Can be either the test or production network.
 */
public class NetworkParameters implements Serializable {
   private static final long serialVersionUID = 1L;

   public static final int PROTOCOL_VERSION = 70016;
   public static final NetworkParameters testNetwork;
   public static final NetworkParameters productionNetwork;
   public static final NetworkParameters regtestNetwork;
   private static final byte[] TESTNET_GENESIS_BLOCK;
   private static final byte[] PRODNET_GENESIS_BLOCK;
   private static final byte[] REGTEST_GENESIS_BLOCK;

   private final int _bip44_coin_type;

   static {
      // get it via RPC:
      // getblockhash 0
      // getblock "<hash>" false
      TESTNET_GENESIS_BLOCK = HexUtils.toBytes("0100000000000000000000000000000000000000000000000000000000000000"
              + "0000000080d30c9b88dd3e46b4d732d2124d7ee77bed0f4587e0b38a20330d2b"
              + "e9ea98e6d78d6460f0ff0f1ebdde000001010000000100000000000000000000"
              + "00000000000000000000000000000000000000000000ffffffff8b04ffff001d"
              + "01044c82d79bd79cd79bd79cd799d7a1d7982033312fd79ed7a8d7a52f323032"
              + "3120d791d7a0d7a720d799d7a9d7a8d790d79c3a20d799d799d7aad79bd79f20"
              + "d7a9d799d794d799d79420d7a6d795d7a8d79a20d79cd794d7a2d79cd795d7aa"
              + "20d79ed7a1d799d79d20d79bd793d79920d79cd7a6d790d7aa20d79ed794d79e"
              + "d7a9d791d7a8ffffffff0100f2052a01000000434104678afdb0fe5548271967"
              + "f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f355"
              + "04e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000");

      PRODNET_GENESIS_BLOCK = HexUtils.toBytes("0100000000000000000000000000000000000000000000000000000000000000"
              + "000000000080d30c9b88dd3e46b4d732d2124d7ee77bed0f4587e0b38a20330d"
              + "2be9ea98e6e88d6460f0ff0f1e0e7f0500010100000001000000000000000000"
              + "0000000000000000000000000000000000000000000000ffffffff8b04ffff00"
              + "1d01044c82d79bd79cd79bd79cd799d7a1d7982033312fd79ed7a8d7a52f3230"
              + "323120d791d7a0d7a720d799d7a9d7a8d790d79c3a20d799d799d7aad79bd79f"
              + "20d7a9d799d794d799d79420d7a6d795d7a8d79a20d79cd794d7a2d79cd795d7"
              + "aa20d79ed7a1d799d79d20d79bd793d79920d79cd7a6d790d7aa20d79ed794d7"
              + "9ed7a9d791d7a8ffffffff0100f2052a01000000434104678afdb0fe55482719"
              + "67f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f3"
              + "5504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000");

      REGTEST_GENESIS_BLOCK = HexUtils.toBytes("01000000000000000000000000000000000000000000000000000000000000000"
              + "000000080d30c9b88dd3e46b4d732d2124d7ee77bed0f4587e0b38a20330d2be9ea98e6588d6460f0ff0f1e5a26010001"
              + "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff8b04ffff001d010"
              + "44c82d79bd79cd79bd79cd799d7a1d7982033312fd79ed7a8d7a52f3230323120d791d7a0d7a720d799d7a9d7a8d790d7"
              + "9c3a20d799d799d7aad79bd79f20d7a9d799d794d799d79420d7a6d795d7a8d79a20d79cd794d7a2d79cd795d7aa20d79"
              + "ed7a1d799d79d20d79bd793d79920d79cd7a6d790d7aa20d79ed794d79ed7a9d791d7a8ffffffff0100f2052a01000000"
              + "434104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec"
              + "112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000");

      testNetwork = new NetworkParameters(NetworkType.TESTNET);
      productionNetwork = new NetworkParameters(NetworkType.PRODNET);
      regtestNetwork = new NetworkParameters(NetworkType.REGTEST);
   }

   /**
    * The first byte of a base58 encoded bitcoinil standard address.
    */
   private int _standardAddressHeader;

   /**
    * The first byte of a base58 encoded bitcoinil multisig address.
    */
   private int _multisigAddressHeader;

   /**
    * The genesis block
    */
   private byte[] _genesisBlock;

   private final int _port;
   private final int _packetMagic;
   private final byte[] _packetMagicBytes;
   private final NetworkType _networkType;

   public enum NetworkType {
      PRODNET, TESTNET, REGTEST
   }

   private NetworkParameters(NetworkType networkType) {
      _networkType = networkType;
      switch (networkType) {
         case PRODNET:
            _standardAddressHeader = 0x00;
            _multisigAddressHeader = 0x84;
            _genesisBlock = PRODNET_GENESIS_BLOCK;
            _port = 8224;
            _packetMagic = 0xf2b0b2d1;
            _packetMagicBytes = new byte[]{(byte) 0xf2, (byte) 0xb0, (byte) 0xb2, (byte) 0xd1};
            _bip44_coin_type = 0;
            break;
         case TESTNET:
            _standardAddressHeader = 0x6F;
            _multisigAddressHeader = 0xC4;
            _genesisBlock = TESTNET_GENESIS_BLOCK;
            _port = 18224;
            _packetMagic = 0x02100201;
            _packetMagicBytes = new byte[]{(byte) 0x02, (byte) 0x10, (byte) 0x02, (byte) 0x01};
            _bip44_coin_type = 1;
            break;
         case REGTEST:
            _standardAddressHeader = 0x6F;
            _multisigAddressHeader = 0xC4;
            _genesisBlock = REGTEST_GENESIS_BLOCK;
            _port = 18444;
            _packetMagic = 0xf2b0b2d1;
            _packetMagicBytes = new byte[]{(byte) 0xf2, (byte) 0xb0, (byte) 0xb2, (byte) 0xd1};
            _bip44_coin_type = 1;
            break;
         default:
            throw new RuntimeException("unknown network " + networkType.toString());
      }
   }

   /**
    * Get the first byte of a base58 encoded bitcoinil address as an integer.
    * 
    * @return The first byte of a base58 encoded bitcoinil address as an integer.
    */
   public int getStandardAddressHeader() {
      return _standardAddressHeader;
   }

   /**
    * Get the first byte of a base58 encoded bitcoinil multisig address as an
    * integer.
    * 
    * @return The first byte of a base58 encoded bitcoinil multisig address as an
    *         integer.
    */
   public int getMultisigAddressHeader() {
      return _multisigAddressHeader;
   }

   public byte[] getGenesisBlock() {
      return _genesisBlock;
   }

   public int getPort() {
      return _port;
   }

   public NetworkType getNetworkType() {
      return _networkType;
   }

   public int getPacketMagic() {
      return _packetMagic;
   }

   public byte[] getPacketMagicBytes() {
      return _packetMagicBytes;
   }

   @Override
   public int hashCode() {
      return _standardAddressHeader;
   };

   public boolean isProdnet() {
      return this.equals(NetworkParameters.productionNetwork);
   }

   public boolean isRegTest() {
      return this.equals(NetworkParameters.regtestNetwork);
   }

   public boolean isTestnet() {
      return this.equals(NetworkParameters.testNetwork);
   }

   // used for Trezor coin_name
   public String getCoinName(){
      return isProdnet() ? "BitcoinIL" : "Testnet";
   }

   @Override
   public boolean equals(Object obj) {
      if (!(obj instanceof NetworkParameters)) {
         return false;
      }
      NetworkParameters other = (NetworkParameters) obj;
      return other._standardAddressHeader == _standardAddressHeader;
   }

   @Override
   public String toString() {
      return isProdnet() ? "prodnet" : (_networkType == NetworkType.REGTEST ? "regtest" : "testnet");
   }

   public int getBip44CoinType() {
      return _bip44_coin_type;
   }
}
