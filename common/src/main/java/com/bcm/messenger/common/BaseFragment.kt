package com.bcm.messenger.common

import androidx.fragment.app.Fragment

/**
 * bcm.social.01 2019/3/7.
 */
open class BaseFragment : Fragment() {
    private var isActive: Boolean = false

    open fun setActive(isActive: Boolean) {
        this.isActive = isActive
    }

    open fun isActive(): Boolean {
        return isActive
    }
}