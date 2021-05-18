package com.mycelium.demo.world

import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.mycelium.modularizationtools.CommunicationManager

import kotlinx.android.synthetic.main.activity_world.*

class WorldActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_world)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            val number = (Math.random() * 10).toInt()
            val msg = "My secret number is $number."
            Snackbar.make(view, "Sending '$msg' to Hello.", Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
            WorldApplication.sentMessages.add(msg)
            CommunicationManager.getInstance().send("com.mycelium.demo.hello", Intent("someAction").putExtra("message", msg))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_world, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
