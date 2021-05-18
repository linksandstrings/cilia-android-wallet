package com.cilia.wallet.activity.settings

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import android.view.View
import com.cilia.wallet.R
import android.widget.RadioButton
import android.widget.RadioGroup
import com.cilia.wallet.Constants
import com.mycelium.wapi.wallet.btc.ChangeAddressMode

import org.junit.Before
import org.junit.Rule
import org.junit.Test

import org.junit.Assert.assertEquals

class SetSegwitChangeActivityTest {
    @Rule @JvmField
    val setSegwitChangeRule = ActivityTestRule(SetSegwitChangeActivity::class.java)
    private val sharedPrefs = InstrumentationRegistry.getInstrumentation().context.getSharedPreferences(Constants.SETTINGS_NAME, Activity.MODE_PRIVATE)
    private var sut: SetSegwitChangeActivity? = null
    private var radioGroup: RadioGroup? = null

    @Before
    fun setUp() {
        sut = setSegwitChangeRule.activity
        radioGroup = sut!!.findViewById(R.id.radio_group)
    }

    @Test
    fun changeSettingsArePersistedInSharedPrefs() {
        enumValues<ChangeAddressMode>().filter { it != ChangeAddressMode.NONE }.forEach {
            sut!!.runOnUiThread {
                val changeMode = it.toString()
                (radioGroup!!.findViewWithTag<View>(changeMode) as? RadioButton)!!.performClick()
                val changeModeInPrefs =  sharedPrefs.getString(Constants.CHANGE_ADDRESS_MODE, null)
                assertEquals(changeMode, changeModeInPrefs)
            }
        }
    }
}
