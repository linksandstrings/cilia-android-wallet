package com.cilia.wallet.external.changelly.bch;


import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import com.cilia.wallet.R;
import com.cilia.wallet.activity.view.ValueKeyboard;

public class ExchangeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exchange);
        setTitle(getString(R.string.excange_title));
        ActionBar bar = getSupportActionBar();
        bar.setDisplayShowHomeEnabled(true);
        bar.setIcon(R.drawable.action_bar_logo);

        getWindow().setBackgroundDrawableResource(R.drawable.background_witherrors_centered);

        if (getFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new ExchangeFragment(), "ExchangeFragment")
                    .addToBackStack("ExchangeFragment")
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onBackPressed() {
        ValueKeyboard valueKeyboard = findViewById(R.id.numeric_keyboard);
        if (valueKeyboard != null && valueKeyboard.getVisibility() == View.VISIBLE) {
            valueKeyboard.done();
        } else if (getFragmentManager().getBackStackEntryCount() > 1) {
            getFragmentManager().popBackStack();
        } else {
            finish();
        }
    }
}
