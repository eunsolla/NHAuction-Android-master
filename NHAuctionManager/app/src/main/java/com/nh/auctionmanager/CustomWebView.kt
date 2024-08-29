package com.nh.auctionmanager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import androidx.core.view.NestedScrollingChild
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import com.hmju.permissions.SimplePermissions
import java.util.*

/**
 * Description :
 *
 * Created by hmju on 2021-12-06
 */
class CustomWebView @JvmOverloads constructor(
    private val ctx: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(ctx, attrs, defStyleAttr), LifecycleOwner, LifecycleObserver, NestedScrollingChild {

    interface Listener {
        fun onScroll(scrollY: Int)
    }

    private val activity: Activity by lazy { ctx as Activity }
    private val lifecycleRegistry: LifecycleRegistry by lazy { LifecycleRegistry(this) }

    private var lastY: Int = 0
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var nestedOffsetY: Int = 0
    private val childHelper: NestedScrollingChildHelper by lazy { NestedScrollingChildHelper(this) }

    private val uiHandler = Handler(Looper.getMainLooper())
    var listener: Listener? = null
    private var currProgress = 0
    private var prevScroll = 0

    var valueCallback: ValueCallback<Array<Uri>>? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
    fun onStateEvent(owner: LifecycleOwner, event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
            }
            Lifecycle.Event.ON_RESUME -> {
            }
            else -> {
            }
        }
    }

    override fun getLifecycle() = lifecycleRegistry

    init {
        setWebSetting()
        setCookie()
        isNestedScrollingEnabled = true
        webChromeClient = WebChromeClientClass()
        webViewClient = WebViewClientClass()
        addJavascriptInterface(JavaInterface(), "auctionBridge")

        if (ctx is FragmentActivity) {
            ctx.lifecycle.addObserver(this)
        }
    }

    /**
     * JAVA SCRIPT 추가
     */
    inner class JavaInterface {
        @JavascriptInterface
        fun dummyScript() {
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setWebSetting() {
        this.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setNetworkAvailable(true)
        settings.apply {
            javaScriptEnabled = true // 자바 스크립트 허용
            javaScriptCanOpenWindowsAutomatically = true // 팝업창 오픈 기능
            loadWithOverviewMode = true // 웹뷰 화면 맞춤
            useWideViewPort = true // 뷰포트 사용여부
            loadsImagesAutomatically = true // 앱 이미지 리소스 자동 로드
            domStorageEnabled = true // 로컬 스토리지 사용 여부
            loadWithOverviewMode = true // 콘텐츠가 웹뷰보다 클경우 스크린 크기에 맞게 조정.
            allowFileAccess = true
            domStorageEnabled = true

            setSupportMultipleWindows(true) // 멀티 윈도우 허용
            setGeolocationEnabled(true) // 위치 허용
            textZoom = 100
            cacheMode = WebSettings.LOAD_NO_CACHE // 캐시 거부
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // http 리소스 허용
            if (BuildConfig.DEBUG) {
                setWebContentsDebuggingEnabled(true)
            }
        }
        requestFocus()
        isFocusable = true
        isFocusableInTouchMode = true
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_UP -> {
                    if (v.hasFocus()) {
                        v.requestFocus()
                    }
                }
            }
            return@setOnTouchListener false
        }
    }

    /**
     * Setting Cookies
     */
    private fun setCookie() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@CustomWebView, true)
        }
    }

    inner class WebChromeClientClass : WebChromeClient() {
        @SuppressLint("SetJavaScriptEnabled")
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {

            if (resultMsg == null) return false

            val popupWebView = WebView(activity).apply {
                settings.javaScriptEnabled = true
            }

            val dialog = Dialog(activity).apply {
                setContentView(popupWebView)
//                window?.attributes?.also {
//                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
//                    it.height = ViewGroup.LayoutParams.MATCH_PARENT
//                }
                setOnDismissListener {
                    popupWebView.destroy()
                }
            }
            popupWebView.webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView?) {
                    dialog.dismiss()
                    window?.destroy()
                }
            }
            popupWebView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }
            }

            dialog.show()

            (resultMsg.obj as WebViewTransport).webView = popupWebView

            resultMsg.sendToTarget()

            return true
        }

        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {

            filePathCallback?.let {
                valueCallback = it
                if (activity is MainActivity) {
                    (activity as MainActivity).showFileChooser(it)
                }
            }

            return true
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            currProgress = newProgress
        }


        override fun onPermissionRequest(request: PermissionRequest?) {
            SimplePermissions(context)
                .requestPermissions(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS
                )
                .build { b, strings ->
                    Log.d("JLogger", "$b Request Permissions ${Arrays.deepToString(request?.resources)}")
                    if (b) {
                        uiHandler.post {
                            request?.grant(request.resources)
                        }
                    } else {
                        request?.deny()
                    }
                }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
            request?.let { permissionRequest ->
                Log.d("JLogger", "Request Cancel Permissions ${Arrays.deepToString(permissionRequest.resources)}")
            }
        }
    }

    fun clearValueCallback() {
        valueCallback?.let {
            it.onReceiveValue(null)
        }
        valueCallback = null
    }

    inner class WebViewClientClass : WebViewClient() {

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val url = request?.url.toString()

            return false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            CookieManager.getInstance().flush()
        }
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        var returnValue = false
        val event = MotionEvent.obtain(ev)
        val action = ev?.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }
        val eventY = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())
        when (action) {
            MotionEvent.ACTION_MOVE -> {
                var deltaY: Int = lastY - eventY
                // NestedPreScroll
                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset)) {
                    deltaY -= scrollConsumed[1]
                    lastY = eventY - scrollOffset[1]
                    event.offsetLocation(0f, -scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                }
                returnValue = super.onTouchEvent(event)

                // NestedScroll
                if (dispatchNestedScroll(0, scrollOffset[1], 0, deltaY, scrollOffset)) {
                    event.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                    lastY -= scrollOffset[1]
                }
            }
            MotionEvent.ACTION_DOWN -> {
                returnValue = super.onTouchEvent(event)
                lastY = eventY
                // start NestedScroll
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                returnValue = super.onTouchEvent(event)
                // end NestedScroll
                stopNestedScroll()
            }
        }
        return returnValue
    }

    // Nested Scroll implements
    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return childHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll() {
        childHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean {
        return childHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean {
        return childHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        prevScroll = t
        listener?.onScroll(t)
    }

}