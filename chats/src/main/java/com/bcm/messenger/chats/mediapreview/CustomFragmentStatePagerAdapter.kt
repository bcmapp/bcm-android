package com.bcm.messenger.chats.mediapreview

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.viewpager.widget.PagerAdapter
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by Kin on 2018/10/30
 */
abstract class CustomFragmentStatePagerAdapter<T>(private val fm: FragmentManager) : PagerAdapter() {

    data class AdapterItem<T>(var fragment: Fragment, var data: T?, var position: Int)

    private val TAG = "FragmentStatePagerAdapter"
    private var mCurTransaction: FragmentTransaction? = null
    private val mSavedState = mutableListOf<Fragment.SavedState?>()
    private var mItems = mutableListOf<AdapterItem<T>?>()
    private var mCurrentPrimaryItem: Fragment? = null
    private var mNeedProcessCache = false

    abstract fun getItem(position: Int): Fragment

    abstract fun getItemData(position: Int): T?

    abstract fun getDataPosition(data: T?): Int

    fun getCurrentFragment() = mCurrentPrimaryItem

    override fun startUpdate(container: ViewGroup) {
        if (container.id == -1) {
            throw IllegalStateException("ViewPager with adapter $this requires a view id")
        }
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        if (mItems.size > position) {
            val item = mItems[position]
            if (item != null) {
                if (item.position == position) return item
                else checkCacheChanged()
            }
        }

        val fragment = getItem(position)
        if (mSavedState.size > position) {
            val ss = mSavedState[position]
            if (ss != null) {
                fragment.setInitialSavedState(ss)
            }
        }
        fragment.setMenuVisibility(false)
        fragment.userVisibleHint = false

        val newItem: AdapterItem<T> = AdapterItem(fragment, getItemData(position), position)
        while (mItems.size <= position) {
            mItems.add(null)
        }
        mItems[position] = newItem

        if (mCurTransaction == null) {
            mCurTransaction = fm.beginTransaction()
        }
        mCurTransaction?.add(container.id, fragment)

        return newItem
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val item = `object` as AdapterItem<T>
        while (mSavedState.size <= position) {
            mSavedState.add(null)
        }
        mSavedState[position] = if (item.fragment.isAdded) {
            fm.saveFragmentInstanceState(item.fragment)
        }
        else {
            null
        }

        while (mItems.size <= position) {
            mItems.add(null)
        }
        mItems[position] = null
        if (mCurTransaction == null) {
            mCurTransaction = fm.beginTransaction()
        }
        this.mCurTransaction?.remove(item.fragment)
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {

        val fragment = (`object` as AdapterItem<T>).fragment
        if (fragment != this.mCurrentPrimaryItem) {
            mCurrentPrimaryItem?.let {
                it.setMenuVisibility(false)
                it.userVisibleHint = false
            }
            fragment.setMenuVisibility(true)
            fragment.userVisibleHint = true
            this.mCurrentPrimaryItem = fragment
        }

    }

    override fun finishUpdate(container: ViewGroup) {
        this.mCurTransaction?.commitNowAllowingStateLoss()
        this.mCurTransaction = null
    }

    override fun getItemPosition(`object`: Any): Int {
        mNeedProcessCache = true
        `object` as AdapterItem<T>
        val oldPos = mItems.indexOf(`object`)
        if (oldPos >= 0) {
            val oldData = `object`.data
            val newData = getItemData(oldPos)
            if (oldData == newData) {
                mNeedProcessCache = false
                return POSITION_UNCHANGED
            }
            val newPos = getDataPosition(oldData)
            if (newPos < 0) {
                return POSITION_NONE
            }
            val oldItem = mItems[oldPos]
            if (oldItem != null) {
                oldItem.position = newPos
            }
            return newPos
        }
        return POSITION_NONE
    }

    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
        checkCacheChanged()
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        val fragment = (`object` as AdapterItem<T>).fragment
        return fragment.view === view
    }

    override fun saveState(): Parcelable? {
        var state: Bundle? = null
        if (this.mSavedState.size > 0) {
            state = Bundle()
            val fss = this.mSavedState.toTypedArray()
            state.putParcelableArray("states", fss)
        }

        for (i in this.mItems.indices) {
            val f = this.mItems[i]?.fragment
            if (f != null && f.isAdded) {
                if (state == null) {
                    state = Bundle()
                }
                val key = "f$i"
                this.fm.putFragment(state, key, f)
            }
        }

        return state
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        if (state != null) {
            val bundle = state as Bundle
            bundle.classLoader = loader
            val fss = bundle.getParcelableArray("states")
            this.mSavedState.clear()
            this.mItems.clear()
            if (fss != null) {
                this.mSavedState.addAll(fss.mapNotNull {
                    it as? Fragment.SavedState
                })
            }

            val keys = bundle.keySet()
            for (key in keys) {
                if (key.startsWith("f")) {
                    val index = key.substring(1).toInt()
                    val f = fm.getFragment(bundle, key)
                    if (f != null) {
                        while (mItems.size <= index) {
                            this.mItems.add(null)
                        }
                        f.setMenuVisibility(false)
                        this.mItems[index] = AdapterItem(f, getItemData(index), index)
                    } else {
                        ALog.w(TAG, "Bad fragment at key $key")
                    }
                }
            }
        }
    }

    private fun checkCacheChanged() {
        if (!mNeedProcessCache) return
        mNeedProcessCache = false
        val tempInfoList = mutableListOf<AdapterItem<T>?>()
        for (i in 0 until mItems.size) {
            tempInfoList.add(null)
        }
        mItems.forEach {
            if (it != null) {
                if (it.position >= 0) {
                    while (tempInfoList.size <= it.position) {
                        tempInfoList.add(null)
                    }
                    tempInfoList[it.position] = it
                }
            }
        }
        mItems = tempInfoList
    }
}