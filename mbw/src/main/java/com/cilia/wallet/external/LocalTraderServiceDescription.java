package com.cilia.wallet.external;

import android.app.Activity;
import android.widget.Toast;

import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.Utils;
import com.cilia.wallet.activity.modern.Toaster;
import com.cilia.wallet.lt.LocalTraderManager;
import com.cilia.wallet.lt.activity.LtMainActivity;
import com.mycelium.wapi.wallet.Address;
import com.mycelium.wapi.wallet.eth.coins.EthMain;
import com.mycelium.wapi.wallet.eth.coins.EthTest;

public class LocalTraderServiceDescription extends BuySellServiceDescriptor {

   public LocalTraderServiceDescription() {
      super(R.string.lt_mycelium_marketplace, R.string.lt_mycelium_marketplace_description, 0, R.drawable.mycelium_logo_transp);
   }

   @Override
   public void launchService(Activity activity, MbwManager mbwManager, Address activeReceivingAddress) {
      if (!mbwManager.getSelectedAccount().canSpend()) {
         new Toaster(activity).toast(R.string.lt_warning_watch_only_account, false);
         return;
      }
      if (!Utils.isAllowedForLocalTrader(mbwManager.getSelectedAccount())) {
         new Toaster(activity).toast(R.string.lt_warning_wrong_account_type, false);
         return;
      }

      LocalTraderManager ltManager = mbwManager.getLocalTraderManager();
      boolean newActivity = ltManager.hasLocalTraderAccount() && ltManager.needsTraderSynchronization();
      LtMainActivity.callMe(activity, newActivity ? LtMainActivity.TAB_TYPE.ACTIVE_TRADES
              : LtMainActivity.TAB_TYPE.DEFAULT);
   }

   @Override
   public boolean showEnableInSettings() {
      // The LT settings are handled in their own category
      return false;
   }

   @Override
   public boolean isEnabled(MbwManager mbwManager) {
      return mbwManager.getLocalTraderManager().isLocalTraderEnabled()
              && mbwManager.getSelectedAccount().getCoinType() != EthMain.INSTANCE
              && mbwManager.getSelectedAccount().getCoinType() != EthTest.INSTANCE;
   }

   @Override
   public void setEnabled(MbwManager mbwManager, boolean enabledState) {
      mbwManager.getLocalTraderManager().setLocalTraderEnabled(enabledState);
   }
}
