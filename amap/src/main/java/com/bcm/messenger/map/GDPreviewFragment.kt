package com.bcm.messenger.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.CoordinateConverter
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.model.*
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.BcmFileUtils
import com.example.amap.R
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * GDMap preview
 * Created by zjl on 2018/6/23.
 */
class GDPreviewFragment : Fragment() {

    private lateinit var map: MapView
    private var aMap: AMap? = null
    private var tileOverlay: TileOverlay? = null
    private var tileOverlayOptions: TileOverlayOptions? = null
    private var hasGoogleWall = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.amap_fragment_map_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity ?: return
        map = view.findViewById(R.id.map)
        map.onCreate(savedInstanceState)
        aMap = map.map

        val lan = activity.intent.getDoubleExtra(ARouterConstants.PARAM.MAP.LATITUDE, 0.0)
        val lon = activity.intent.getDoubleExtra(ARouterConstants.PARAM.MAP.LONGTITUDE, 0.0)
        val provider = AMapModuleImp()
        val locationPair = provider.toGDLatLng(activity, lan, lon)
        setUpMap(locationPair.first, locationPair.second)
    }

    private fun setUpMap(latitude: Double, longitude: Double) {
        aMap?.setMapLanguage(getSelectedLocale(context).language)
        val uriSettings = aMap?.uiSettings
        uriSettings?.isZoomControlsEnabled = false
        aMap?.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(p0: CameraPosition?) {

            }

            override fun onCameraChangeFinish(position: CameraPosition?) {
                if (position != null) {
                    val converter = CoordinateConverter.isAMapDataAvailable(position.target.latitude, position.target.longitude)
                    if (!converter && !hasGoogleWall) {
                        useOMCMap()
                        hasGoogleWall = true
                    } else if (converter && tileOverlay != null && tileOverlay!!.isVisible) {
                        stopUseOMCMap()
                        hasGoogleWall = false
                    }
                }
            }
        })
        aMap?.setOnMapLoadedListener {
            aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 100f))
            aMap?.addMarker(MarkerOptions().anchor(0.5f, 0.5f)
                    .icon(BitmapDescriptorFactory
                            .fromBitmap(BitmapFactory.decodeResource(
                                    resources, R.drawable.amap_drag_location_icon)))
                    .position(LatLng(latitude, longitude)))
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        map.onDestroy()
        super.onDestroy()
    }

    private fun useOMCMap() {

        val url = "http://mt2.google.cn/vt/lyrs=m@167000000&hl=zh-CN&gl=cn&src=app&x=%d&y=%d&z=%d&s=Galil"  //2D Map
        if (tileOverlayOptions == null) {
            tileOverlayOptions = TileOverlayOptions().tileProvider(object : UrlTileProvider(256, 256) {
                override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                    try {
                        val mFileDirName: String
                        val mFileName: String
                        mFileDirName = String.format(Locale.US, "L%02d/", zoom + 1)
                        mFileName = String.format(Locale.US, "%s", TileXYToQuadKey(x, y, zoom))//remvoe .jpg
                        val LJ = AmeFileUploader.get(AMELogin.majorContext).MAP_DIRECTORY + mFileDirName + mFileName
                        if (BcmFileUtils.isExist(mFileDirName + mFileName)) {
                            return URL("file://" + LJ)
                        } else {
                            val filePath = String.format(Locale.US, url, x, y, zoom)
                            val mBitmap: Bitmap
                            val stream = getImageStream(filePath)
                            if (stream != null) {
                                mBitmap = getImageBitmap(stream)
                                try {
                                    saveFile(mBitmap, mFileName, mFileDirName)
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }
                            }

                            return URL(filePath)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    return null
                }
            })
            tileOverlayOptions?.diskCacheEnabled(false)
                    ?.diskCacheDir(AmeFileUploader.get(AMELogin.majorContext).MAP_DIRECTORY)
                    ?.diskCacheSize(1024000)
                    ?.memoryCacheEnabled(true)
                    ?.memCacheSize(102400)
                    ?.zIndex(-9999f)
        }
        tileOverlay = aMap?.addTileOverlay(tileOverlayOptions)
        tileOverlay?.isVisible = true
    }

    fun stopUseOMCMap() {
        tileOverlay?.remove()
        tileOverlay?.clearTileCache()
        tileOverlay?.isVisible = false
        aMap?.removecache()

    }

    private fun TileXYToQuadKey(tileX: Int, tileY: Int, levelOfDetail: Int): String {
        val quadKey = StringBuilder()
        for (i in levelOfDetail downTo 1) {
            var digit = '0'
            val mask = 1 shl i - 1
            if (tileX and mask != 0) {
                digit++
            }
            if (tileY and mask != 0) {
                digit++
                digit++
            }
            quadKey.append(digit)
        }
        return quadKey.toString()
    }

    @Throws(Exception::class)
    fun getImageStream(path: String): InputStream? {
        val url = URL(path)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5 * 1000
        conn.requestMethod = "GET"
        return if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            conn.inputStream
        } else null
    }


    fun getImageBitmap(imputStream: InputStream): Bitmap {
        var targetData: ByteArray? = null
        val bytePart = ByteArray(4096)
        while (true) {
            try {
                val readLength = imputStream.read(bytePart)
                if (readLength == -1) {
                    break
                } else {
                    val temp = ByteArray(readLength + if (targetData == null) 0 else targetData.size)
                    if (targetData != null) {
                        System.arraycopy(targetData, 0, temp, 0, targetData.size)
                        System.arraycopy(bytePart, 0, temp, targetData.size, readLength)
                    } else {
                        System.arraycopy(bytePart, 0, temp, 0, readLength)
                    }
                    targetData = temp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
        return BitmapFactory.decodeByteArray(targetData, 0, targetData!!.size)
    }

    @Throws(IOException::class)
    fun saveFile(bm: Bitmap?, fileName: String, fileDirName: String) {
        Thread(Runnable {
            try {
                if (bm != null) {
                    val dirFile = File(AmeFileUploader.get(AMELogin.majorContext).MAP_DIRECTORY + fileDirName)
                    if (!dirFile.exists()) {
                        dirFile.mkdir()
                    }
                    val myCaptureFile = File(AmeFileUploader.get(AMELogin.majorContext).MAP_DIRECTORY + fileDirName + fileName)
                    val bos = BufferedOutputStream(FileOutputStream(myCaptureFile))
                    bm.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                    bos.flush()
                    bos.close()
                }

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }).start()

    }
}