package com.cilia.wallet.external.changelly.bch;


import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.megiontechnologies.BitcoinCash;
import com.cilia.wallet.MbwManager;
import com.cilia.wallet.R;
import com.cilia.wallet.Utils;
import com.cilia.wallet.activity.modern.Toaster;
import com.cilia.wallet.activity.send.event.SelectListener;
import com.cilia.wallet.activity.send.view.SelectableRecyclerView;
import com.cilia.wallet.activity.util.ValueExtensionsKt;
import com.cilia.wallet.activity.view.ValueKeyboard;
import com.cilia.wallet.event.ExchangeRatesRefreshed;
import com.cilia.wallet.external.changelly.AccountAdapter;
import com.cilia.wallet.external.changelly.ChangellyAPIService;
import com.cilia.wallet.external.changelly.ChangellyAPIService.ChangellyAnswerDouble;
import com.cilia.wallet.external.changelly.Constants;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.coins.Value;
import com.squareup.otto.Subscribe;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static butterknife.OnTextChanged.Callback.AFTER_TEXT_CHANGED;
import static com.cilia.wallet.activity.util.WalletManagerExtensionsKt.getActiveBTCSingleAddressAccounts;
import static com.cilia.wallet.external.changelly.Constants.ABOUT;
import static com.cilia.wallet.external.changelly.Constants.decimalFormat;
import static com.mycelium.wapi.wallet.bch.bip44.Bip44BCHHDModuleKt.getBCHBip44Accounts;
import static com.mycelium.wapi.wallet.bch.single.BitcoinCashSingleAddressModuleKt.getBCHSingleAddressAccounts;
import static com.mycelium.wapi.wallet.btc.bip44.BitcoinHDModuleKt.getActiveHDAccounts;
import static com.mycelium.wapi.wallet.btcil.bip44.BitcoinILHDModuleKt.getActiveHDAccountsIL;
import static com.mycelium.wapi.wallet.currency.CurrencyValue.BCH;
import static com.mycelium.wapi.wallet.currency.CurrencyValue.BTC;

public class ExchangeFragment extends Fragment {
    public static final BigDecimal MAX_BITCOIN_VALUE = BigDecimal.valueOf(20999999);
    public static final String BCH_EXCHANGE = "bch_exchange";
    public static final String BCH_EXCHANGE_TRANSACTIONS = "bch_exchange_transactions";
    public static final String BCH_MIN_EXCHANGE_VALUE = "bch_min_exchange_value";
    public static final float NOT_LOADED = -1f;
    public static final String TO_ACCOUNT = "toAccount";
    public static final String FROM_ACCOUNT = "fromAccount";
    public static final String FROM_VALUE = "fromValue";
    private static final String TAG = "ChangellyActivity";
    private ChangellyAPIService changellyAPIService = ChangellyAPIService.retrofit.create(ChangellyAPIService.class);

    @BindView(R.id.scrollView)
    ScrollView scrollView;

    @BindView(R.id.from_account_list)
    SelectableRecyclerView fromRecyclerView;

    @BindView(R.id.to_account_list)
    SelectableRecyclerView toRecyclerView;

    @BindView(R.id.numeric_keyboard)
    ValueKeyboard valueKeyboard;

    @BindView(R.id.fromValue)
    TextView fromValue;

    @BindView(R.id.toValue)
    TextView toValue;

    @BindView(R.id.toValueLayout)
    View toLayout;

    @BindView(R.id.tvErrorFrom)
    TextView tvErrorFrom;

    @BindView(R.id.tvErrorTo)
    TextView tvErrorTo;

    @BindView(R.id.buttonContinue)
    Button buttonContinue;

    @BindView(R.id.exchange_rate)
    TextView exchangeRate;

    @BindView(R.id.exchange_fiat_rate)
    TextView exchangeFiatRate;

    @BindView(R.id.use_all_funds)
    View useAllFunds;

    private MbwManager mbwManager;
    private AccountAdapter toAccountAdapter;
    private AccountAdapter fromAccountAdapter;

    private double minAmount = NOT_LOADED;
    private boolean avoidTextChangeEvent = false;
    private SharedPreferences sharedPreferences;

    private double bchToBtcRate = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mbwManager = MbwManager.getInstance(getActivity());
        setRetainInstance(true);
        sharedPreferences = getActivity().getSharedPreferences(BCH_EXCHANGE, Context.MODE_PRIVATE);
        minAmount = (double) sharedPreferences.getFloat(BCH_MIN_EXCHANGE_VALUE, NOT_LOADED);
        changellyAPIService.getMinAmount(BCH, BTC).enqueue(new GetMinCallback());
        requestExchangeRate("1");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_exchage, container, false);
        ButterKnife.bind(this, view);
        int senderFinalWidth = getActivity().getWindowManager().getDefaultDisplay().getWidth();
        int firstItemWidth = (senderFinalWidth - getResources().getDimensionPixelSize(R.dimen.item_dob_width)) / 2;

        WalletManager walletManager = mbwManager.getWalletManager(false);
        List<WalletAccount<?>> toAccounts = new ArrayList<>();
        toAccounts.addAll(getActiveHDAccountsIL(walletManager));
        toAccounts.addAll(getActiveHDAccounts(walletManager));
        toAccounts.addAll(getActiveBTCSingleAddressAccounts(walletManager));
        toAccountAdapter = new AccountAdapter(mbwManager, toAccounts, firstItemWidth);
        toAccountAdapter.setAccountUseType(AccountAdapter.AccountUseType.IN);
        toRecyclerView.setAdapter(toAccountAdapter);
        View toHeader = inflater.inflate(AccountAdapter.AccountUseType.IN.paddingLayout, toRecyclerView, false);
        toHeader.setBackground(null);
        toRecyclerView.setHeader(toHeader);
        toRecyclerView.setFooter(toHeader);

        List<WalletAccount<?>> fromAccounts = new ArrayList<>();
        for (WalletAccount walletAccount : getBCHBip44Accounts(walletManager)) {
            if (walletAccount.canSpend() && !walletAccount.getAccountBalance().confirmed.isZero()) {
                fromAccounts.add(walletAccount);
            }
        }

        for (WalletAccount walletAccount : getBCHSingleAddressAccounts(walletManager)) {
            if (walletAccount.canSpend() && !walletAccount.getAccountBalance().confirmed.isZero()) {
                fromAccounts.add(walletAccount);
            }
        }

        if (fromAccounts.isEmpty()) {
            toast(getString(R.string.no_spendable_accounts));
            getActivity().finish();
        }
        fromAccountAdapter = new AccountAdapter(mbwManager, fromAccounts, firstItemWidth);
        fromAccountAdapter.setAccountUseType(AccountAdapter.AccountUseType.OUT);
        fromRecyclerView.setAdapter(fromAccountAdapter);
        fromRecyclerView.setSelectedItem(mbwManager.getSelectedAccount());
        fromRecyclerView.setSelectListener(new SelectListener() {
            @Override
            public void onSelect(RecyclerView.Adapter adapter, int position) {
                WalletAccount fromAccount = fromAccountAdapter.getItem(fromRecyclerView.getSelectedItem()).account;
                valueKeyboard.setSpendableValue(getMaxSpend(fromAccount));
                isValueForOfferOk(true);
            }
        });
        View fromHeader = inflater.inflate(AccountAdapter.AccountUseType.OUT.paddingLayout, fromRecyclerView, false);
        fromHeader.setBackground(null);
        fromRecyclerView.setHeader(fromHeader);
        fromRecyclerView.setFooter(fromHeader);

        valueKeyboard.setMaxDecimals(8);
        valueKeyboard.setInputListener(new ValueKeyboard.SimpleInputListener() {
            @Override
            public void done() {
                stopCursor(fromValue);
                stopCursor(toValue);
                useAllFunds.setVisibility(View.VISIBLE);
                fromValue.setHint(R.string.zero);
                toValue.setHint(R.string.zero);
                isValueForOfferOk(true);
            }
        });
        valueKeyboard.setMaxText(getString(R.string.use_all_funds), 14);
        valueKeyboard.setPasteVisibility(View.GONE);

        valueKeyboard.setVisibility(View.GONE);
        buttonContinue.setEnabled(false);
        return view;
    }

    private void startCursor(final TextView textView) {
        textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.input_cursor, 0);
        textView.post(new Runnable() {
            @Override
            public void run() {
                AnimationDrawable animationDrawable = (AnimationDrawable) textView.getCompoundDrawables()[2];
                if (!animationDrawable.isRunning()) {
                    animationDrawable.start();
                }
            }
        });
    }

    private void stopCursor(final TextView textView) {
        textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        MbwManager.getEventBus().register(this);
    }

    @Override
    public void onPause() {
        MbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    @OnClick(R.id.buttonContinue)
    void continueClick() {
        String txtAmount = fromValue.getText().toString();
        double dblAmount;
        try {
            dblAmount = Double.parseDouble(txtAmount);
        } catch (NumberFormatException e) {
            toast("Error exchanging value");
            buttonContinue.setEnabled(false);
            return;
        }
        Fragment fragment = new ConfirmExchangeFragment();
        Bundle bundle = new Bundle();
        bundle.putDouble(Constants.FROM_AMOUNT, dblAmount);
        WalletAccount toAccount = toAccountAdapter.getItem(toRecyclerView.getSelectedItem()).account;
        bundle.putSerializable(Constants.DESTADDRESS, toAccount.getId());
        WalletAccount fromAccount = fromAccountAdapter.getItem(fromRecyclerView.getSelectedItem()).account;
        bundle.putSerializable(Constants.FROM_ADDRESS, fromAccount.getId());
        bundle.putString(Constants.TO_AMOUNT, toValue.getText().toString());

        fragment.setArguments(bundle);
        getFragmentManager().beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, fragment, "ConfirmExchangeFragment")
                .addToBackStack("ConfirmExchangeFragment")
                .commitAllowingStateLoss();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(FROM_VALUE, fromValue.getText().toString());
        outState.putSerializable(FROM_ACCOUNT, fromAccountAdapter.getItem(fromRecyclerView.getSelectedItem()).account.getId());
        outState.putSerializable(TO_ACCOUNT, toAccountAdapter.getItem(toRecyclerView.getSelectedItem()).account.getId());
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            fromValue.setText(savedInstanceState.getString(FROM_VALUE));
            fromRecyclerView.setSelectedItem(mbwManager.getWalletManager(false)
                    .getAccount((UUID) savedInstanceState.getSerializable(FROM_ACCOUNT)));
            toRecyclerView.setSelectedItem(mbwManager.getWalletManager(false)
                    .getAccount((UUID) savedInstanceState.getSerializable(TO_ACCOUNT)));
            requestExchangeRate(getFromExcludeFee().toPlainString());
        }
    }

    @OnClick(R.id.toValueLayout)
    void toValueClick() {
        valueKeyboard.setInputTextView(toValue);
        valueKeyboard.setVisibility(View.VISIBLE);
        useAllFunds.setVisibility(View.GONE);
        valueKeyboard.setEntry(toValue.getText().toString());
        toValue.setHint("");
        fromValue.setHint(R.string.zero);
        startCursor(toValue);
        stopCursor(fromValue);
        valueKeyboard.setSpendableValue(BigDecimal.ZERO);
        valueKeyboard.setMaxValue(MAX_BITCOIN_VALUE);
        isValueForOfferOk(true);
        scrollTo(toLayout.getBottom());
    }

    private void scrollTo(final int to) {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.smoothScrollTo(0, to);
            }
        });
    }

    @OnClick(R.id.fromValueLayout)
    void fromValueClick() {
        valueKeyboard.setInputTextView(fromValue);
        valueKeyboard.setVisibility(View.VISIBLE);
        useAllFunds.setVisibility(View.GONE);
        valueKeyboard.setEntry(fromValue.getText().toString());
        startCursor(fromValue);
        stopCursor(toValue);
        fromValue.setHint("");
        toValue.setHint(R.string.zero);
        AccountAdapter.Item item = fromAccountAdapter.getItem(fromRecyclerView.getSelectedItem());
        valueKeyboard.setSpendableValue(getMaxSpend(item.account));
        valueKeyboard.setMaxValue(MAX_BITCOIN_VALUE);
        isValueForOfferOk(true);
    }

    @OnClick(R.id.use_all_funds)
    void useAllFundsClick() {
        AccountAdapter.Item item = fromAccountAdapter.getItem(fromRecyclerView.getSelectedItem());
        fromValue.setText(getMaxSpend(item.account).stripTrailingZeros().toPlainString());
    }

    //TODO call getMaxFundsTransferrable need refactoring, we should call account object
    private BigDecimal getMaxSpend(WalletAccount account) {
        return BigDecimal.valueOf(0);
    }


    @OnTextChanged(value = R.id.fromValue, callback = AFTER_TEXT_CHANGED)
    public void afterEditTextInputFrom(Editable editable) {
        isValueForOfferOk(true);
        if (!avoidTextChangeEvent && !fromValue.getText().toString().isEmpty()) {
            try {
                requestExchangeRate(getFromExcludeFee().toPlainString());
            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        if (!avoidTextChangeEvent && fromValue.getText().toString().isEmpty()) {
            avoidTextChangeEvent = true;
            toValue.setText(null);
            avoidTextChangeEvent = false;
        }
        resizeTextView(fromValue);
        updateUi();
    }

    private void resizeTextView(TextView textView) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP
                , textView.getText().toString().length() < 11 ? 36 : 22);
    }

    private BigDecimal getFromExcludeFee() {
        BigDecimal val = new BigDecimal(fromValue.getText().toString());
        if (val.compareTo(MAX_BITCOIN_VALUE) > 0) {
            val = MAX_BITCOIN_VALUE;
            fromValue.setText(val.toPlainString());
        }
        BigDecimal txFee = UtilsKt.estimateFeeFromTransferrableAmount(
                fromAccountAdapter.getItem(fromRecyclerView.getSelectedItem()).account,
                mbwManager, BitcoinCash.nearestValue(val).getLongValue());
        return val.add(txFee.negate());
    }

    @OnTextChanged(value = R.id.toValue, callback = AFTER_TEXT_CHANGED)
    public void afterEditTextInputTo(Editable editable) {
        if (!avoidTextChangeEvent && !toValue.getText().toString().isEmpty()) {
            BigDecimal val = new BigDecimal(toValue.getText().toString());
            if (val.compareTo(MAX_BITCOIN_VALUE) > 0) {
                val = MAX_BITCOIN_VALUE;
                toValue.setText(val.toPlainString());
            }
            avoidTextChangeEvent = true;
            fromValue.setText(decimalFormat.format(calculateBTCtoBHC(val.toPlainString())));
            avoidTextChangeEvent = false;
        }
        if (!avoidTextChangeEvent && toValue.getText().toString().isEmpty()) {
            avoidTextChangeEvent = true;
            fromValue.setText(null);
            avoidTextChangeEvent = false;
        }
        resizeTextView(toValue);
        updateUi();
    }

    private void requestExchangeRate(String amount) {
        double dblAmount;
        try {
            dblAmount = Double.parseDouble(amount);
        } catch (NumberFormatException e) {
            new Toaster(getActivity()).toast("Error parsing double values", true);
            return;
        }
        changellyAPIService.getExchangeAmount(BCH, BTC, dblAmount).enqueue(new GetOfferCallback(dblAmount));
    }

    private double calculateBTCtoBHC(String amount) {
        double dblAmount;
        try {
            dblAmount = Double.parseDouble(amount);
        } catch (NumberFormatException e) {
            new Toaster(getActivity()).toast("Error parsing double values", true);
            return 0;
        }
        if (bchToBtcRate == 0) {
            new Toaster(getActivity()).toast("Please wait while loading exchange rate", true);
            return 0;
        }
        return dblAmount / bchToBtcRate;
    }

    boolean isValueForOfferOk(boolean checkMin) {
        tvErrorFrom.setVisibility(View.INVISIBLE);
        tvErrorTo.setVisibility(View.GONE);
        exchangeFiatRate.setVisibility(View.VISIBLE);
        String txtAmount = fromValue.getText().toString();
        if (txtAmount.isEmpty()) {
            buttonContinue.setEnabled(false);
            return false;
        }
        Double dblAmount;
        try {
            dblAmount = Double.parseDouble(txtAmount);
        } catch (NumberFormatException e) {
            toast("Error exchanging value");
            buttonContinue.setEnabled(false);
            return false;
        }
        double dblAmountTo = 0.0;
        try {
            dblAmountTo = Double.parseDouble(toValue.getText().toString());
        } catch (NumberFormatException ignore) {
        }

        WalletAccount fromAccount = fromAccountAdapter.getItem(fromRecyclerView.getSelectedItem()).account;
        if (checkMin && minAmount == NOT_LOADED) {
            buttonContinue.setEnabled(false);
            toast("Please wait while loading minimum amount information.");
            return false;
        } else if (fromAccount.getAccountBalance().confirmed.getValueAsBigDecimal().compareTo(BigDecimal.valueOf(dblAmount)) < 0) {
            buttonContinue.setEnabled(false);
            TextView tvError = valueKeyboard.getVisibility() == View.VISIBLE
                    && valueKeyboard.getInputTextView() == toValue
                    ? tvErrorTo : tvErrorFrom;
            tvError.setText(R.string.balance_error);
            tvError.setVisibility(View.VISIBLE);
            exchangeFiatRate.setVisibility(View.INVISIBLE);
            return false;
        } else if (checkMin && minAmount != NOT_LOADED
                && dblAmount.compareTo(getMinAmountWithFee()) < 0) {
            buttonContinue.setEnabled(false);
            if (dblAmount != 0 || dblAmountTo != 0) {
                TextView tvError = valueKeyboard.getVisibility() == View.VISIBLE
                        && valueKeyboard.getInputTextView() == toValue
                        ? tvErrorTo : tvErrorFrom;
                tvError.setText(getString(R.string.exchange_minimum_amount
                        , decimalFormat.format(getMinAmountWithFee()), "BCH"));
                tvError.setVisibility(View.VISIBLE);

                exchangeFiatRate.setVisibility(View.INVISIBLE);
            }
            return false;
        }
        buttonContinue.setEnabled(true);
        return true;
    }

    private Map<WalletAccount, Double> cachedMinAmountWithFee = new HashMap<>();

    private double getMinAmountWithFee() {
        WalletAccount account = fromAccountAdapter.getItem(fromRecyclerView.getSelectedItem()).account;
        Double result = cachedMinAmountWithFee.get(account);
        if (result == null) {
            BigDecimal txFee = UtilsKt.estimateFeeFromTransferrableAmount(account
                    , mbwManager, BitcoinCash.nearestValue(minAmount).getLongValue());
            result = minAmount + txFee.doubleValue();
            cachedMinAmountWithFee.put(account, result);
        }
        return result;
    }

    private void updateUi() {
        Value currencyBTCValue = null;
        try {
            currencyBTCValue = mbwManager.getCurrencySwitcher().getAsFiatValue(
                    Utils.getBtcCoinType().value(toValue.getText().toString()));
        } catch (IllegalArgumentException ignore) {
        }
        if (currencyBTCValue != null && tvErrorTo.getVisibility() != View.VISIBLE) {
            exchangeFiatRate.setText(ABOUT + ValueExtensionsKt.toStringWithUnit(currencyBTCValue));
            exchangeFiatRate.setVisibility(View.VISIBLE);
        } else {
            exchangeFiatRate.setVisibility(View.INVISIBLE);
        }
    }

    private void toast(String msg) {
        new Toaster(getActivity()).toast(msg, true);
    }

    @Subscribe
    public void exchangeRatesRefreshed(ExchangeRatesRefreshed event) {
        updateUi();
    }

    class GetMinCallback implements Callback<ChangellyAnswerDouble> {
        @Override
        public void onResponse(@NonNull Call<ChangellyAnswerDouble> call, @NonNull Response<ChangellyAnswerDouble> response) {
            ChangellyAnswerDouble result = response.body();
            if(result == null || result.result == NOT_LOADED) {
                Log.e("CiliaChangelly", "Minimum amount could not be retrieved");
                new Toaster(getActivity()).toast("Service unavailable", false);
                return;
            }
            double min = result.result;
            Log.d(TAG, "Received minimum amount: " + min);
            cachedMinAmountWithFee.clear();
            sharedPreferences.edit()
                    .putFloat(BCH_MIN_EXCHANGE_VALUE, (float) min)
                    .apply();
            minAmount = min;
        }

        @Override
        public void onFailure(@NonNull Call<ChangellyAnswerDouble> call, @NonNull Throwable t) {
            toast("Service unavailable");
        }
    }

    class GetOfferCallback implements Callback<ChangellyAnswerDouble> {
        double fromAmount;

        GetOfferCallback(double fromAmount) {
            this.fromAmount = fromAmount;
        }

        @Override
        public void onResponse(@NonNull Call<ChangellyAnswerDouble> call,
                               @NonNull Response<ChangellyAnswerDouble> response) {
            ChangellyAnswerDouble result = response.body();
            if(result != null) {
                double amount = result.result;
                avoidTextChangeEvent = true;
                try {
                    if (fromAmount == getFromExcludeFee().doubleValue()) {
                        toValue.setText(decimalFormat.format(amount));
                    }
                } catch (NumberFormatException ignore) {
                }
                if (fromAmount != 0 && amount != 0) {
                    bchToBtcRate = amount / fromAmount;
                    exchangeRate.setText("1 BCH ~ " + decimalFormat.format(bchToBtcRate) + " BTC");
                    exchangeRate.setVisibility(View.VISIBLE);
                }
                isValueForOfferOk(true);

                avoidTextChangeEvent = false;
                updateUi();
            }
        }

        @Override
        public void onFailure(@NonNull Call<ChangellyAnswerDouble> call,
                              @NonNull Throwable t) {
            toast("Service unavailable");
        }
    }
}
