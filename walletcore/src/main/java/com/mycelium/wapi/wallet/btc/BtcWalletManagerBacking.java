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

package com.mycelium.wapi.wallet.btc;

import com.mycelium.wapi.wallet.SecureKeyValueStoreBacking;
import com.mycelium.wapi.wallet.SingleAddressBtcAccountBacking;
import com.mycelium.wapi.wallet.SingleAddressBtcILAccountBacking;
import com.mycelium.wapi.wallet.WalletBacking;
import com.mycelium.wapi.wallet.btc.bip44.HDAccountContext;
import com.mycelium.wapi.wallet.btc.single.SingleAddressAccountContext;
import com.mycelium.wapi.wallet.btcil.Bip44BtcILAccountBacking;
import com.mycelium.wapi.wallet.btcil.bip44.HDAccountContextIL;
import com.mycelium.wapi.wallet.btcil.single.SingleAddressAccountContextIL;

import java.util.List;
import java.util.UUID;

public interface BtcWalletManagerBacking<AccountContext>
        extends WalletBacking<AccountContext>, SecureKeyValueStoreBacking {
    void beginTransaction();

    void setTransactionSuccessful();

    void endTransaction();

    void createBip44AccountContext(HDAccountContext context);
    void createBip44AccountILContext(HDAccountContextIL context);

    List<HDAccountContext> loadBip44AccountContexts();
    List<HDAccountContextIL> loadBip44AccountILContexts();

    Bip44BtcAccountBacking getBip44AccountBacking(UUID accountId);
    Bip44BtcILAccountBacking getBip44AccountILBacking(UUID accountId);

    void deleteBip44AccountContext(UUID accountId);

    void createSingleAddressAccountContext(SingleAddressAccountContext context);
    void createSingleAddressAccountContextIL(SingleAddressAccountContextIL context);

    List<SingleAddressAccountContext> loadSingleAddressAccountContexts();
    List<SingleAddressAccountContextIL> loadSingleAddressAccountILContexts();

    SingleAddressBtcAccountBacking getSingleAddressAccountBacking(UUID accountId);
    SingleAddressBtcILAccountBacking getSingleAddressAccountILBacking(UUID accountId);

    void deleteSingleAddressAccountContext(UUID accountId);
}
