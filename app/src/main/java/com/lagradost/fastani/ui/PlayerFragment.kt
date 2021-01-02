package com.lagradost.fastani.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.animation.AnimationUtils
import com.lagradost.fastani.*
import com.lagradost.fastani.MainActivity.Companion.getViewPosDur
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.player.*
import kotlinx.android.synthetic.main.player_custom_layout.*
import java.lang.Exception
import android.view.animation.AlphaAnimation
import com.lagradost.fastani.MainActivity.Companion.getColorFromAttr
import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.content.Intent
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.DefaultEventListener
import android.content.res.Resources
import android.net.Uri
import android.preference.PreferenceManager
import android.view.*
import android.view.View.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.lagradost.fastani.MainActivity.Companion.hideSystemUI
import java.io.File
import kotlin.math.*
import androidx.core.content.ContextCompat.getSystemService as getSystemService


const val STATE_RESUME_WINDOW = "resumeWindow"
const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
const val STATE_PLAYER_PLAYING = "playerOnPlay"
const val ACTION_MEDIA_CONTROL = "media_control"
const val EXTRA_CONTROL_TYPE = "control_type"

/**
 * A simple [Fragment] subclass.
 * Use the [PlayerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

// TITLE AND URL OR CARD MUST BE PROVIDED
// EPISODE AND SEASON SHOULD START AT 0
data class PlayerData(
    val title: String?,
    val url: String?,

    var episodeIndex: Int?,
    var seasonIndex: Int?,
    val card: FastAniApi.Card?,
    val startAt: Long?,
    val anilistId: String?,
)

enum class PlayerEventType(val value: Int) {
    Stop(-1),
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),
    SkipCurrentChapter(4),
    NextEpisode(5),
    PlayPauseToggle(6)
}

class PlayerFragment(private var data: PlayerData) : Fragment() {
    companion object {
        var isInPlayer: Boolean = false
        var onLeftPlayer = Event<Boolean>()
    }

    private var isLocked = false
    private var isShowing = true
    private lateinit var exoPlayer: SimpleExoPlayer

    // private val url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var isFullscreen = false
    private var isPlayerPlaying = true
    private var currentX = 0F
    private var isMovingStartTime = 0L
    private var skipTime = 0L
    private var hasPassedSkipLimit = false
    private val settingsManager = PreferenceManager.getDefaultSharedPreferences(MainActivity.activity)
    private val swipeEnabled = settingsManager.getBoolean("swipe_enabled", true)
    private val skipOpEnabled = settingsManager.getBoolean("skip_op_enabled", true)
    var width = 0

    private fun canPlayNextEpisode(): Boolean {
        if (data.card == null || data.seasonIndex == null || data.episodeIndex == null) {
            return false
        }
        return try {
            MainActivity.canPlayNextEpisode(data.card!!, data.seasonIndex!!, data.episodeIndex!!).isFound
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentEpisode(): FastAniApi.FullEpisode {
        return data.card!!.cdnData.seasons[data.seasonIndex!!].episodes[data.episodeIndex!!]
    }

    private fun getCurrentTitle(): String {
        if (data.title != null) return data.title!!

        val isMovie: Boolean = data.card!!.episodes == 1 && data.card!!.status == "FINISHED"
        // data.card!!.cdnData.seasons.size == 1 && data.card!!.cdnData.seasons[0].episodes.size == 1
        var preTitle = ""
        if (!isMovie) {
            preTitle = "S${data.seasonIndex!! + 1}:E${data.episodeIndex!! + 1} · "
        }
        // Replaces with "" if it's null
        return preTitle + (getCurrentEpisode().title ?: "")
    }

    private fun getCurrentUrl(): String {
        println("MAN::: " + data.url)
        if (data.url != null) return data.url!!
        return getCurrentEpisode().file
    }

    fun savePos() {
        if (((data.anilistId != null
                    && data.seasonIndex != null
                    && data.episodeIndex != null) || data.card != null)
            && exoPlayer.duration > 0 && exoPlayer.currentPosition > 0
        ) {
            MainActivity.setViewPosDur(data, exoPlayer.currentPosition, exoPlayer.duration)
        }
    }

    override fun onDestroy() {
        savePos()
        // DON'T SAVE DATA OF TRAILERS

        isInPlayer = false
        onLeftPlayer.invoke(true)
        MainActivity.showSystemUI()
        MainActivity.onPlayerEvent -= ::handlePlayerEvent
        MainActivity.onAudioFocusEvent -= ::handleAudioFocusEvent

        super.onDestroy()
        //MainActivity.showSystemUI()
    }

    private fun updateLock() {
        video_locked_img.setImageResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
        video_locked_img.setColorFilter(
            if (isLocked) requireContext().getColorFromAttr(R.attr.colorPrimary)
            else Color.WHITE
        )

        val isClick = !isLocked
        exo_play.isClickable = isClick
        exo_pause.isClickable = isClick
        exo_ffwd.isClickable = isClick
        exo_prev.isClickable = isClick
        video_go_back.isClickable = isClick
        exo_progress.isClickable = isClick
        next_episode_btt.isClickable = isClick
        skip_op.isClickable = isClick

        val fadeTo = if (!isLocked) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        shadow_overlay.startAnimation(fadeAnimation)

    }

    private var receiver: BroadcastReceiver? = null
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        MainActivity.isInPIPMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
            player_holder.alpha = 0f
            receiver = object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (ACTION_MEDIA_CONTROL != intent.action) {
                        return
                    }
                    handlePlayerEvent(intent.getIntExtra(EXTRA_CONTROL_TYPE, 0))
                }
            }
            val filter = IntentFilter()
            filter.addAction(
                ACTION_MEDIA_CONTROL
            )
            activity!!.registerReceiver(receiver, filter)
            updatePIPModeActions()
        } else {
            // Restore the full-screen UI.
            player_holder.alpha = 1f
            receiver?.let {
                activity!!.unregisterReceiver(it)
            }
            hideSystemUI()
            hideKeyboard()
        }
    }

    private fun getPen(code: PlayerEventType): PendingIntent {
        return getPen(code.value)
    }

    private fun getPen(code: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            activity,
            code,
            Intent("media_control").putExtra("control_type", code),
            0
        )
    }

    private fun getRemoteAction(id: Int, title: String, event: PlayerEventType): RemoteAction {
        return RemoteAction(
            Icon.createWithResource(activity, id),
            title,
            title,
            getPen(event)
        )
    }

    private fun updatePIPModeActions() {
        if (!MainActivity.isInPIPMode) return
        val actions: ArrayList<RemoteAction> = ArrayList()

        actions.add(getRemoteAction(R.drawable.go_back_30, "Go Back", PlayerEventType.SeekBack))

        if (exoPlayer.isPlaying) {
            actions.add(getRemoteAction(R.drawable.netflix_pause, "Pause", PlayerEventType.Pause))
        } else {
            actions.add(getRemoteAction(R.drawable.netflix_play, "Play", PlayerEventType.Play))
        }

        actions.add(getRemoteAction(R.drawable.go_forward_30, "Go Forward", PlayerEventType.SeekForward))
        activity!!.setPictureInPictureParams(PictureInPictureParams.Builder().setActions(actions).build())
    }

    private fun onClickChange() {
        isShowing = !isShowing

        click_overlay.visibility = if (isShowing) GONE else VISIBLE

        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        if (!isLocked) {
            video_holder.startAnimation(fadeAnimation)
            shadow_overlay.startAnimation(fadeAnimation)
        }
        video_lock_holder.startAnimation(fadeAnimation)
    }

    private fun handleAudioFocusEvent(event: Boolean) {
        if (!event) exoPlayer.pause()
    }

    private fun handlePlayerEvent(event: PlayerEventType) {
        handlePlayerEvent(event.value)
    }

    private fun handlePlayerEvent(event: Int) {
        when (event) {
            PlayerEventType.Play.value -> exoPlayer.play()
            PlayerEventType.Pause.value -> exoPlayer.pause()
            PlayerEventType.SeekBack.value -> seekTime(-30000L)
            PlayerEventType.SeekForward.value -> seekTime(30000L)
        }
    }

    private fun forceLetters(inp: Int, letters: Int = 2): String {
        val added: Int = letters - inp.toString().length
        return if (added > 0) {
            "0".repeat(added) + inp.toString()
        } else {
            inp.toString()
        }
    }

    private fun convertTimeToString(time: Double): String {
        val sec = time.toInt()
        val rsec = sec % 60
        val min = ceil((sec - rsec) / 60.0).toInt()
        val rmin = min % 60
        val h = ceil((min - rmin) / 60.0).toInt()
        //int rh = h;// h % 24;
        return (if (h > 0) forceLetters(h) + ":" else "") + (if (rmin >= 0 || h >= 0) forceLetters(rmin) + ":" else "") + forceLetters(
            rsec
        )
    }

    private fun handleMotionEvent(motionEvent: MotionEvent) {
        // TIME_UNSET   ==   -9223372036854775807L
        // No swiping on unloaded
        // https://exoplayer.dev/doc/reference/constant-values.html
        if (isLocked || exoPlayer.duration == -9223372036854775807L || !swipeEnabled) return

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                currentX = motionEvent.rawX
                isMovingStartTime = exoPlayer.currentPosition
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = motionEvent.rawX - currentX

                val diffX = distance * 2.0 / width
                skipTime = ((exoPlayer.duration * (diffX * diffX) / 10) * (if (diffX < 0) -1 else 1)).toLong()
                if (isMovingStartTime + skipTime < 0) {
                    skipTime = -isMovingStartTime
                } else if (isMovingStartTime + skipTime > exoPlayer.duration) {
                    skipTime = exoPlayer.duration - isMovingStartTime
                }
                if (abs(skipTime) > 3000 || hasPassedSkipLimit) {
                    hasPassedSkipLimit = true
                    val timeString =
                        "${convertTimeToString((isMovingStartTime + skipTime) / 1000.0)} [${(if (abs(skipTime) < 1000) "" else (if (skipTime > 0) "+" else "-"))}${
                            convertTimeToString(abs(skipTime / 1000.0))
                        }]"
                    timeText.alpha = 1f
                    timeText.text = timeString
                } else {
                    timeText.alpha = 0f
                }
            }
            MotionEvent.ACTION_UP -> {
                hasPassedSkipLimit = false
                if (abs(skipTime) > 7000) {
                    exoPlayer.seekTo(maxOf(minOf(skipTime + isMovingStartTime, exoPlayer.duration), 0))
                }
                val fadeAnimation = AlphaAnimation(1f, 0f)

                fadeAnimation.duration = 100
                fadeAnimation.fillAfter = true

                timeText.startAnimation(fadeAnimation)

            }
        }
    }

    fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun Fragment.hideKeyboard() {
        view.let {
            if (it != null) {
                activity?.hideKeyboard(it)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        width = Resources.getSystem().displayMetrics.widthPixels
        MainActivity.onPlayerEvent += ::handlePlayerEvent
        MainActivity.onAudioFocusEvent += ::handleAudioFocusEvent

        hideKeyboard()

        updateLock()
        video_lock.setOnClickListener {
            isLocked = !isLocked
            val fadeTo = if (isLocked) 0f else 1f

            val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)
            fadeAnimation.duration = 100
            //   fadeAnimation.startOffset = 100
            fadeAnimation.fillAfter = true
            video_holder.startAnimation(fadeAnimation)

            updateLock()
        }
        /*
        player_holder.setOnTouchListener(OnTouchListener { v, event -> // ignore all touch events
            !isShowing
        })*/

        player_holder.setOnTouchListener { _: View, motionEvent: MotionEvent ->
            handleMotionEvent(motionEvent)
            return@setOnTouchListener false
        }
        click_overlay.setOnTouchListener { _: View, motionEvent: MotionEvent ->
            handleMotionEvent(motionEvent)
            return@setOnTouchListener false
        }

        click_overlay.setOnClickListener {
            onClickChange()
        }
        player_holder.setOnClickListener {
            onClickChange()
            /*if(!isShowing) {
                video_holder.postDelayed({
                    video_holder.setVisibility(View.INVISIBLE);
                    video_lock_holder.setVisibility(View.INVISIBLE);
                }, 100);
            }*/

            //isClickable WILL CAUSE UI BUG
            /*  exo_play.isClickable = isShowing

              exo_pause.isClickable = isShowing
              //exo_pause.isFocusable = isShowing
              exo_ffwd.isClickable = isShowing
              //exo_ffwd.isFocusable = isShowing
              exo_prev.isClickable = isShowing
              //exo_prev.isFocusable = isShowing
              video_lock.isClickable = isShowing
              //video_lock.isFocusable = isShowing
              video_go_back.isClickable = isShowing
              //video_go_back.isFocusable = isShowing
              exo_progress.isClickable = isShowing*/
            //  exo_progress.isFocusable = isShowing
        }

        isInPlayer = true
        retainInstance = true // OTHERWISE IT WILL CAUSE A CRASH

        video_go_back.setOnClickListener {
            MainActivity.popCurrentPage()
        }

        exo_rew.setOnClickListener {
            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew.startAnimation(rotateLeft)
            seekTime(-10000L)
        }
        exo_ffwd.setOnClickListener {
            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd.startAnimation(rotateRight)
            seekTime(10000L)
        }

        if (skipOpEnabled) {
            skip_op_holder.visibility = VISIBLE
            skip_op.setOnClickListener {
                seekTime(85000L)
            }
        }

        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
        }
    }

    private fun seekTime(time: Long) {
        exoPlayer.seekTo(maxOf(minOf(exoPlayer.currentPosition + time, exoPlayer.duration), 0))
    }

    private fun releasePlayer() {
        isPlayerPlaying = exoPlayer.playWhenReady
        playbackPosition = exoPlayer.currentPosition
        currentWindow = exoPlayer.currentWindowIndex
        exoPlayer.release()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_RESUME_WINDOW, exoPlayer.currentWindowIndex)
        outState.putLong(STATE_RESUME_POSITION, exoPlayer.currentPosition)
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, isPlayerPlaying)
        super.onSaveInstanceState(outState)
    }


    private fun initPlayer() {
        // NEEDED FOR HEADERS
        class CustomFactory : DataSource.Factory {
            override fun createDataSource(): DataSource {
                val dataSource = DefaultHttpDataSourceFactory(FastAniApi.USER_AGENT).createDataSource()
                FastAniApi.currentHeaders?.forEach {
                    dataSource.setRequestProperty(it.key, it.value)
                }
                return dataSource
            }
        }
        if (data.card != null || (data.anilistId != null && data.episodeIndex != null && data.seasonIndex != null)) {
            val pro = getViewPosDur(if (data.card != null) data.card!!.anilistId else data.anilistId!!,
                data.seasonIndex!!,
                data.episodeIndex!!)
            playbackPosition = if (pro.pos > 0 && pro.dur > 0 && (pro.pos * 100 / pro.dur) < 95) { // UNDER 95% RESUME
                pro.pos
            } else {
                0L
            }
        } else if (data.startAt != null) {
            playbackPosition = data.startAt!!
        }
        video_title.text = getCurrentTitle()
        if (canPlayNextEpisode()) {
            next_episode_btt.visibility = VISIBLE
            next_episode_btt.setOnClickListener {
                savePos()
                val next = MainActivity.canPlayNextEpisode(data.card!!, data.seasonIndex!!, data.episodeIndex!!)
                val key = MainActivity.getViewKey(
                    data.card!!.anilistId,
                    next.seasonIndex,
                    next.episodeIndex
                )
                DataStore.removeKey(VIEW_POS_KEY, key)
                DataStore.removeKey(VIEW_DUR_KEY, key)

                data.seasonIndex = next.seasonIndex
                data.episodeIndex = next.episodeIndex
                releasePlayer()
                initPlayer()
            }
        }
        // this to make the button visible in the editor
        else {
            next_episode_btt.visibility = GONE
        }
        var currentUrl = getCurrentUrl()
        val isOnline = currentUrl.startsWith("https://") || currentUrl.startsWith("http://")
        if (isOnline) {
            currentUrl = currentUrl.replace(" ", "%20")
        }
        val _mediaItem = MediaItem.Builder()
            //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
            .setMimeType(MimeTypes.APPLICATION_MP4)

        if (isOnline) {
            _mediaItem.setUri(currentUrl)
        } else {
            _mediaItem.setUri(Uri.fromFile(File(currentUrl)))
        }

        val mediaItem = _mediaItem.build()
        val trackSelector = DefaultTrackSelector(requireContext())
        // Disable subtitles
        trackSelector.parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
            .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
            .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
            .setDisabledTextTrackSelectionFlags(C.TRACK_TYPE_TEXT)
            .clearSelectionOverrides()
            .build()

        val _exoPlayer =
            SimpleExoPlayer.Builder(this.requireContext())
                .setTrackSelector(trackSelector)

        if(!isOnline) {
            _exoPlayer.setMediaSourceFactory(DefaultMediaSourceFactory(CustomFactory()))
        }

        exoPlayer = _exoPlayer.build().apply {
            playWhenReady = isPlayerPlaying
            seekTo(currentWindow, playbackPosition)
            setMediaItem(mediaItem, false)
            prepare()
        }
        exoPlayer.setHandleAudioBecomingNoisy(true) // WHEN HEADPHONES ARE PLUGGED OUT https://github.com/google/ExoPlayer/issues/7288
        player_view.player = exoPlayer

        //https://stackoverflow.com/questions/47731779/detect-pause-resume-in-exoplayer
        exoPlayer.addListener(object : DefaultEventListener() {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                updatePIPModeActions()
                if (playWhenReady && playbackState == Player.STATE_READY) {
                    MainActivity.requestAudioFocus()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        MainActivity.hideSystemUI()
        if (data.card != null) {
            val pro = getViewPosDur(data.card!!.anilistId, data.seasonIndex!!, data.episodeIndex!!)
            if (pro.pos > 0 && pro.dur > 0 && (pro.pos * 100 / pro.dur) < 95) { // UNDER 95% RESUME
                playbackPosition = pro.pos
            }
        }
        if (Util.SDK_INT > 23) {
            initPlayer()
            if (player_view != null) player_view.onResume()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23) {
            initPlayer()
            if (player_view != null) player_view.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            if (player_view != null) player_view.onPause()
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            if (player_view != null) player_view.onPause()
            releasePlayer()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.player, container, false)
    }
}