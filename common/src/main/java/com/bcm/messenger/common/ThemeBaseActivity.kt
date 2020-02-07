package com.bcm.messenger.common

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.theme.ThemeManager

/**
 * Created by Kin on 2020/2/7
 */
open class ThemeBaseActivity : AppCompatActivity() {
    protected val themeManager = ThemeManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager.onCreate(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        themeManager.onResume(this)
    }
}