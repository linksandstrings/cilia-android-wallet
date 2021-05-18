package com.cilia.wallet.activity.news

import android.annotation.TargetApi
import android.content.*
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View.*
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.appbar.AppBarLayout
import com.cilia.wallet.R
import com.cilia.wallet.activity.modern.NewsFragment
import com.cilia.wallet.activity.modern.adapter.NewsAdapter
import com.cilia.wallet.activity.settings.SettingsPreference
import com.cilia.wallet.external.mediaflow.GetMediaFlowTopicTask
import com.cilia.wallet.external.mediaflow.MediaFlowSyncWorker
import com.cilia.wallet.external.mediaflow.NewsConstants
import com.cilia.wallet.external.mediaflow.NewsSyncUtils
import com.cilia.wallet.external.mediaflow.database.NewsDatabase
import com.cilia.wallet.external.mediaflow.model.News
import kotlinx.android.synthetic.main.activity_news.*
import kotlin.math.abs
import kotlin.math.exp


class NewsActivity : AppCompatActivity() {
    lateinit var news: News
    private lateinit var preference: SharedPreferences

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NewsConstants.MEDIA_FLOW_UPDATE_ACTION) {
                GetMediaFlowTopicTask(news.id) {
                    it?.let {
                        news = it
                        updateUI()
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news)
        collapsing_toolbar.setStatusBarScrimColor(Color.parseColor("#1a1a1a"))
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        app_bar_layout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val scrollDelta = abs(verticalOffset * 1f / appBarLayout.totalScrollRange)
            tvCategory.alpha = 1 - scrollDelta
            toolbar_shadow.visibility = if (scrollDelta == 1f) VISIBLE else GONE
            collapsing_toolbar.title = if (scrollDelta == 1f) Html.fromHtml(news.title.rendered) else ""
            llRoot.clipChildren = scrollDelta == 1f
            llRoot.clipToPadding = scrollDelta == 1f
        })
        news = intent.getSerializableExtra(NewsConstants.NEWS) as News
        if (!news.isFull) {
            WorkManager.getInstance(this)
                    .enqueueUniqueWork(NewsSyncUtils.WORK_NAME_ONCE, ExistingWorkPolicy.REPLACE,
                            OneTimeWorkRequest.Builder(MediaFlowSyncWorker::class.java).build())
        }
        NewsDatabase.markRead(news)
        content.setBackgroundColor(Color.TRANSPARENT)
        preference = getSharedPreferences(NewsConstants.NEWS_PREF, Context.MODE_PRIVATE)!!
        updateUI()
        content.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                content.postDelayed({
                    val params = content.layoutParams
                    val widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY)
                    val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    content.measure(widthMeasureSpec, heightMeasureSpec)
                    params.height = content.measuredHeight
                    content.layoutParams = params
                }, 500)
            }

            @TargetApi(Build.VERSION_CODES.N)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return handleUri(request?.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleUri(Uri.parse(url))
            }

            private fun handleUri(uri: Uri?): Boolean {
                if (uri != null) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
                return false
            }
        }
        content.imageClicklistener = { url ->
            startActivity(Intent(this, NewsImageActivity::class.java)
                    .putExtra("url", url))
        }
        ivImage.setOnClickListener {
            startActivity(Intent(this, NewsImageActivity::class.java)
                    .putExtra("url", news.image))
        }
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
            val layoutParams = scrollBar.layoutParams
            val scrollHeight = scrollView.getChildAt(0).measuredHeight - scrollView.measuredHeight
            layoutParams.width = scrollView.measuredWidth * scrollY / scrollHeight
            scrollBar.layoutParams = layoutParams
            if (bottomButtonBanner.visibility == VISIBLE) {
                // sigmoid function for smooth change translationY of banner button
                val contentHeight = content.height + headLayout.height + tvTitle.height +
                        resources.getDimensionPixelOffset(R.dimen.media_head_margin_sum)
                val sigmoid = 1.0f / (1.0f + exp((contentHeight - scrollView.measuredHeight - scrollY).toDouble() / 100))
                bottomButtonBanner.translationY = (sigmoid * bottomButtonBanner.height).toFloat()
            }
        })
        shareBtn2.setOnClickListener {
            share()
        }
        val fragment = supportFragmentManager.findFragmentById(R.id.otherNews) as NewsFragment
        fragment.currentNews = news
        fragment.newsClick = {
            finish()
            startActivity(Intent(this, NewsActivity::class.java)
                    .putExtra(NewsConstants.NEWS, it))
        }
        SettingsPreference.getMediaFlowContent()?.bannersDetails
                ?.firstOrNull { banner ->
                    banner.isActive() && news.tags?.firstOrNull { it.name?.equals(banner.tag, true) ?: false } != null
                            && SettingsPreference.isContentEnabled(banner.parentId)
                }?.let { banner ->
                    bottomButtonBanner.visibility = VISIBLE
                    Glide.with(bottomButtonBanner)
                            .load(banner.imageUrl)
                            .into(bottomButtonBanner)
                    bottomButtonBanner.setOnClickListener {
                        openLink(banner.link)
                    }
                }
    }

    private fun openLink(link: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    }

    fun updateUI() {
        news.content?.rendered?.let { topicContent ->
            val parsedContent = NewsUtils.parseNews(topicContent)
            val contentText = parsedContent.news
                    .replace("width=\".*?\"", "width=\"100%\"")
                    .replace("width: .*?px", "width: 100%")
                    .replace("height=\".*?\"", "")
            content.settings.defaultFontSize = 14

            val html = getString(R.string.media_flow_html_template
                    , resources.toWebViewPx(12f).toString()
                    , resources.toWebViewPx(24f).toString()
                    , resources.toWebViewPx(16f).toString()
                    , resources.toWebViewPx(2f).toString()
                    , resources.toWebViewPx(8f).toString()
                    , contentText)
            content.loadDataWithBaseURL("https://blog.mycelium.com", html, "text/html", "UTF-8", null)
        }
        news_loading.visibility = if (news.isFull) INVISIBLE else VISIBLE

        tvTitle.text = Html.fromHtml(news.title.rendered)
        news.date?.let {
            tvDate.text = NewsUtils.getDateString(this, news)
        }
        tvAuthor.text = news.author?.name

        val categoryText = news.categories?.firstOrNull()?.name ?: ""
        tvCategory.text = categoryText
        news.image?.let {
            Glide.with(ivImage)
                    .load(news.getFitImage(resources.displayMetrics.widthPixels))
                    .apply(RequestOptions().centerCrop().error(R.drawable.mediaflow_default_picture))
                    .into(ivImage)
        }
    }


    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, IntentFilter(NewsConstants.MEDIA_FLOW_UPDATE_ACTION))
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
        super.onPause()
    }

    private fun share() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_SUBJECT, Html.fromHtml(news.title.rendered))
                .putExtra(Intent.EXTRA_TEXT, news.link)
                .setType("text/plain"), getString(R.string.share_news)))
    }

    private fun favorite() {
        preference.edit()
                .putBoolean(NewsAdapter.PREF_FAVORITE + news.id, !preference.getBoolean(NewsAdapter.PREF_FAVORITE + news.id, false))
                .apply()
        invalidateOptionsMenu()
    }

    private fun Resources.toWebViewPx(dipValue: Float): Float {
        val metrics = this.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics) / metrics.density
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_share, menu)
        menu?.findItem(R.id.favorite)?.icon = resources.getDrawable(
                if (preference.getBoolean(NewsAdapter.PREF_FAVORITE + news.id, false)) R.drawable.ic_favorite
                else R.drawable.ic_not_favorite)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.share -> {
                share()
                return true
            }
            R.id.favorite -> {
                favorite()
                return true
            }
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

}
