package com.bcm.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import com.bcm.route.api.BcmRouter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        home_hello.setOnClickListener {
//            BcmRouter.getInstance().get("SecondActivity")
//                .navigation(this)

            val provider = BcmRouter.getInstance().get("TestProvider")
                .navigationWithCast<TestProvider>()
            Log.i("Main", "Provider is $provider")
        }
    }
}
