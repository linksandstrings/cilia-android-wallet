package com.mycelium.wapi.wallet.coins;


public class Balance {
    /**
     * The sum of the unspent outputs which are confirmed and currently not spent
     * in pending transactions.
     */
    public final Value confirmed;

    /**
     * The sum of the outputs which are being received as part of pending
     * transactions from foreign addresses.
     */
    public final Value pendingReceiving;

    /**
     * The sum of outputs currently being sent from the address set.
     */
    public final Value pendingSending;

    /**
     * Get the value that is sending to foreign addresses
     * Used in UI to display 'Sending ..."
     */
    public Value getSendingToForeignAddresses() {
        return pendingSending.minus(pendingChange);
    }

    /**
     * The sum of the outputs being sent from the address set to itself
     */
    public final Value pendingChange;

    public Balance(Value confirmed, Value pendingReceiving, Value pendingSending, Value pendingChange) {
        this.confirmed = confirmed;
        this.pendingReceiving = pendingReceiving;
        this.pendingSending = pendingSending;
        this.pendingChange = pendingChange;
    }

    public Value getSpendable() {
        return confirmed.plus(pendingReceiving).plus(pendingChange);
    }

    public static Balance getZeroBalance(AssetInfo assetInfo) {
        return new Balance(Value.zeroValue(assetInfo), Value.zeroValue(assetInfo),
                Value.zeroValue(assetInfo), Value.zeroValue(assetInfo));
    }
}
