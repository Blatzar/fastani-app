package com.lagradost.shiro.utils

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.*
import com.lagradost.shiro.ui.result.ResultFragment
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import kotlin.concurrent.thread


object AppApi {
    var settingsManager: SharedPreferences? = null

    fun FragmentActivity.init() {
        settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
    }

    fun unixTime(): Long {
        return System.currentTimeMillis() / 1000L
    }

    fun Context.isCastApiAvailable(): Boolean {
        val isCastApiAvailable =
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS
        try {
            applicationContext?.let { CastContext.getSharedInstance(it) }
        } catch (e: Exception) {
            // track non-fatal
            return false
        }
        return isCastApiAvailable
    }


    fun getViewKey(data: PlayerData): String {
        return getViewKey(
            data.slug,
            data.episodeIndex!!
        )
    }

    fun getViewKey(id: String, episodeIndex: Int): String {
        return id + "E" + episodeIndex
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

    fun Activity.requestAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    fun Activity.getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun Activity.changeStatusBarState(hide: Boolean): Int {
        return if (hide) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            0
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            getStatusBarHeight()
        }
    }

    fun Activity.openBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    fun getViewPosDur(aniListId: String, episodeIndex: Int): EpisodePosDurInfo {
        val key = getViewKey(aniListId, episodeIndex)

        return EpisodePosDurInfo(
            DataStore.getKey(VIEW_POS_KEY, key, -1L)!!,
            DataStore.getKey(VIEW_DUR_KEY, key, -1L)!!,
            DataStore.containsKey(VIEWSTATE_KEY, key)
        )
    }

    private fun canPlayNextEpisode(card: ShiroApi.AnimePageData?, episodeIndex: Int): NextEpisode {
        val canNext = card!!.episodes!!.size > episodeIndex + 1

        return if (canNext) {
            NextEpisode(true, episodeIndex + 1, 0)
        } else {
            NextEpisode(false, episodeIndex + 1, 0)
        }

    }

    fun getLatestSeenEpisode(data: ShiroApi.AnimePageData): NextEpisode {
        for (i in (data.episodes?.size ?: 0) downTo 0) {
            val firstPos = getViewPosDur(data.slug, i)
            if (firstPos.viewstate) {
                return NextEpisode(true, i, 0)
            }
        }
        return NextEpisode(false, 0, 0)
    }


    fun getNextEpisode(data: ShiroApi.AnimePageData): NextEpisode {
        // HANDLES THE LOGIC FOR NEXT EPISODE
        var episodeIndex = 0
        var seasonIndex = 0
        val maxValue = 90
        val firstPos = getViewPosDur(data.slug, 0)
        // Hacky but works :)
        if (((firstPos.pos * 100) / firstPos.dur <= maxValue || firstPos.pos == -1L) && !firstPos.viewstate) {
            val found = data.episodes?.getOrNull(episodeIndex) != null
            return NextEpisode(found, episodeIndex, seasonIndex)
        }

        while (true) { // IF PROGRESS IS OVER 95% CONTINUE SEARCH FOR NEXT EPISODE
            val next = canPlayNextEpisode(data, episodeIndex)
            if (next.isFound) {
                val nextPro = getViewPosDur(data.slug, next.episodeIndex)
                seasonIndex = next.seasonIndex
                episodeIndex = next.episodeIndex
                if (((nextPro.pos * 100) / nextPro.dur <= maxValue || nextPro.pos == -1L) && !nextPro.viewstate) {
                    return NextEpisode(true, episodeIndex, seasonIndex)
                }
            } else {
                val found = data.episodes?.getOrNull(episodeIndex) != null
                return NextEpisode(found, episodeIndex, seasonIndex)
            }
        }
    }


    fun setViewPosDur(data: PlayerData, pos: Long, dur: Long) {
        val key = getViewKey(data)

        if (settingsManager?.getBoolean("save_history", true) == true) {
            DataStore.setKey(VIEW_POS_KEY, key, pos)
            DataStore.setKey(VIEW_DUR_KEY, key, dur)
        }

        if (data.card == null) return

        // HANDLES THE LOGIC FOR NEXT EPISODE
        var episodeIndex = data.episodeIndex!!
        var seasonIndex = data.seasonIndex!!
        val maxValue = 90
        var canContinue: Boolean = (pos * 100 / dur) > maxValue
        var isFound = true
        var _pos = pos
        var _dur = dur

        val card = data.card
        while (canContinue) { // IF PROGRESS IS OVER 95% CONTINUE SEARCH FOR NEXT EPISODE
            val next = canPlayNextEpisode(card, episodeIndex)
            if (next.isFound) {
                val nextPro = getViewPosDur(card.slug, next.episodeIndex)
                seasonIndex = next.seasonIndex
                episodeIndex = next.episodeIndex
                if ((nextPro.pos * 100) / dur <= maxValue) {
                    _pos = nextPro.pos
                    _dur = nextPro.dur
                    canContinue = false
                    isFound = true
                }
            } else {
                canContinue = false
                isFound = false
            }
        }

        if (!isFound) return

        if (settingsManager?.getBoolean("save_history", true) == true) {
            DataStore.setKey(
                VIEW_LST_KEY,
                data.card.slug,
                LastEpisodeInfo(
                    _pos,
                    _dur,
                    System.currentTimeMillis(),
                    card,
                    card.slug,
                    episodeIndex,
                    seasonIndex,
                    data.card.episodes!!.size == 1 && data.card.status == "finished",
                    card.episodes?.get(episodeIndex),
                    card.image,
                    card.name,
                    card.banner.toString(),
                )
            )

            thread {
                ShiroApi.requestHome(true)
            }
        }
    }

    fun Activity.checkWrite(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun Activity.requestRW() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            1337
        )
    }

    fun fixCardTitle(title: String): String {
        val suffix = " Dubbed"
        return if (title.endsWith(suffix)) "✦ ${
            title.substring(
                0,
                title.length - suffix.length
            ).replace(" (Anime)", "")
        }" else title.replace(" (Anime)", "")
    }

    fun splitQuery(url: URL): Map<String, String> {
        val queryPairs: MutableMap<String, String> = LinkedHashMap()
        val query: String = url.query
        val pairs = query.split("&").toTypedArray()
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            queryPairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] =
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }
        return queryPairs
    }

    fun FragmentActivity.popCurrentPage(isInPlayer: Boolean, isInExpandedView: Boolean, isInResults: Boolean) {
        println("POPP")
        val currentFragment = supportFragmentManager.fragments.last {
            it.isVisible
        }

        if (settingsManager?.getBoolean("force_landscape", false) == true) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // No fucked animations leaving the player :)
        when {
            isInPlayer -> {
                supportFragmentManager.beginTransaction()
                    //.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
            }
            isInExpandedView && !isInResults -> {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.enter_from_right,
                        R.anim.exit_to_right,
                        R.anim.pop_enter,
                        R.anim.pop_exit
                    )
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
            }
            else -> {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                    .remove(currentFragment)
                    .commitAllowingStateLoss()
            }
        }
    }

    fun Activity.hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    fun String.md5(): String {
        return hashString(this, "MD5")
    }

    fun String.sha256(): String {
        return hashString(this, "SHA-256")
    }

    private fun hashString(input: String, algorithm: String): String {
        return MessageDigest
            .getInstance(algorithm)
            .digest(input.toByteArray())
            .fold("", { str, it -> str + "%02x".format(it) })
    }

    // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
    fun Activity.showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

    }

    fun FragmentActivity.loadPlayer(episodeIndex: Int, startAt: Long, card: ShiroApi.AnimePageData) {
        loadPlayer(
            PlayerData(
                null, null,
                episodeIndex,
                0,
                card,
                startAt,
                card.slug
            )
        )
    }

    /*fun loadPlayer(pageData: FastAniApi.AnimePageData, episodeIndex: Int, startAt: Long?) {
        loadPlayer(PlayerData("${pageData.name} - Episode ${episodeIndex + 1}", null, episodeIndex, null, null, startAt, null, true))
    }
    fun loadPlayer(title: String?, url: String, startAt: Long?) {
        loadPlayer(PlayerData(title, url, null, null, null, startAt, null))
    }*/

    fun FragmentActivity.loadPlayer(data: PlayerData) {
        this.supportFragmentManager?.beginTransaction()
            .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
            .add(
                R.id.videoRoot, PlayerFragment.newInstance(
                    data
                )
            )
            .commitAllowingStateLoss()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }

    fun FragmentActivity.loadPage(card: ShiroApi.AnimePageData) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
            .add(R.id.homeRoot, ResultFragment.newInstance(card.slug))
            .commitAllowingStateLoss()
        /*
        activity?.runOnUiThread {
            val _navController = Navigation.findNavController(activity!!, R.id.nav_host_fragment)
            _navController?.navigateUp()
            _navController?.navigate(R.layout.fragment_results,null,null)
        }
*/
        // NavigationUI.navigateUp(navController!!,R.layout.fragment_results)
    }

    @ColorInt
    fun Context.getColorFromAttr(
        @AttrRes attrColor: Int,
        typedValue: TypedValue = TypedValue(),
        resolveRefs: Boolean = true,
    ): Int {
        theme.resolveAttribute(attrColor, typedValue, resolveRefs)
        return typedValue.data
    }

    fun shouldShowPIPMode(isInPlayer: Boolean): Boolean {
        return settingsManager?.getBoolean("pip_enabled", true) ?: true && isInPlayer
    }

    fun Context.hasPIPPermission(): Boolean {
        val appOps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        } else {
            return false
        }
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }


}