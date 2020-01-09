package com.bcm.messenger.me.ui.language

import android.content.Context
import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.me.R
import kotlinx.android.synthetic.main.me_activity_language_select.*
import java.util.*
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.core.updateLanguage
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.CommonSettingItem
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_item_language_select.view.*
import com.bcm.messenger.common.AccountSwipeBaseActivity

class LanguageSelectActivity : AccountSwipeBaseActivity() {

    private var adapter: LanguageSelectAdapter? = null
    private var languageViewModel: LanguageViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_language_select)
        languageViewModel = ViewModelProviders.of(this).get(LanguageViewModel::class.java)
        languageViewModel?.languageName?.value = SuperPreferences.getLanguageString(this, Locale.getDefault().language)
        languageViewModel?.countryName?.value = SuperPreferences.getLanguageString(this, Locale.getDefault().country)

        language_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                saveLanguage()
            }
        })

        language_list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        adapter = LanguageSelectAdapter(this)
        language_list.adapter = adapter
        adapter?.loadData(LanguageViewModel.getDisplayLanguageList())
    }

    fun saveLanguage() {
        updateLanguage(baseContext, languageViewModel?.countryName?.value, languageViewModel?.languageName?.value)
        var flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        flags = flags.or(Intent.FLAG_ACTIVITY_NEW_TASK)
        BcmRouter.getInstance().get(ARouterConstants.Activity.APP_LAUNCH_PATH).setFlags(flags).navigation(this)
    }


    inner class LanguageSelectAdapter(context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var mInflater = LayoutInflater.from(context)
        private var mDataList: List<LanguageSelectBean>? = null

        fun loadData(list: List<LanguageSelectBean>?) {
            mDataList = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return LanguageSelectViewHolder(mInflater.inflate(R.layout.me_item_language_select, parent, false))
        }

        override fun getItemCount(): Int {
            return mDataList?.size ?: 0
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is LanguageSelectViewHolder) {
                holder.bindData(mDataList?.get(position))
            }
        }

    }

    inner class LanguageSelectViewHolder(containerView: View) : RecyclerView.ViewHolder(containerView) {

        fun bindData(data: LanguageSelectBean?) {
            data ?: return
            itemView.language_select_item.setName(data.name)
            itemView.language_select_item.setSubName(data.subName)
            val languageViewModel = ViewModelProviders.of(this@LanguageSelectActivity).get(LanguageViewModel::class.java)
            itemView.language_select_item.setOnClickListener {
                languageViewModel.languageName.value = data.languageName
                languageViewModel.countryName.value = data.countryName
            }
            languageViewModel.languageName.observe(this@LanguageSelectActivity, androidx.lifecycle.Observer {
                if (languageViewModel.languageName.value == data.languageName) {
                    itemView.language_select_item.showRightStatus(CommonSettingItem.RIGHT_YES)
                } else {
                    itemView.language_select_item.showRightStatus(CommonSettingItem.RIGHT_NONE)
                }
            })

        }
    }
}