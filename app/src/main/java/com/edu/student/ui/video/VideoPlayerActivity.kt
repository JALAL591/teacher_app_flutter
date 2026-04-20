package com.edu.student.ui.video

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.edu.teacher.databinding.StudentActivityVideoPlayerBinding
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_PATH = "video_path"
        const val EXTRA_VIDEO_TITLE = "video_title"
    }

    private lateinit var binding: StudentActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "فيديو"

        binding.toolbar.apply {
            title = videoTitle
            setNavigationOnClickListener { finish() }
        }

        if (videoPath.isNullOrEmpty()) {
            showError("مسار الفيديو فارغ")
            return
        }

        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            showError("ملف الفيديو غير موجود")
            return
        }

        initializePlayer(videoFile)
    }

    private fun initializePlayer(videoFile: File) {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer

            android.util.Log.d("VideoPlayer", "Loading video from: ${videoFile.absolutePath}")

            val uri = Uri.parse("file://${videoFile.absolutePath}")
            val mediaItem = MediaItem.fromUri(uri)

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = playWhenReady
            exoPlayer.seekTo(currentWindow, playbackPosition)
            exoPlayer.prepare()

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e("VideoPlayer", "ExoPlayer error: ${error.errorCode} - ${error.message}")
                    showError("فشل تشغيل الفيديو: ${error.message}")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            })
        }
    }

    private fun showError(message: String) {
        android.util.Log.e("VideoPlayer", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            releasePlayer()
        } else {
            player?.let {
                playbackPosition = it.currentPosition
                currentWindow = it.currentMediaItemIndex
                playWhenReady = it.playWhenReady
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}