package com.cilia.wallet.activity.main

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cilia.wallet.R
import com.cilia.wallet.R.string.*
import com.cilia.wallet.activity.main.adapter.RecommendationAdapter
import com.cilia.wallet.activity.main.model.*
import com.cilia.wallet.activity.settings.SettingsPreference
import com.cilia.wallet.activity.view.DividerItemDecoration
import com.cilia.wallet.external.Ads
import com.cilia.wallet.external.partner.model.Partner
import kotlinx.android.synthetic.main.main_recommendations_view.*

class RecommendationsFragment : Fragment() {
    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.main_recommendations_view, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list.addItemDecoration(DividerItemDecoration(resources.getDrawable(R.drawable.divider_account_list), LinearLayoutManager.VERTICAL)
                .apply { setFromItem(1) })

        val adapter = RecommendationAdapter(mutableListOf<RecommendationInfo>().apply {
            add(RecommendationHeader(SettingsPreference.getPartnersHeaderTitle(),
                    SettingsPreference.getPartnersHeaderText()))
            SettingsPreference.getPartners()
                    ?.filter { it.isActive() && SettingsPreference.isContentEnabled(it.parentId) }
                    ?.forEach {
                add(getPartnerInfo(it))
            }
            add(RecommendationFooter())
        })
        adapter.setClickListener(object : RecommendationAdapter.ClickListener {
            override fun onClick(bean: PartnerInfo) {
                if (bean.action != null) {
                    bean.action?.run()
                } else if (bean.info != null && bean.info.isNotEmpty()) {
                    alertDialog = AlertDialog.Builder(activity)
                            .setMessage(bean.info)
                            .setTitle(warning_partner)
                            .setIcon(bean.smallIcon)
                            .setPositiveButton(ok) { dialog, id ->
                                if (bean.uri != null) {
                                    val intent = Intent(Intent.ACTION_VIEW)
                                            .setData(Uri.parse(bean.uri))
                                    startActivity(intent)
                                }
                            }
                            .setNegativeButton(cancel, null)
                            .create()
                    alertDialog?.show()
                } else {
                    if (bean.uri != null) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(bean.uri)))
                    }
                }
            }

            override fun onClick(recommendationFooter: RecommendationFooter) {
                alertDialog = AlertDialog.Builder(activity)
                        .setTitle(your_privacy_out_priority)
                        .setMessage(partner_more_info_text)
                        .setPositiveButton(ok, null)
                        .setIcon(R.drawable.mycelium_logo_transp_small)
                        .create()
                alertDialog?.show()
            }

            override fun onClick(recommendationBanner: RecommendationBanner) {
                // implement when using big Banner Ad
            }
        })
        list.adapter = adapter
    }

    override fun onDestroy() {
        try {
            if (alertDialog?.isShowing == true) {
                alertDialog?.dismiss()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        super.onDestroy()
    }

    private fun getPartnerInfo(partner: Partner): PartnerInfo =
            PartnerInfo(partner.title, partner.description, partner.info, partner.link,
                    partner.imageUrl, if (partner.action?.isNotEmpty() == true) Runnable { Ads.doAction(partner.action, requireContext()) } else null)
}
