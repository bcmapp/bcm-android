package com.bcm.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bcm.route.annotation.Route

/**
 * Created by "Kin" on 2019/7/17
 */

@Route(routePath = "SecondActivity")
class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}