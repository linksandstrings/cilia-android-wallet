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

package com.mrd.bitillib.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mrd.bitillib.model.BitcoinILAddress;
import com.mrd.bitlib.model.hdpath.HdKeyPath;
import com.mrd.bitillib.crypto.PublicKey;
import com.mrd.bitlib.util.HexUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BitlibJsonModule extends SimpleModule {
   private static final long serialVersionUID = 1L;
   private static final Map<Class<?>, JsonDeserializer<?>> DESERIALIZERS;
   private static final List<JsonSerializer<?>> SERIALIZERS;

   static {
      DESERIALIZERS = new HashMap<Class<?>, JsonDeserializer<?>>();
      DESERIALIZERS.put(BitcoinILAddress.class, new AddressDeserializer());
      DESERIALIZERS.put(PublicKey.class, new PublicKeyDeserializer());
      DESERIALIZERS.put(HdKeyPath.class, new HdKeyPathDeserializer());
      DESERIALIZERS.put(HdKeyPath.class, new BitcoinILSha256HashDeserializer());

      SERIALIZERS = new ArrayList<JsonSerializer<?>>();
      SERIALIZERS.add(new AddressSerializer());
      SERIALIZERS.add(new PublicKeySerializer());
      SERIALIZERS.add(new HdKeyPathSerializer());
      SERIALIZERS.add(new BitcoinILSha256HashSerializer());
   }

   private static class BitcoinILSha256HashDeserializer extends JsonDeserializer<BitcoinILSha256Hash> {

      @Override
      public BitcoinILSha256Hash deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
         ObjectCodec oc = jp.getCodec();
         JsonNode node = oc.readTree(jp);
         BitcoinILSha256Hash hash = BitcoinILSha256Hash.fromString(node.asText());
         if (hash == null) {
            throw new JsonParseException("Failed to convert string '" + node.asText() + "' into a Sha256 hash",
                  JsonLocation.NA);
         }
         return hash;
      }

   }

   private static class BitcoinILSha256HashSerializer extends JsonSerializer<BitcoinILSha256Hash> {

      @Override
      public void serialize(BitcoinILSha256Hash value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
         jgen.writeString(value.toString());
      }

      @Override
      public Class<BitcoinILSha256Hash> handledType() {
         return BitcoinILSha256Hash.class;
      }

   }

   private static class AddressDeserializer extends JsonDeserializer<BitcoinILAddress> {

      @Override
      public BitcoinILAddress deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
         ObjectCodec oc = jp.getCodec();
         JsonNode node = oc.readTree(jp);
         BitcoinILAddress address = BitcoinILAddress.fromString(node.asText());
         if (address == null) {
            throw new JsonParseException("Failed to convert string '" + node.asText() + "' into an address",
                  JsonLocation.NA);
         }
         return address;
      }

   }

   private static class AddressSerializer extends JsonSerializer<BitcoinILAddress> {

      @Override
      public void serialize(BitcoinILAddress value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
         jgen.writeString(value.toString());
      }

      @Override
      public Class<BitcoinILAddress> handledType() {
         return BitcoinILAddress.class;
      }

   }

   private static class PublicKeyDeserializer extends JsonDeserializer<PublicKey> {

      @Override
      public PublicKey deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
         ObjectCodec oc = jp.getCodec();
         JsonNode node = oc.readTree(jp);
         byte[] pubKeyBytes;
         try {
            pubKeyBytes = HexUtils.toBytes(node.asText());
         } catch (RuntimeException e) {
            throw new JsonParseException("Failed to convert string '" + node.asText() + "' into an public key bytes",
                  JsonLocation.NA);
         }
         return new PublicKey(pubKeyBytes);
      }

   }

   private static class PublicKeySerializer extends JsonSerializer<PublicKey> {

      @Override
      public void serialize(PublicKey value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
         jgen.writeString(HexUtils.toHex(value.getPublicKeyBytes()));
      }

      @Override
      public Class<PublicKey> handledType() {
         return PublicKey.class;
      }

   }

   private static class HdKeyPathDeserializer extends JsonDeserializer<HdKeyPath> {
      @Override
      public HdKeyPath deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
         return HdKeyPath.valueOf(jp.getValueAsString());
      }
   }

   private static class HdKeyPathSerializer extends JsonSerializer<HdKeyPath> {
      @Override
      public void serialize(HdKeyPath value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
         jgen.writeString(value.toString());
      }

      @Override
      public Class<HdKeyPath> handledType() {
         return HdKeyPath.class;
      }
   }


   public BitlibJsonModule() {
      super("BitLibJsonModule", Version.unknownVersion(), DESERIALIZERS, SERIALIZERS);
   }

}
