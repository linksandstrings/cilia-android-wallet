package com.mycelium.demo.hello

import android.net.Uri
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import android.widget.TextView
import com.mycelium.modularizationtools.CommunicationManager

import kotlinx.android.synthetic.main.activity_hello.*

class HelloActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hello)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Loading secrets", Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
            if (CommunicationManager.getInstance().requestPair("com.mycelium.demo.world")) {
                contentResolver.query(Uri.parse("content://com.mycelium.demo.world.providers.Secretsprovider"), null, null, null, null).use {
                    val secretsList = mutableListOf<String>()
                    while (it?.moveToNext() == true) {
                        secretsList.add(it.getString(0))
                    }
                    rvSecrets.adapter = SecretsRecyclerAdapter(secretsList)
                    Snackbar.make(view, "Secrets received: ${secretsList.joinToString(" ")}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
        rvSecrets.adapter = SecretsRecyclerAdapter(listOf("no data", "press the button"))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_hello, menu)
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

class SecretsRecyclerAdapter(val secretsList: List<String>) : RecyclerView.Adapter<SecretsRecyclerAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.list_layout, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindItems(secretsList[position])
    }

    override fun getItemCount(): Int {
        return secretsList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindItems(msg: String) {
            itemView.findViewById<TextView>(R.id.textViewMsg).text = msg
        }
    }
}