package com.mycelium.bequant.kyc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.cilia.wallet.R
import kotlinx.android.synthetic.main.activity_bequant_kyc.*


class BequantKycActivity : AppCompatActivity(R.layout.activity_bequant_kyc) {
    private lateinit var viewModel: BequantKycViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            setHomeAsUpIndicator(resources.getDrawable(R.drawable.ic_back_arrow))
        }
        viewModel = ViewModelProviders.of(this).get(BequantKycViewModel::class.java)
        viewModel.title.observe(this, Observer {
            supportActionBar?.title = it
        })
    }
}