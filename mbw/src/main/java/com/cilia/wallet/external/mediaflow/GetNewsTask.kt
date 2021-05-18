package com.cilia.wallet.external.mediaflow

import android.os.AsyncTask
import com.cilia.wallet.external.mediaflow.database.NewsDatabase
import com.cilia.wallet.external.mediaflow.model.Category
import com.cilia.wallet.external.mediaflow.model.News


class GetNewsTask(val search: String? = null, private val categories: List<Category> = listOf(),
                  val offset: Int = 0, val limit: Int = -1,
                  var listener: (List<News>, Long) -> Unit) : AsyncTask<Void, Void, List<News>>() {
    private var count = 0L
    override fun doInBackground(vararg p0: Void?): List<News> {
        count = NewsDatabase.getNewsCount(search, categories)
        return NewsDatabase.getNews(search, categories, limit, offset)
    }

    override fun onPostExecute(result: List<News>) {
        super.onPostExecute(result)
        listener.invoke(result, count)
    }
}

class GetAllNewsTask(var listener: (List<News>, Long) -> Unit) : AsyncTask<Void, Void, List<News>>() {
    private var count = 0L
    override fun doInBackground(vararg p0: Void?): List<News> {
        count = NewsDatabase.getNewsCount(null, listOf())
        return NewsDatabase.getCategories().flatMap { NewsDatabase.getNews(null, listOf(it), 6) }
    }

    override fun onPostExecute(result: List<News>) {
        super.onPostExecute(result)
        listener.invoke(result, count)
    }
}

class GetMediaFlowTopicTask(val id: Int, var listener: (News?) -> Unit)
    : AsyncTask<Void, Void, News?>() {
    override fun doInBackground(vararg p0: Void?): News? =
            NewsDatabase.getTopic(id)

    override fun onPostExecute(result: News?) {
        super.onPostExecute(result)
        listener.invoke(result)
    }
}