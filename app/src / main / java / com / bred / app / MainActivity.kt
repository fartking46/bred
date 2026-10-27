package com.bred.app

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class MainActivity : Activity() {

    private lateinit var url: EditText
    private lateinit var quality: Spinner
    private lateinit var go: Button
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar

    private val accent = Color.parseColor("#B9772F")
    private val muted = Color.parseColor("#8A8A8A")
    private val err = Color.parseColor("#D33333")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handleShare(intent)
        startEngine()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        root.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(112))
        })

        root.addView(TextView(this).apply {
            text = "bred"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#161616"))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })

        root.addView(TextView(this).apply {
            text = "paste a link, get the mp3"
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(24))
        })

        url = EditText(this).apply {
            hint = "https://youtube.com/watch?v=..."
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 16f
            background = rounded(Color.parseColor("#F6F6F6"), Color.parseColor("#EBEBEB"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) { convert(); true } else false
            }
        }
        root.addView(url, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        quality = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("128 kbps", "192 kbps", "320 kbps"))
            setSelection(1)
        }
        row.addView(quality, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(52)).apply { rightMargin = dp(10) })

        go = Button(this).apply {
            text = "Convert"
            isAllCaps = false
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(accent)
            isEnabled = false
            setOnClickListener { convert() }
        }
        row.addView(go, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        bar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(bar, LinearLayout.LayoutParams(dp(28), dp(28)).apply { topMargin = dp(18) })

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(status)

        setContentView(root)
    }

    private fun say(msg: String, isError: Boolean = false, busy: Boolean = false) = runOnUiThread {
        status.text = msg
        status.setTextColor(if (isError) err else muted)
        bar.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val link = Regex("https?://\\S+").find(text)?.value ?: return
        url.setText(link)
    }

    private fun startEngine() {
        say("Getting ready...", busy = true)
        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
            } catch (t: Throwable) {
                say("Couldn't start: ${t.message}", isError = true)
                return@Thread
            }
            try {
                say("Checking for updates...", busy = true)
                YoutubeDL.getInstance().updateYoutubeDL(application, YoutubeDL.UpdateChannel.STABLE)
            } catch (_: Throwable) {
            }
            runOnUiThread { go.isEnabled = true }
            say("Ready")
        }.start()
    }

    private fun convert() {
        if (!go.isEnabled) return
        val link = url.text.toString().trim()
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            say("Paste a valid link first.", isError = true)
            return
        }
        val kbps = when (quality.selectedItemPosition) { 0 -> "128K"; 2 -> "320K"; else -> "192K" }

        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(url.windowToken, 0)
        go.isEnabled = false
        say("Converting... this can take a moment.", busy = true)

        Thread {
            val tmp = File(cacheDir, "dl")
            try {
                tmp.deleteRecursively()
                tmp.mkdirs()

                val req = YoutubeDLRequest(link)
                req.addOption("--no-playlist")
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
                req.addOption("--audio-quality", kbps)
                req.addOption("-o", tmp.absolutePath + "/%(title)s.%(ext)s")
                YoutubeDL.getInstance().execute(req)

                val mp3 = tmp.listFiles { f -> f.extension.equals("mp3", true) }?.firstOrNull()
                    ?: throw RuntimeException("Conversion produced no file.")
                saveToMusic(mp3)

                runOnUiThread { url.setText("") }
                say("Saved to Music/bred: ${mp3.name}")
            } catch (t: Throwable) {
                val m = (t.message ?: "Something went wrong.").replace("ERROR: ", "")
                say(m.take(220), isError = true)
            } finally {
                tmp.deleteRecursively()
                runOnUiThread { go.isEnabled = true }
            }
        }.start()
    }

    private fun saveToMusic(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/bred")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw RuntimeException("Couldn't create the file in Music.")
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: throw RuntimeException("Couldn't write the file.")
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
