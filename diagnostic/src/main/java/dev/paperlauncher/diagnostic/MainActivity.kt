package dev.paperlauncher.diagnostic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Display
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch

/** Survives activity recreation so a fold never loses the log. Memory only: nothing is sent or stored. */
object Report {
    private const val MAX_EVENTS = 6000
    val header = ArrayList<String>()
    val events = ArrayDeque<String>()
    var dropped = 0

    fun event(text: String) {
        if (events.size >= MAX_EVENTS) { events.removeFirst(); dropped++ }
        events.addLast("${SystemClock.elapsedRealtime()} $text")
    }

    fun full(): String = buildString {
        header.forEach { appendLine(it) }
        appendLine("--- events (elapsed ms), dropped=$dropped ---")
        events.forEach { appendLine(it) }
    }

    fun tail(): String = events.toList().takeLast(150).joinToString("\n")
}

class MainActivity : ComponentActivity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var output: TextView
    private lateinit var sensors: SensorManager
    private lateinit var displays: DisplayManager
    private var hinge: Sensor? = null
    private var foldingAngle: Sensor? = null
    private var lastAngle = Float.NaN
    private var lastFolding = ""

    private val foldingListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val values = e.values.joinToString(",")
            if (values == lastFolding) return
            lastFolding = values
            val lagMs = (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1_000_000
            Report.event("FOLDING_ANGLE values=$values lagMs=$lagMs acc=${e.accuracy}")
        }
        override fun onAccuracyChanged(s: Sensor, accuracy: Int) = Unit
    }

    private val hingeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val angle = e.values[0]
            if (angle == lastAngle) return
            lastAngle = angle
            val lagMs = (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1_000_000
            Report.event("HINGE angle=$angle lagMs=$lagMs acc=${e.accuracy}")
        }
        override fun onAccuracyChanged(s: Sensor, accuracy: Int) = Unit
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = Report.event("DISPLAY added $id")
        override fun onDisplayRemoved(id: Int) = Report.event("DISPLAY removed $id")
        override fun onDisplayChanged(id: Int) = Report.event("DISPLAY changed ${describe(displays.getDisplay(id))}")
    }

    private var recording = false
    private val motionListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (recording) Report.event("MOTION ${e.sensor.name} " + e.values.joinToString(",") { "%.3f".format(it) })
        }
        override fun onAccuracyChanged(s: Sensor, accuracy: Int) = Unit
    }
    private val probeListener = object : SensorEventListener {
        private val last = HashMap<String, String>()
        override fun onSensorChanged(e: SensorEvent) {
            val v = e.values.joinToString(",")
            if (last[e.sensor.name] != v) {
                last[e.sensor.name] = v
                Report.event("PROBE ${e.sensor.name} type=${e.sensor.stringType} values=$v")
            }
        }
        override fun onAccuracyChanged(s: Sensor, accuracy: Int) = Unit
    }

    private fun isCandidate(s: Sensor): Boolean {
        val text = (s.name + " " + s.stringType).lowercase()
        return listOf("fold", "hinge", "angle", "flex", "hall", "lid", "secondary", "sub ", "second", "aux").any { it in text }
    }

    private fun startRecording() {
        if (recording) return
        recording = true
        Report.event("RECORD start, open and close the phone slowly")
        listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GRAVITY).forEach { type ->
            sensors.getSensorList(type).forEach { sensors.registerListener(motionListener, it, 20_000) }
        }
        ui.postDelayed({ stopRecording() }, 20_000)
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        sensors.unregisterListener(motionListener)
        Report.event("RECORD stop")
    }

    private val refresh = object : Runnable {
        override fun run() {
            output.text = Report.tail()
            ui.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensors = getSystemService(SensorManager::class.java)
        displays = getSystemService(DisplayManager::class.java)
        hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        foldingAngle = sensors.getSensorList(Sensor.TYPE_ALL).firstOrNull { it.stringType == "com.samsung.sensor.folding_angle" }
        setContentView(buildUi())
        if (Report.header.isEmpty()) collectHeader()
        Report.event("LIFECYCLE onCreate")
        logWindow("create")

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { info ->
                        val folds = info.displayFeatures.filterIsInstance<FoldingFeature>()
                        if (folds.isEmpty()) Report.event("FOLD none reported")
                        folds.forEach {
                            Report.event("FOLD bounds=${it.bounds} state=${it.state} orientation=${it.orientation} occlusion=${it.occlusionType} separating=${it.isSeparating}")
                        }
                    }
            }
        }
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(251, 245, 236))
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left + 24, bars.top + 12, bars.right + 24, bars.bottom + 12)
            insets
        }
        root.addView(TextView(this).apply {
            setTextColor(Color.rgb(28, 27, 25))
            textSize = 14f
            text = "1. Lay the phone flat on a table. Tap Record 20s.\n2. Keep one half on the table. Slowly lift the other half to half open and back, 3 times.\n3. Wait for RECORD stop, then tap Share and save the report as a file."
        })
        val buttons = LinearLayout(this)
        buttons.addView(Button(this).apply {
            text = "Copy report"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Paper diagnostic", Report.full()))
            }
        })
        buttons.addView(Button(this).apply {
            text = "Share"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Report.full())
                startActivity(Intent.createChooser(send, "Share report"))
            }
        })
        buttons.addView(Button(this).apply {
            text = "Record 20s"
            setOnClickListener { startRecording() }
        })
        buttons.addView(Button(this).apply {
            text = "Clear"
            setOnClickListener { Report.events.clear() }
        })
        root.addView(buttons)
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setTextColor(Color.rgb(28, 27, 25))
        }
        root.addView(
            ScrollView(this).apply { addView(output) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        return root
    }

    private fun collectHeader() {
        val h = Report.header
        h += "Paper diagnostic 0.3"
        h += "device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
        h += "android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} build=${Build.DISPLAY}"
        h += "renderEffect=${Build.VERSION.SDK_INT >= 31} runtimeShader=${Build.VERSION.SDK_INT >= 33}"
        h += "hingeSensor=" + (hinge?.let { "${it.name} vendor=${it.vendor} max=${it.maximumRange} res=${it.resolution} minDelayUs=${it.minDelay} wakeUp=${it.isWakeUpSensor} mode=${it.reportingMode}" } ?: "NOT EXPOSED")
        sensors.getSensorList(Sensor.TYPE_ALL).forEach { h += "sensor name=${it.name} type=${it.stringType} wake=${it.isWakeUpSensor} mode=${it.reportingMode} max=${it.maximumRange} res=${it.resolution}" }
        h += "foldingAngleSensor=" + (foldingAngle?.let { "${it.name} max=${it.maximumRange} res=${it.resolution} minDelayUs=${it.minDelay} wakeUp=${it.isWakeUpSensor} mode=${it.reportingMode}" } ?: "NOT EXPOSED")
        sensors.getSensorList(Sensor.TYPE_ALL)
            .filter { it.name.contains("hinge", true) || it.name.contains("fold", true) || it.stringType.contains("hinge", true) }
            .forEach { h += "relatedSensor=${it.name} type=${it.stringType}" }
        displays.displays.forEach { h += "display ${describe(it)} modes=${it.supportedModes.map { m -> "${m.physicalWidth}x${m.physicalHeight}@${m.refreshRate}" }}" }
    }

    private fun describe(d: Display?): String =
        if (d == null) "null" else "id=${d.displayId} name=${d.name} state=${d.state} hz=${d.refreshRate}"

    private fun logWindow(why: String) {
        val m = windowManager.currentWindowMetrics
        val density = resources.displayMetrics.density
        val cutout = m.windowInsets.getInsets(WindowInsets.Type.displayCutout())
        val bars = m.windowInsets.getInsets(WindowInsets.Type.systemBars())
        Report.event(
            "WINDOW $why px=${m.bounds.width()}x${m.bounds.height()} " +
                "dp=${(m.bounds.width() / density).toInt()}x${(m.bounds.height() / density).toInt()} " +
                "density=$density cutout=$cutout bars=$bars displayId=${display?.displayId}",
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logWindow("configChanged")
        Choreographer.getInstance().postFrameCallback { Report.event("FRAME first after config change") }
    }

    override fun onStart() {
        super.onStart()
        Report.event("LIFECYCLE onStart")
        logWindow("start")
        lastAngle = Float.NaN
        lastFolding = ""
        hinge?.let { sensors.registerListener(hingeListener, it, SensorManager.SENSOR_DELAY_FASTEST) }
        foldingAngle?.let { Report.event("REGISTER folding angle fastest accepted=" + sensors.registerListener(foldingListener, it, SensorManager.SENSOR_DELAY_FASTEST)) }
        sensors.getSensorList(Sensor.TYPE_ALL).filter { isCandidate(it) && it != hinge && it != foldingAngle }.forEach {
            Report.event("REGISTER ${it.name} type=${it.stringType} accepted=" + sensors.registerListener(probeListener, it, SensorManager.SENSOR_DELAY_FASTEST))
        }
        displays.registerDisplayListener(displayListener, ui)
        ui.post(refresh)
    }

    override fun onResume() { super.onResume(); Report.event("LIFECYCLE onResume") }
    override fun onPause() { Report.event("LIFECYCLE onPause"); super.onPause() }

    override fun onStop() {
        Report.event("LIFECYCLE onStop")
        sensors.unregisterListener(hingeListener)
        sensors.unregisterListener(foldingListener)
        sensors.unregisterListener(probeListener)
        stopRecording()
        displays.unregisterDisplayListener(displayListener)
        ui.removeCallbacks(refresh)
        super.onStop()
    }

    override fun onDestroy() { Report.event("LIFECYCLE onDestroy"); super.onDestroy() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Report.event("FOCUS $hasFocus")
    }
}
