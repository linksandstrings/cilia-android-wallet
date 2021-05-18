package com.mycelium.bequant.signin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.cilia.wallet.BuildConfig
import com.cilia.wallet.R


class SignInViewModel(application: Application) : AndroidViewModel(application) {
    val buildVersion = MutableLiveData(application.getString(R.string.build_version, BuildConfig.VERSION_NAME))
    val email = MutableLiveData<String>()
    val password = MutableLiveData<String>()
}