package com.nh.cowauction.ui.auction

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.viewModels
import com.nh.cowauction.BR
import com.nh.cowauction.R
import com.nh.cowauction.base.BaseFragment
import com.nh.cowauction.contants.CastState
import com.nh.cowauction.databinding.FragmentAuctionCamBinding
import com.nh.cowauction.utility.DLogger
import com.nh.cowauction.viewmodels.AuctionCamViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.video.VideoCanvas

/**
 * Description : 경매 라이브 방송 Fragment
 *
 * Created by hmju on 2021-06-09
 */
@AndroidEntryPoint
class AuctionCamFragment : BaseFragment<FragmentAuctionCamBinding, AuctionCamViewModel>() {

    override val layoutId = R.layout.fragment_auction_cam
    override val viewModel: AuctionCamViewModel by viewModels()
    override val bindingVariable = BR.viewModel

    private lateinit var container: FrameLayout
    private var surfaceView: SurfaceView? = null
    private var channelId: String = ""
    private var roomCnt: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.viewerContainer)

        with(viewModel) {

            playPosition.observe(viewLifecycleOwner) {
                DLogger.d("### playPosition :: $it position $position")

                // playPosition.value == 0 일때 list index 0 조인
                if (playPosition.value == 0) {
                    agoraJoin(channellist[playPosition.value])

                }else {
                    // playPosition.value == 0 일때 list index playPosition.value-1 조인
                    if (playPosition.value == position) {
                        agoraJoin(channellist[playPosition.value-1])
                    }
                }

            }

            // rxbus로 agora callback 받고 썸네일 클릭 시 뷰 그리는 처리
            playHandler.observe(viewLifecycleOwner) {
                if (it) {
                    setupRemoteVideo()
                }
            }

            // castState가 playing이 아닌 다른걸로 변경하여 썸네일이 노출되도록함
            thumbnailState.observe(viewLifecycleOwner) {
                DLogger.d("### STOP 썸네일 노출")
                viewModel._liveCastState.postValue(CastState.JOIN)
            }

            liveCastState.observe(viewLifecycleOwner) { state ->
                when (state) {
                    CastState.JOIN -> {
                        DLogger.d("### CastState.JOIN")

                    }
                    // agora stop하는 구문
                    CastState.STOP -> {
                        DLogger.d("### CastState.STOP")
                        stopAgoraLive()

                    }
                    // agora stop하는 구문
                    CastState.ERROR -> {
                        DLogger.d("### CastState.ERROR")
                        binding.clReady.visibility = View.VISIBLE
                        stopAgoraLive()

                    }
                    CastState.PLAYING -> {
                        DLogger.d("### CastState.PLAYING")
                    }
                }
            }

            liveSoundState.observe(viewLifecycleOwner) { isSoundOn ->
                setLiveVolume(isSoundOn)
            }

            start() // rxbus 이벤트 활성화 위해 onCreate에서 처리
            agoraLiveEventCallback() // rxbus 이벤트 활성화 위해 onCreate에서 처리
        }
    }

    /**
     * Agora engine.join 함수
     */
    private fun agoraJoin(channelId: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            options.clientRoleType = Constants.CLIENT_ROLE_AUDIENCE // 수신자
            viewModel.engine?.joinChannel("", channelId, 0, options)
            DLogger.d("### handler list : $channelId")
        }, 300)

        // 현재 화면이 1일때 playhandler true로 변경
//        if (viewModel.playPosition.value == 1) {
//            viewModel.playHandler.value = true
//        }

    }

    /**
     * 뷰 그리기
     *VideoCanvas에 뷰가 그려질 경우 CastState.JOIN -> CastState.PLAYING으로 변경
     * CastState.PLAYING일경우 썸네일 gone
     */
    private fun setupRemoteVideo() {
        // MainThread
        DLogger.d("### setupRemoteVideo 구문 탐")

        Handler(Looper.getMainLooper()).post {
            surfaceView = SurfaceView(requireContext())
            surfaceView?.setZOrderMediaOverlay(true)
            container.addView(surfaceView)
            viewModel.engine?.setupRemoteVideo(
                VideoCanvas(
                    surfaceView,
                    VideoCanvas.RENDER_MODE_ADAPTIVE,
                    viewModel.agoraChannelUid
                )
            )
            surfaceView?.visibility = View.VISIBLE
        }
        viewModel._liveCastState.postValue(CastState.PLAYING)
    }

}