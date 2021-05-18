/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.cilia.wallet.activity.util;

import android.content.Context;
import androidx.appcompat.widget.PopupMenu;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.event.ExchangeRatesRefreshed;
import com.cilia.wallet.event.SelectedCurrencyChanged;
import com.mycelium.wapi.model.ExchangeRate;
import com.mycelium.wapi.wallet.coins.AssetInfo;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ToggleableCurrencyButton extends ToggleableCurrencyDisplay {
   public ToggleableCurrencyButton(Context context, AttributeSet attrs) {
      super(context, attrs);
   }

   public ToggleableCurrencyButton(Context context, AttributeSet attrs, int defStyle) {
      super(context, attrs, defStyle);
   }

   public ToggleableCurrencyButton(Context context) {
      super(context);
   }

   @Override
   protected void updateUi() {
      super.updateUi();

      final List<AssetInfo> currencies = getAvailableCurrenciesList();
      // there are more than one fiat-currency
      // there is only one currency to show - don't show a triangle hinting that the user can toggle
      findViewById(R.id.ivSwitchable).setVisibility(currencies.size() > 1 ? VISIBLE : INVISIBLE);

      LinearLayout linearLayout = findViewById(R.id.llContainer);
      final PopupMenu menu = new PopupMenu(getContext(), linearLayout);
      linearLayout.setOnClickListener(new OnClickListener() {
         @Override
         public void onClick(View v) {
            menu.show();
         }
      });

      if (currencies.size() > 1) {
         final Map<MenuItem, AssetInfo> itemMap = new HashMap<>();
         for (int i = 0; i < currencies.size(); i++) {
            String currency = currencies.get(i).getSymbol();
            MenuItem item = menu.getMenu().add(currency);
            itemMap.put(item, currencies.get(i));
         }

         menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
               if (getCoinType() == null) { // then it's a total TCB
                  getCurrencySwitcher().setCurrentTotalCurrency(itemMap.get(item));
               } else { // else it's a currency group TCB
                  getCurrencySwitcher().setCurrency(getCoinType(), itemMap.get(item));
               }
               if (MbwManager.getEventBus() != null) {
                  // update UI via event bus, also inform other parts of the app about the change
                  MbwManager.getEventBus().post(new SelectedCurrencyChanged());
               } else {
                  updateUi();
               }
               return true;
            }
         });
      }
      setVisibility(currencies.size() == 0 ? View.INVISIBLE : View.VISIBLE);
   }

   private List<AssetInfo> getAvailableCurrenciesList() {
      List<AssetInfo> result = new ArrayList<>();
      MbwManager mbwManager = MbwManager.getInstance(getContext());

      if (!getFiatOnly()) {
         result.add(getCoinType());
      }

      // add such fiat currencies for which exchange rate is available
      AssetInfo baseCurrency = getFiatOnly() ?
              mbwManager.getSelectedAccount().getCoinType() : getCoinType();
      for (AssetInfo asset : getCurrencySwitcher().getCurrencyList()) {
         ExchangeRate exchangeRate = mbwManager.getExchangeRateManager()
                 .getExchangeRate(baseCurrency.getSymbol(), asset.getSymbol());
         if (exchangeRate != null && exchangeRate.price != null) {
            result.add(asset);
         }
      }
      return result;
   }

   @Subscribe
   @Override
   public void onExchangeRateChange(ExchangeRatesRefreshed event){
      updateUi();
   }

   @Subscribe
   @Override
   public void onSelectedCurrencyChange(SelectedCurrencyChanged event){
      updateUi();
   }
}