package com.mycelium.demo.world

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.mycelium.modularizationtools.CommunicationManager

class Secretsprovider : ContentProvider() {
    private var communicationManager : CommunicationManager? = null

    override fun onCreate(): Boolean {
        CommunicationManager.init(context!!)
        communicationManager = CommunicationManager.getInstance()
        return true
    }

    override fun query(p0: Uri, p1: Array<String>?, p2: String?, p3: Array<String>?, p4: String?): Cursor? {
        if(!communicationManager!!.requestPair(callingPackage!!)) {
            return null
        }
        return MatrixCursor(arrayOf("msg")).also { mc ->
            WorldApplication.sentMessages.forEach {
                mc.addRow(listOf(it))
            }
        }
    }

    override fun insert(p0: Uri, p1: ContentValues?): Uri? {
        TODO("not implemented")
    }

    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<String>?): Int {
        TODO("not implemented")
    }

    override fun delete(p0: Uri, p1: String?, p2: Array<String>?): Int {
        TODO("not implemented")
    }

    override fun getType(p0: Uri): String? {
        TODO("not implemented")
    }
}
