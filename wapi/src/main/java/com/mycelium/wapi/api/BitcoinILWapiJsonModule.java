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

package com.mycelium.wapi.api;

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
import com.megiontechnologies.BitcoinILs;
import com.mrd.bitillib.crypto.PublicKey;
import com.mrd.bitillib.model.BitcoinILAddress;
import com.mrd.bitillib.model.BitcoinILOutPoint;
import com.mrd.bitillib.util.BitcoinILSha256Hash;
import com.mrd.bitlib.util.HexUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for serializing and deserializing complex types where we cannot
 * add Jackson annotations
 */
public class BitcoinILWapiJsonModule extends SimpleModule {
   private static final long serialVersionUID = 1L;
   private static Map<Class<?>, JsonDeserializer<?>> DESERIALIZERS;
   private static List<JsonSerializer<?>> SERIALIZERS;

   static {
      DESERIALIZERS = new HashMap<Class<?>, JsonDeserializer<?>>();
      DESERIALIZERS.put(BitcoinILs.class, new BitcoinDeserializer());
      DESERIALIZERS.put(BitcoinILAddress.class, new AddressDeserializer());
      DESERIALIZERS.put(PublicKey.class, new PublicKeyDeserializer());
      DESERIALIZERS.put(BitcoinILSha256Hash.class, new Sha256HashDeserializer());
      DESERIALIZERS.put(BitcoinILOutPoint.class, new OutPointDeserializer());

      SERIALIZERS = new ArrayList<JsonSerializer<?>>();
      SERIALIZERS.add(new BitcoinSerializer());
      SERIALIZERS.add(new AddressSerializer());
      SERIALIZERS.add(new PublicKeySerializer());
      SERIALIZERS.add(new Sha256HashSerializer());
      SERIALIZERS.add(new OutPointSerializer());
   }

   private static class BitcoinDeserializer extends JsonDeserializer<BitcoinILs> {
      @Override
      public BitcoinILs deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
            JsonProcessingException {
         ObjectCodec oc = jp.getCodec();
         JsonNode node = oc.readTree(jp);
         BitcoinILs bitcoins = BitcoinILs.valueOf(node.asLong());
         if (bitcoins == null) {
            throw new JsonParseException("Failed to convert string '" + node.asText() + "' into an bitcoin",
                  JsonLocation.NA);
         }
         return bitcoins;
      }
   }

   private static class AddressDeserializer extends JsonDeserializer<BitcoinILAddress> {

      @Override
      public BitcoinILAddress deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
            JsonProcessingException {
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

   private static class BitcoinSerializer extends JsonSerializer<BitcoinILs> {

      @Override
      public void serialize(BitcoinILs value, JsonGenerator jgen, SerializerProvider provider) throws IOException,
            JsonProcessingException {
         jgen.writeString(Long.toString(value.getLongValue()));
      }

      @Override
      public Class<BitcoinILs> handledType() {
         return BitcoinILs.class;
      }

   }

   private static class AddressSerializer extends JsonSerializer<BitcoinILAddress> {

      @Override
      public void serialize(BitcoinILAddress value, JsonGenerator jgen, SerializerProvider provider) throws IOException,
            JsonProcessingException {
         jgen.writeString(value.toString());
      }

      @Override
      public Class<BitcoinILAddress> handledType() {
         return BitcoinILAddress.class;
      }

   }

   private static class PublicKeyDeserializer extends JsonDeserializer<PublicKey> {

      @Override
      public PublicKey deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
            JsonProcessingException {
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
      public void serialize(PublicKey value, JsonGenerator jgen, SerializerProvider provider) throws IOException,
            JsonProcessingException {
         jgen.writeString(HexUtils.toHex(value.getPublicKeyBytes()));
      }

      @Override
      public Class<PublicKey> handledType() {
         return PublicKey.class;
      }

   }

   private static class Sha256HashDeserializer extends JsonDeserializer<BitcoinILSha256Hash> {

      @Override
      public BitcoinILSha256Hash deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
            JsonProcessingException {
         ObjectCodec oc = jp.getCodec();
         JsonNode node = oc.readTree(jp);
         BitcoinILSha256Hash hash = BitcoinILSha256Hash.fromString(node.asText());
         if (hash == null) {
            throw new JsonParseException("Failed to convert string '" + node.asText() + "' into a Sha256Hash instance",
                  JsonLocation.NA);
         }
         return hash;
      }

   }

   private static class Sha256HashSerializer extends JsonSerializer<BitcoinILSha256Hash> {

      @Override
      public void serialize(BitcoinILSha256Hash value, JsonGenerator jgen, SerializerProvider provider) throws IOException,
            JsonProcessingException {
         jgen.writeString(value.toString());
      }

      @Override
      public Class<BitcoinILSha256Hash> handledType() {
         return BitcoinILSha256Hash.class;
      }

   }

   private static class OutPointDeserializer extends JsonDeserializer<BitcoinILOutPoint> {

      @Override
      public BitcoinILOutPoint deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
            JsonProcessingException {
         ObjectCodec oc = jp.getCodec();
         JsonNode node = oc.readTree(jp);
         BitcoinILOutPoint outPoint = BitcoinILOutPoint.fromString(node.asText());
         if (outPoint == null) {
            throw new JsonParseException("Failed to convert string '" + node.asText() + "' into an OutPoint instance",
                  JsonLocation.NA);
         }
         return outPoint;
      }

   }

   private static class OutPointSerializer extends JsonSerializer<BitcoinILOutPoint> {

      @Override
      public void serialize(BitcoinILOutPoint value, JsonGenerator jgen, SerializerProvider provider) throws IOException,
            JsonProcessingException {
         jgen.writeString(value.toString());
      }

      @Override
      public Class<BitcoinILOutPoint> handledType() {
         return BitcoinILOutPoint.class;
      }

   }

   public BitcoinILWapiJsonModule() {
      super("Wapi Json module", Version.unknownVersion(), DESERIALIZERS, SERIALIZERS);
   }
}
