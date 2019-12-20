package com.bcm.messenger.contacts

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager
import com.bcm.route.annotation.Route
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.api.IContactsAction
import com.bcm.messenger.common.api.IContactsCallback
import kotlinx.android.synthetic.main.contacts_fragment_contacts_selection.*
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.common.recipients.Recipient

/**
 * Created by zjl on 2018/4/8.
 */
@Route(routePath = ARouterConstants.Fragment.SELECT_CONTACTS)
class ContactsSelectionFragment : Fragment(), IContactsAction {

    private val mContactFragments = mutableListOf<Fragment>()
    private var mPosition: Int = -1

    private var mIndividualContact: SingleContactSelectionFragment? = null
    private var mGroupContact: SingleContactSelectionFragment? = null

    init {
        val individualFragment = SingleContactSelectionFragment()
        var arg = Bundle()
        arg.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CONTACT_GROUP, false)
        arg.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, true)
        individualFragment.arguments = arg
        mContactFragments.add(individualFragment)
        mIndividualContact = individualFragment

        val groupFragment = SingleContactSelectionFragment()
        arg = Bundle()
        arg.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CONTACT_GROUP, true)
        arg.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, true)
        groupFragment.arguments = arg
        mContactFragments.add(groupFragment)
        mGroupContact = groupFragment
    }

    override fun onActivityCreated(icicle: Bundle?) {
        super.onActivityCreated(icicle)
        initView()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.contacts_fragment_contacts_selection, container, false)
    }

    private fun initView() {
        val fm = fragmentManager ?: return

        contacts_content.adapter = object : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
            override fun getItem(position: Int): Fragment {
                return mContactFragments[position]
            }

            override fun getCount(): Int {
                return mContactFragments.size
            }

        }

        contacts_content.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

            }

            override fun onPageSelected(position: Int) {
                selectContacts(position)
            }

            override fun onPageScrollStateChanged(state: Int) {

            }
        })

        contacts_individual.setOnClickListener {
            selectContacts(0)
        }
        contacts_group.setOnClickListener {
            selectContacts(1)
        }
        selectContacts(0)
    }

    private fun selectContacts(index: Int) {
        activity?.hideKeyboard()

        if (mPosition == index) {
            return
        }
        mPosition = index
        contacts_content.setCurrentItem(mPosition, true)
        when (mPosition) {
            0 -> {
                contacts_individual.isSelected = true
                contacts_individual.setTextColor(getColorCompat(R.color.common_color_white))
                contacts_group.isSelected = false
                contacts_group.setTextColor(getColorCompat(R.color.common_color_black))
            }
            1 -> {
                contacts_individual.isSelected = false
                contacts_individual.setTextColor(getColorCompat(R.color.common_color_black))
                contacts_group.isSelected = true
                contacts_group.setTextColor(getColorCompat(R.color.common_color_white))
            }
        }
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)
        ALog.d("ContactsSelectionFragment", "setArguments")
        var arg = mIndividualContact?.arguments ?: Bundle()
        if (args != null) {
            arg.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, args.getBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, true))
            mIndividualContact?.arguments = arg
        }

        arg = mGroupContact?.arguments ?: Bundle()
        if (args != null) {
            arg.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, args.getBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, true))
            mGroupContact?.arguments = arg
        }


    }

    override fun setMultiMode(multiSelect: Boolean) {
        mIndividualContact?.setMultiMode(multiSelect)
        mGroupContact?.setMultiMode(multiSelect)
    }

    override fun queryContacts(filter: String, callback: IContactsAction.QueryResultCallback?) {
        val target = mContactFragments.size
        var count = 0
        val resultList = mutableListOf<Recipient>()
        fun notify(recipientList: List<Recipient>) {
            resultList.addAll(recipientList)
            count++
            if (count >= target) {
                callback?.onQueryResult(resultList)
            }
        }
        mIndividualContact?.queryContacts(filter, object : IContactsAction.QueryResultCallback {
            override fun onQueryResult(recipientList: List<Recipient>) {
                notify(recipientList)
            }

        })
        mGroupContact?.queryContacts(filter, object : IContactsAction.QueryResultCallback {
            override fun onQueryResult(recipientList: List<Recipient>) {
                notify(recipientList)
            }
        })
    }

    override fun queryContactsFromRemote(address: String, callback: IContactsAction.QueryResultCallback) {
        mIndividualContact?.queryContactsFromRemote(address, callback)
    }

    override fun addSearchBar(context: Context) {
        mIndividualContact?.addSearchBar(context)
        mGroupContact?.addSearchBar(context)
    }

    override fun addEmptyShade(context: Context) {
        mIndividualContact?.addSearchBar(context)
        mGroupContact?.addSearchBar(context)
    }

    override fun addHeader(header: View) {

    }

    override fun addFooter(footer: View) {

    }

    override fun showHeader(index: Int, show: Boolean) {

    }

    override fun showFooter(index: Int, show: Boolean) {

    }

    override fun setSelected(recipient: Recipient, select: Boolean) {
        if (recipient.isGroupRecipient) {
            mGroupContact?.setSelected(recipient, select)
        } else {
            mIndividualContact?.setSelected(recipient, select)
        }
    }

    override fun setContactSelectCallback(callback: IContactsCallback) {
        mIndividualContact?.setContactSelectCallback(callback)
        mGroupContact?.setContactSelectCallback(callback)
    }

}
