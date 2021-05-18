package com.cilia.wallet.activity

import android.app.Activity
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.core.internal.deps.guava.collect.Iterables
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

inline fun <reified T : Activity> waitForActivity(timeout: Long = 10000L) {
    runBlocking {
        withTimeout(timeout) {
            do {
                delay(300L)
            } while (getCurrentActivity() !is T)
        }
    }
}

inline fun <reified T : Activity> waitWhileAcitvity(): ActivityIdlingResource {
    val activityIdlingResource = ActivityIdlingResource(T::class.java)
    IdlingRegistry.getInstance().register(activityIdlingResource)
    return activityIdlingResource
}

fun getCurrentActivity(): Activity? {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    val arrayOfActivities = arrayOfNulls<Activity>(1)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val activities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        arrayOfActivities[0] = Iterables.getOnlyElement(activities)
    }
    return arrayOfActivities[0]
}

fun customKeyboardType(message: String) {
    message.toCharArray().forEach {
        Espresso.onView(ViewMatchers.withText(it.toString())).perform(ViewActions.click())
    }
}