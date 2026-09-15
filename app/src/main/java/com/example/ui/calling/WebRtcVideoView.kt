package com.example.ui.calling

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.calling.CallManager
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    modifier: Modifier = Modifier,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
    mirror: Boolean = false
) {
    val context = LocalContext.current

    val surfaceViewRenderer = remember {
        SurfaceViewRenderer(context).apply {
            val eglBaseContext = CallManager.eglBaseContext
            if (eglBaseContext != null) {
                init(eglBaseContext, null)
                setScalingType(scalingType)
                setMirror(mirror)
                setEnableHardwareScaler(true)
            }
        }
    }

    DisposableEffect(videoTrack) {
        if (videoTrack != null) {
            videoTrack.addSink(surfaceViewRenderer)
        }
        onDispose {
            if (videoTrack != null) {
                videoTrack.removeSink(surfaceViewRenderer)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            surfaceViewRenderer.release()
        }
    }

    AndroidView(
        factory = {
            FrameLayout(context).apply {
                addView(surfaceViewRenderer)
            }
        },
        modifier = modifier
    )
}
