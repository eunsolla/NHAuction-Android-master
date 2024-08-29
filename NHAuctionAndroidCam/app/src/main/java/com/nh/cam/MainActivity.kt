package com.nh.cam

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.remotemonster.sdk.RemonCast
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

class MainActivity : AppCompatActivity() {

    private val viewer: SurfaceViewRenderer by lazy { findViewById(R.id.viewer) }
    private val imgPlayAndStop: AppCompatImageView by lazy { findViewById(R.id.imgPlayAndStop) }
    private val cvMic: CardView by lazy { findViewById(R.id.cvMic) }
    private val imgMic: AppCompatImageView by lazy { findViewById(R.id.imgMic) }
    private val tvMic: TextView by lazy { findViewById(R.id.tvMic) }
    private val imgSwitch: AppCompatImageView by lazy { findViewById(R.id.imgSwitch) }
    private var caster: RemonCast? = null
    private val permissionsResult = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
    }

    private fun initRemonCast(viewer: SurfaceViewRenderer): RemonCast {
        return RemonCast.Builder()
            .context(this)
            .localView(viewer)
            .serviceId(getString(R.string.kakao_live_id))
            .key(getString(R.string.kakao_live_key))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)

        imgPlayAndStop.setOnClickListener {
            if (caster == null) {
                val channelId = "${System.currentTimeMillis()}_app"
                caster = initRemonCast(viewer).apply {
                    create(channelId)
                    isDebugMode = true
                }
                imgPlayAndStop.isSelected = true
            } else {
                caster?.close()
                caster = null
                imgPlayAndStop.isSelected = false
            }
        }

        imgMic.setOnClickListener {
            if (it.isSelected) {
                // 마이크 ON
                tvMic.text = "소리끔"
                cvMic.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_FF534F4F))
                caster?.setMicMute(false)
                it.isSelected = false
            } else {
                // 마이크 OFF
                it.isSelected = true
                tvMic.text = "소리켬"
                cvMic.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
                caster?.setMicMute(true)
            }
        }

        imgSwitch.setOnClickListener {
            caster?.switchCamera()
        }

        permissionsResult.launch(
            arrayOf(
                Manifest.permission.INTERNET,
                Manifest.permission.CAMERA,
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.VIBRATE
            )
        )
    }
}