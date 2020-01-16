package com.bcm.messenger.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ARouterConstants.Activity.APP_DEV_SETTING
import com.bcm.messenger.common.bcmhttp.conncheck.IMServerConnectionChecker
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.logic.EnvSettingLogic
import com.bcm.messenger.logic.bean.EnvSetting
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.HexUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_dev_setting.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.util.regex.Pattern


@Route(routePath = APP_DEV_SETTING)
class DevSettingsActivity : AppCompatActivity() {
    private val TAG = "DevSettingsActivity"

    private val curEnv = EnvSettingLogic.getEnvSetting().copy()
    private val connChecker = IMServerConnectionChecker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppUtil.isReleaseBuild()) {
            return
        }

        setContentView(R.layout.activity_dev_setting)
        setupActionBar()
        initServerEnv()

        window.setStatusBarLightMode()
    }

    private fun initServerEnv() {
        dev_setting_dev_enable.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            curEnv.devEnable = isChecked
            updateEnvState(curEnv)
        })


        edit_selection.setOnClickListener {
            val builder = AmePopup.bottom.newBuilder()
                    .withTitle(getString(R.string.dev_settings_ip_select_title))
            for (env in EnvSettingLogic.EnvList){
                builder.withPopItem(AmeBottomPopup.PopupItem(env){
                    curEnv.server = env
                    curEnv.lbsEnable = (env == EnvSettingLogic.RELEASE_HOST)
                    updateEnvState(curEnv)
                })
            }
            builder.withDoneTitle(getString(R.string.common_cancel))
                    .show(this)
        }

        dev_setting_dev_wallet.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            curEnv.walletDev = isChecked
        })

        dev_setting_dev_lbs.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            curEnv.lbsEnable = isChecked
            updateEnvState(curEnv)
        })

        dev_setting_dev_https.setOnCheckedChangeListener (CompoundButton.OnCheckedChangeListener { _, isChecked ->
            curEnv.httpsEnable = isChecked
            updateEnvState(curEnv)
        })

        dev_setting_dev_crash.setOnClickListener {
            val outOfBoundsException: ArrayList<String> = ArrayList()
            ALog.i(TAG, outOfBoundsException[0])
        }

        dev_setting_dev_anr.setOnClickListener {
            Thread.sleep(50000)
        }

        dev_setting_dev_conn_test.setOnClickListener {
            connChecker.check(0, AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.mainScheduler)
                    .subscribe({
                        ToastUtil.show(this, "Success:$it")
                    },{
                        ToastUtil.show(this, "Fail")
                    })
        }

        dev_setting_dev_pull_data.setOnClickListener {
            if (!AMELogin.majorContext.isLogin) {
                ToastUtil.show(this, "Export failed")
                return@setOnClickListener
            }

            val dirs = arrayOf("messages%s.db", "new_group%s", "user_%s.db")
            for (i in dirs) {
                val name = String.format(i, AMELogin.majorUid)
                val dbFile = AppContextHolder.APP_CONTEXT.getDatabasePath(name)
                val diskPath = Environment.getExternalStorageDirectory().absolutePath
                val dbDestPath= diskPath + File.separatorChar + ARouterConstants.SDCARD_ROOT_FOLDER + File.separatorChar + name

                val dbDestFile = File(dbDestPath)
                if (!dbDestFile.exists()) {
                    dbDestFile.createNewFile()
                }

                var source: FileChannel? = null
                var destination: FileChannel? = null

                try {
                    source = FileInputStream(dbFile).channel
                    destination = FileOutputStream(dbDestFile).channel
                    destination.transferFrom(source, 0, source!!.size())
                } catch (e:Throwable)  {
                    ToastUtil.show(this, "Export failed")
                }
                finally {
                    try {
                        source?.close()
                        destination?.close()
                    } catch (e:Throwable) {
                        ALog.e(TAG, e)
                    }
                }
            }


            ToastUtil.show(this, "Export success")
        }

        dev_host_edit.addTextChangedListener(object :TextWatcher{
            override fun afterTextChanged(s: Editable?) {
                if (curEnv.server != s.toString()){
                    curEnv.server = s.toString()
                    updateEnvState(curEnv)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })

        dev_setting_dev_sign.setOnClickListener {
            val hash = EncryptUtils.computeSHA256(packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures[0].toByteArray())
            ToastUtil.show(this, HexUtil.toString(hash))
            ALog.i(TAG, "hash:${HexUtil.toString(hash)}")
        }


        edit_go_webpage.setOnClickListener {
            val url = dev_webpage_edit.text.toString()

            if (url.trim().isEmpty()) {
                AmePopup.center.newBuilder()
                        .withTitle(getString(R.string.dev_settings_webview_test_dialog_title))
                        .withContent(getString(R.string.dev_settings_webview_test_dialog_content))
                        .withOkTitle(getString(R.string.dev_settings_webview_test_dialog_ok))
                        .show(this)
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.WEB)
                    .putString(ARouterConstants.PARAM.WEB_URL, url)
                    .navigation()
        }

        updateEnvState(curEnv)
    }

    private fun updateEnvState(env: EnvSetting) {
        dev_host_layout.visibility = if (env.devEnable && !env.lbsEnable) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (dev_host_layout.visibility == View.VISIBLE){
            if (dev_host_edit.text.toString() != env.server){
                dev_host_edit.setText(env.server)
                dev_host_edit.setSelection(env.server.length)
            }
        }

        dev_setting_dev_enable.setSwitchStatus(env.devEnable)
        dev_setting_dev_lbs.setSwitchStatus(env.lbsEnable)
        dev_setting_dev_wallet.setSwitchStatus(env.walletDev)
        dev_setting_dev_https.setSwitchStatus(env.httpsEnable)
    }

    override fun onBackPressed() {
        saveSetting()
    }

    private fun setupActionBar() {
        toolbar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                saveSetting()
            }
        })
    }


    private fun saveSetting() {
        val env = EnvSettingLogic.getEnvSetting()
        if (env.diff(curEnv)) {
            val index = EnvSettingLogic.indexOfHost(curEnv.server)
            if(index == null && !checkGoodServer(curEnv.server)){
                AmePopup.result.failure(this, "host format error")
                return
            }

            AmePopup.center.newBuilder()
                    .withTitle(getString(R.string.dev_settings_save_settings_title))
                    .withOkTitle(getString(R.string.common_popup_ok))
                    .withCancelTitle(getString(R.string.common_cancel))
                    .withCancelListener {
                        finish()
                    }.withOkListener {
                        if (AMELogin.isLogin) {
                            AmePopup.loading.show(this@DevSettingsActivity)
                            Observable.create(ObservableOnSubscribe<Boolean> {
                                try {
                                    if(AMELogin.majorContext.isLogin) {
                                        AmeLoginLogic.quit(AMELogin.majorContext,false)
                                    }
                                    it.onNext(true)
                                } catch (ex: Exception) {
                                    it.onNext(false)
                                } finally {
                                    it.onComplete()
                                }
                            }).subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe {
                                        AmePopup.loading.dismiss()
                                        EnvSettingLogic.setEnvSetting(curEnv)
                                        finish()
                                        AppUtil.exitApp()
                                    }
                        } else {
                            EnvSettingLogic.setEnvSetting(curEnv)
                            AppUtil.exitApp()
                        }
                    }.show(this)
            return
        } else {
            finish()
        }
    }


    private fun checkGoodServer(server:String): Boolean{
        val list = server.split(":")
        if (list.size != 2){
            return false
        }

        try {
            val port = list[1].toInt()
            if (port <= 0){
                return false
            }

            val ipSegments = list[0].split(".")
            if (checkIpChars(list[0]) && ipSegments.size != 4){
                return false
            }
        } catch (e:Throwable) {
            return false
        }
        return true
    }

    private fun checkIpChars(host:String):Boolean {
        try {
            val trimHost = host.trim()

            val pattern = Pattern.compile("[0-9.]*")
            val matcher = pattern.matcher(trimHost)
            matcher.find()
            if (matcher.group() == trimHost) {
                return true
            }
        } catch (tr: Throwable) {
            ALog.e(TAG, tr)
        }
        return false
    }
}