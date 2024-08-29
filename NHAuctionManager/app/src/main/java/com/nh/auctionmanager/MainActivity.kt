package com.nh.auctionmanager

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import io.reactivex.rxjava3.subscribers.DisposableSubscriber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private val webView: CustomWebView by lazy { findViewById(R.id.webview) }
    private val backButtonSubject: Subject<Long> = BehaviorSubject.createDefault(0L).toSerialized()

    //[s]카메라 관련
    private lateinit var photoURI: Uri
    var valueCallback: ValueCallback<Array<Uri>>? = null
    var FILE_PROVIDER = ".fileprovider"
    var IMG_FRONT_NAME = "IMG_COW"

    //[s]퍼미션
    private val PERMISSIONS_REQUEST_CODE = 1111

    var PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA
    )


    /**
     * 권한 확인
     */
    private fun checkPermission(): Boolean {
        for (i in PERMISSIONS.indices) {
            val result = ActivityCompat.checkSelfPermission(applicationContext, PERMISSIONS[i])
            if (result != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    /**
     * 퍼미션 요청
     */
    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    /**
     * 퍼미션 결과
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {

            PERMISSIONS_REQUEST_CODE -> if (grantResults.size > 0) {

                var result = true

                grantResults.forEach {
                    if (it != PackageManager.PERMISSION_GRANTED) {
                        result = false
                        return@forEach
                    }
                }

                if (result) {
                    openChooserIntent()
                } else {
                    Toast.makeText(this, "권한을 허용해야 등록이 가능합니다.", Toast.LENGTH_SHORT).show()
                    webView.clearValueCallback()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //캐시 이미지 제거
        clearCacheDir()

        backButtonSubject.toFlowable(BackpressureStrategy.BUFFER)
            .observeOn(AndroidSchedulers.mainThread())
            .buffer(2, 1)
            .map { it[0] to it[1] }
            .subscribeWith(object : DisposableSubscriber<Pair<Long, Long>>() {
                override fun onComplete() {}
                override fun onError(t: Throwable?) {}

                override fun onNext(t: Pair<Long, Long>) {
                    val diffTime = t.second - t.first
                    if (diffTime < 2000) {
                        this@MainActivity.finishAffinity()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.str_back_press_info, Toast.LENGTH_SHORT).show()
                    }
                }
            })

        webView.loadUrl("https://www.난장.kr/office/")
    }

    /**
     * 촬영/갤러리선택 결과
     */
    private var startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        valueCallback = if (result.resultCode == RESULT_OK) {

            if (valueCallback == null) {
                return@registerForActivityResult
            }

            //RESULT_OK 이고 intent data 없으면 카메라 촬영으로 간주함.
            result?.data?.let {

                //갤러리 사진
                val dataList = it.clipData?.convertToList()

                dataList?.let {items->
                    valueCallback!!.onReceiveValue(items.toTypedArray())
                }

            } ?: run {
                // 카메라 사진
                valueCallback!!.onReceiveValue(arrayOf(photoURI))
            }

            null
        } else {
            webView.clearValueCallback()
            null
        }
    }

    /**
     * 다중 선택된 이미지  Uri 리스트 변환.
     */
    private fun ClipData.convertToList(): List<Uri> = 0.until(itemCount).map { getItemAt(it).uri }

    /**
     * 퍼미션 확인 후 chooser open
     */
    fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>?) {

        valueCallback = filePathCallback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            //권한 체크
            val isPermission: Boolean = checkPermission()

            if (!isPermission) {
                requestPermission()
            } else {
                openChooserIntent()
            }
        } else {
            openChooserIntent()
        }

    }

    /**
     * file chooser open
     */
    private fun openChooserIntent() {

        // 갤러리 Intent
        val galleryIntent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        }

        // chooser Intent
        val chooserIntent = Intent.createChooser(galleryIntent, "")

        // 카메라  Intent
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { captureIntent ->

            // package 체크
            if (captureIntent.resolveActivity(this.packageManager) != null) {

                val photoFile: File? =
                    try {
                        createImageFile()
                    } catch (ex: IOException) {
                        null
                    }

                photoFile?.also { file ->
                    runCatching {
                        val photoURI: Uri = FileProvider.getUriForFile(this@MainActivity, application.packageName + FILE_PROVIDER, file)
                        captureIntent.resolveActivity(packageManager)
                        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

                    }.onFailure {
                        it.printStackTrace()
                    }
                }

                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
            }
        }

        startForResult.launch(chooserIntent)

    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        //파일명
        val timeStamp: String = SimpleDateFormat("yyyy-MM-dd_HH_mm_ss").format(Date())
        //저장 위치
        val storageDir: File? = cacheDir.absoluteFile

        return File.createTempFile(
            "${IMG_FRONT_NAME}_${timeStamp}_",
            ".jpg",
            storageDir
        ).also { file ->
            println("tmp file path ->  ${file.path}")
            photoURI = FileProvider.getUriForFile(this@MainActivity, "com.nh.auctionmanager.fileprovider", file)
        }
    }

    /**
     * 패키지  캐시 폴더 촬영된 이미지 제거
     */
    private fun clearCacheDir() {
        val dir = cacheDir.absoluteFile
        if (dir.exists() && dir.isDirectory) {
            val childFileList: Array<File> = dir.listFiles()
            for (childFile in childFileList) {
                if (childFile.exists()) {
                    if (childFile.getName().contains(IMG_FRONT_NAME)) {
                        childFile.delete()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        backButtonSubject.onNext(System.currentTimeMillis())
    }
}