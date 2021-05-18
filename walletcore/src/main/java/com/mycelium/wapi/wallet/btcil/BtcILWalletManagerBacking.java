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

package com.mycelium.wapi.wallet.btcil;

import com.mycelium.wapi.wallet.SecureKeyValueStoreBacking;
import com.mycelium.wapi.wallet.SingleAddressBtcILAccountBacking;
import com.mycelium.wapi.wallet.WalletBacking;
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL;
import com.mycelium.wapi.wallet.btcil.single.SingleAddressAccountContextIL;

import java.util.List;
import java.util.UUID;

public interface BtcILWalletManagerBacking<AccountContext>
        extends WalletBacking<AccountContext>, SecureKeyValueStoreBacking {
    void beginTransaction();

    void setTransactionSuccessful();

    void endTransaction();

    void createBip44AccountContext(HDAccountContextIL context);

    List<HDAccountContextIL> loadBip44AccountContexts();

    Bip44BtcILAccountBacking getBip44AccountBacking(UUID accountId);

    void deleteBip44AccountContext(UUID accountId);

    void createSingleAddressAccountContext(SingleAddressAccountContextIL context);

    List<SingleAddressAccountContextIL> loadSingleAddressAccountContexts();

    SingleAddressBtcILAccountBacking getSingleAddressAccountBacking(UUID accountId);

    void deleteSingleAddressAccountContext(UUID accountId);
}
