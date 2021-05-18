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
package com.mrd.bitillib.crypto;

import com.mrd.bitillib.model.BitcoinILAddress;
import com.mrd.bitillib.model.NetworkParameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PublicKeyRing implements IPublicKeyRing {
    private List<BitcoinILAddress> _addresses;
    private Set<BitcoinILAddress> _addressSet;
    private Map<BitcoinILAddress, PublicKey> _publicKeys;

    public PublicKeyRing() {
        _addresses = new ArrayList<>();
        _addressSet = new HashSet<>();
        _publicKeys = new HashMap<>();
    }

    /**
     * Add a public key to the key ring.
     */
    public void addPublicKey(PublicKey key, NetworkParameters network) {
        Collection<BitcoinILAddress> addresses = key.getAllSupportedAddresses(network).values();
        _addresses.addAll(addresses);
        _addressSet.addAll(addresses);
        for (BitcoinILAddress address : addresses) {
            _publicKeys.put(address, key);
        }
    }

    /**
     * Add a public key and its corresponding BitcoinIL address to the key ring.
     */
    public void addPublicKey(PublicKey key, BitcoinILAddress address) {
        _addresses.add(address);
        _addressSet.add(address);
        _publicKeys.put(address, key);
    }

    @Override
    public PublicKey findPublicKeyByAddress(BitcoinILAddress address) {
        return _publicKeys.get(address);
    }

    public List<BitcoinILAddress> getAddresses() {
        return Collections.unmodifiableList(_addresses);
    }

    public Set<BitcoinILAddress> getAddressSet() {
        return Collections.unmodifiableSet(_addressSet);
    }
}