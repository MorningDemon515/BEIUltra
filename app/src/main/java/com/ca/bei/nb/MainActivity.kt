package com.ca.bei.nb

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ca.bei.nb.ui.liquid.liquidGlassEffect
import com.ca.bei.nb.ui.liquid.vibrancy
import com.ca.bei.nb.ui.theme.BEIUltraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.math.abs

// ============================================================
//  数据模型
// ============================================================

data class Scheme(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var driverUri: Uri? = null,
    var driverName: String = "未选择",
    var driverInputs: String = "",
    var earphoneUri: Uri? = null,
    var earphoneName: String = "未选择",
    var earphoneInputs: String = "",
    var gamePackage: String? = null,
    var gameName: String = "未选择"
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("driverUri", driverUri?.toString() ?: "")
        json.put("driverName", driverName)
        json.put("driverInputs", driverInputs)
        json.put("earphoneUri", earphoneUri?.toString() ?: "")
        json.put("earphoneName", earphoneName)
        json.put("earphoneInputs", earphoneInputs)
        json.put("gamePackage", gamePackage ?: "")
        json.put("gameName", gameName)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): Scheme {
            val json = JSONObject(jsonStr)
            val dUri = json.optString("driverUri", "")
            val eUri = json.optString("earphoneUri", "")
            return Scheme(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "未命名方案"),
                driverUri = if (dUri.isNotEmpty()) Uri.parse(dUri) else null,
                driverName = json.optString("driverName", "未选择"),
                driverInputs = json.optString("driverInputs", ""),
                earphoneUri = if (eUri.isNotEmpty()) Uri.parse(eUri) else null,
                earphoneName = json.optString("earphoneName", "未选择"),
                earphoneInputs = json.optString("earphoneInputs", ""),
                gamePackage = json.optString("gamePackage", "").takeIf { it.isNotEmpty() },
                gameName = json.optString("gameName", "未选择")
            )
        }
    }
}

// ============================================================
//  持久化 & 状态跟踪
// ============================================================

object AppLaunchTracker {
    private const val PREF_NAME = "app_launch_prefs_v12"
    private const val KEY_DRIVER_RUN_BOOT = "driver_run_boot_time"
    private const val KEY_DRIVER_SCHEME_ID = "driver_scheme_id"
    private const val KEY_SCHEMES = "saved_schemes_data"
    private const val KEY_ACTIVE_SCHEME_ID = "active_id"

    private fun getBootTimestamp(): Long =
        System.currentTimeMillis() - SystemClock.elapsedRealtime()

    fun isDriverRunThisBoot(context: Context, currentSchemeId: String?): Boolean {
        if (currentSchemeId == null) return false
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedBootTime = prefs.getLong(KEY_DRIVER_RUN_BOOT, 0L)
        val savedSchemeId = prefs.getString(KEY_DRIVER_SCHEME_ID, null)
        return abs(getBootTimestamp() - savedBootTime) < 3000 && savedSchemeId == currentSchemeId
    }

    fun markDriverRun(context: Context, schemeId: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_DRIVER_RUN_BOOT, getBootTimestamp())
            .putString(KEY_DRIVER_SCHEME_ID, schemeId)
            .apply()
    }

    fun saveAllData(context: Context, schemes: List<Scheme>, activeId: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        schemes.forEach { array.put(it.toJson()) }
        prefs.edit()
            .putString(KEY_SCHEMES, array.toString())
            .putString(KEY_ACTIVE_SCHEME_ID, activeId ?: "")
            .apply()
    }

    fun loadAllData(context: Context): Pair<List<Scheme>, String?> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SCHEMES, null)
        val activeId = prefs.getString(KEY_ACTIVE_SCHEME_ID, null)?.takeIf { it.isNotEmpty() }
        val list = mutableListOf<Scheme>()
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) list.add(Scheme.fromJson(array.getString(i)))
            } catch (_: Exception) {
            }
        }
        return list to activeId
    }

    fun checkRootPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.use { it.write("exit\n".toByteArray()) }
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}

// ============================================================
//  Shell 会话封装
// ============================================================

class ShellSession(private val useRoot: Boolean) {
    private val lock = Any()
    private var process: Process? = null
    private var stdin: BufferedWriter? = null

    @Volatile
    var isAlive: Boolean = false
        private set

    fun start(): Boolean {
        return try {
            val command = if (useRoot) arrayOf("su") else arrayOf("sh")
            val p = ProcessBuilder(*command)
                .redirectErrorStream(false)
                .start()
            process = p
            stdin = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
            isAlive = true
            true
        } catch (_: Exception) {
            process = null
            stdin = null
            isAlive = false
            false
        }
    }

    fun send(command: String) {
        synchronized(lock) {
            if (!isAlive) return
            try {
                stdin?.apply {
                    write(command)
                    if (!command.endsWith("\n")) newLine()
                    flush()
                }
            } catch (_: Exception) {
                isAlive = false
            }
        }
    }

    fun inputStream() = process?.inputStream
    fun errorStream() = process?.errorStream

    /** 尝试优雅退出，超时后强制销毁。 */
    fun close() {
        synchronized(lock) {
            val p = process ?: return
            try {
                stdin?.write("exit\n")
                stdin?.flush()
            } catch (_: Exception) {
            }
            try {
                if (!p.waitFor(2000, TimeUnit.MILLISECONDS)) p.destroy()
            } catch (_: Exception) {
                try { p.destroy() } catch (_: Exception) {}
            }
            try { stdin?.close() } catch (_: Exception) {}
            process = null
            stdin = null
            isAlive = false
        }
    }

    companion object {
        /** 用于检测系统是否已经 root，且授予了当前 App 权限。 */
        fun probeRoot(): Boolean = AppLaunchTracker.checkRootPermission()
    }
}

// ============================================================
//  应用启动器（分层降级）
// ============================================================

enum class LaunchResult { SUCCESS, FALLBACK, FAILED }

object AppLauncher {

    suspend fun launch(
        context: Context,
        packageName: String,
        session: ShellSession?,
        useRoot: Boolean,
        log: (String) -> Unit
    ): LaunchResult {
        if (packageName.isBlank()) {
            log(">>> 包名为空\n")
            return LaunchResult.FAILED
        }

        // ========== 通道 0：解冻/启用（针对被冰箱/黑域冻结的应用）==========
        if (session?.isAlive == true) {
            session.send("pm enable $packageName 2>/dev/null")
            delay(200)
        }

        // ========== 通道 1：PackageManager 意图 ==========
        val pmOk = tryLaunchByPackageManager(context, packageName, log)
        if (pmOk) return LaunchResult.SUCCESS

        if (session == null || !session.isAlive) {
            log(">>> 无可用 Shell 会话，无法兜底\n")
            return LaunchResult.FAILED
        }

        // ========== 通道 2：解析组件 + am start -n ==========
        val component = resolveLauncherComponent(packageName, useRoot)
        if (!component.isNullOrBlank()) {
            log(">>> [通道2] 解析到组件: $component\n")
            // 关键：--user 0 + -a/-c 三重保险，避免部分 ROM 只认 action/category
            session.send(
                "am start --user 0 " +
                        "-a android.intent.action.MAIN " +
                        "-c android.intent.category.LAUNCHER " +
                        "-n '$component'"
            )
            delay(1500)

            // 二次校验：如果没起来，说明是 Root 检测秒退，直接给用户提示
            if (!isProcessRunning(context, packageName)) {
                log(">>> [通道2] am start 已发出，但进程未出现（可能被 Root 检测终止）\n")
            }
            return LaunchResult.FALLBACK
        }
        log(">>> [通道2] resolve-activity 未返回组件\n")

        // ========== 通道 3：monkey 兜底 ==========
        log(">>> [通道3] 使用 monkey 兜底\n")
        session.send("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        delay(1500)
        return LaunchResult.FALLBACK
    }

    /** 通道 1 独立函数，方便加日志。 */
    private fun tryLaunchByPackageManager(
        context: Context,
        packageName: String,
        log: (String) -> Unit
    ): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                log(">>> [通道1] getLaunchIntentForPackage 返回 null\n")
                return false
            }
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            context.startActivity(intent)
            log(">>> [通道1] 系统意图启动成功\n")
            true
        } catch (e: Exception) {
            log(">>> [通道1] 异常: ${e.message}\n")
            false
        }
    }

    /**
     * 通过独立的短生命周期 shell 解析启动组件。
     * 用独立进程是因为主 ShellSession 的 stdout 已被日志协程消费，
     * 再往里写命令会污染输出流，无法可靠读取返回值。
     */
    private fun resolveLauncherComponent(packageName: String, useRoot: Boolean): String? {
        // cmd package 是 Android 8+ 提供的稳定入口，比 dumpsys 输出更好解析
        val cmd = "cmd package resolve-activity --brief " +
                "-c android.intent.category.LAUNCHER $packageName"
        return try {
            val proc = if (useRoot) {
                ProcessBuilder("su", "-c", cmd).start()
            } else {
                ProcessBuilder("sh", "-c", cmd).start()
            }
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor()

            // 输出最后一行一般是 "com.pkg/.MainActivity"
            // 过滤掉 "No activity found" 之类的错误行
            out.lineSequence()
                .map { it.trim() }
                .lastOrNull { it.contains("/") && !it.contains(" ") && !it.startsWith("Error") }
        } catch (_: Exception) {
            null
        }
    }

    /** 通过 ActivityManager 判断进程是否存活——用于诊断 Root 检测秒退。 */
    private fun isProcessRunning(context: Context, packageName: String): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            am.runningAppProcesses?.any { it.processName == packageName } == true
        } catch (_: Exception) {
            // Android 10+ 对普通应用屏蔽了 runningAppProcesses，返回 true 避免误报
            true
        }
    }
}

// ============================================================
//  脚本执行工具
// ============================================================

/**
 * 将 Uri 指向的脚本文件落地到本地缓存，并送入 shell 执行。
 * 失败时返回 false 并短路后续流程。
 */
suspend fun executeScript(
    context: Context,
    session: ShellSession,
    uri: Uri,
    inputs: String,
    tempName: String,
    useRoot: Boolean,
    log: suspend (String) -> Unit
): Boolean {
    val cacheFile = File(context.cacheDir, tempName)

    if (!copyAndSanitizeUriToFile(context, uri, cacheFile)) {
        log(">>> 错误: 无法读取或复制文件 $tempName\n")
        return false
    }

    val execPath: String
    if (useRoot) {
        val tmpPath = "/data/local/tmp/$tempName"
        session.send("cp -f '${cacheFile.absolutePath}' '$tmpPath'")
        session.send("chmod 777 '$tmpPath'")
        execPath = tmpPath
    } else {
        cacheFile.setExecutable(true, false)
        execPath = cacheFile.absolutePath
    }

    val isElf = isElfFile(cacheFile)
    val commandLine = if (isElf) "'$execPath'" else "sh '$execPath'"

    log(">>> 执行脚本: $commandLine\n")
    session.send(commandLine)

    // 给脚本一点初始化时间，再喂自动输入
    delay(1500)

    if (inputs.isNotBlank()) {
        inputs.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                session.send(line)
                log("> 自动输入: $line\n")
                delay(800)
            }
    }
    return true
}

// ============================================================
//  MainActivity
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BEIUltraTheme(darkTheme = false) {
                val context = LocalContext.current
                var currentScreen by remember { mutableStateOf("home") }
                val schemes = remember { mutableStateListOf<Scheme>() }
                var activeSchemeId by remember { mutableStateOf<String?>(null) }
                var hasRoot by remember { mutableStateOf(false) }
                var isDriverInstalled by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (!Environment.isExternalStorageManager()) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    val (loaded, id) = AppLaunchTracker.loadAllData(context)
                    schemes.addAll(loaded)
                    activeSchemeId = id
                    isDriverInstalled = AppLaunchTracker.isDriverRunThisBoot(context, id)
                    withContext(Dispatchers.IO) {
                        hasRoot = ShellSession.probeRoot()
                    }
                }

                LaunchedEffect(activeSchemeId) {
                    isDriverInstalled = AppLaunchTracker.isDriverRunThisBoot(context, activeSchemeId)
                }

                fun triggerSave() {
                    AppLaunchTracker.saveAllData(context, schemes.toList(), activeSchemeId)
                }

                val activeScheme = schemes.find { it.id == activeSchemeId }

                val backgroundBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFFE3F2FD), Color(0xFFF3E5F5))
                )

                Surface(
                    modifier = Modifier.fillMaxSize().background(backgroundBrush),
                    color = Color.Transparent
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        bottomBar = { BottomNavigationBar(currentScreen) { currentScreen = it } }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentScreen) {
                                "home" -> HomeScreen(
                                    isDriverInstalled = isDriverInstalled,
                                    hasRoot = hasRoot,
                                    activeScheme = activeScheme,
                                    onDriverRun = {
                                        AppLaunchTracker.markDriverRun(context, activeScheme?.id)
                                        isDriverInstalled = true
                                    }
                                )
                                "scheme" -> SchemeScreen(
                                    schemes = schemes,
                                    activeSchemeId = activeSchemeId,
                                    hasRoot = hasRoot,
                                    onActivate = { activeSchemeId = it; triggerSave() },
                                    onAddScheme = { schemes.add(it); triggerSave() },
                                    onUpdateScheme = { updated ->
                                        val idx = schemes.indexOfFirst { it.id == updated.id }
                                        if (idx != -1) {
                                            schemes[idx] = updated
                                            triggerSave()
                                        }
                                    },
                                    onDeleteScheme = { id ->
                                        schemes.removeAll { it.id == id }
                                        if (activeSchemeId == id) activeSchemeId = null
                                        triggerSave()
                                    }
                                )
                                "about" -> AboutScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  首页
// ============================================================

@Composable
fun HomeScreen(
    isDriverInstalled: Boolean,
    hasRoot: Boolean,
    activeScheme: Scheme?,
    onDriverRun: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    var logText by remember { mutableStateOf("系统就绪...\n") }
    var activeSession by remember { mutableStateOf<ShellSession?>(null) }
    var showInputDialog by remember { mutableStateOf(false) }

    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    fun appendLog(text: String) {
        logText += text
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "BEI Ultra",
            style = MaterialTheme.typography.headlineLarge,
            color = Color(0xFF1A237E)
        )

        Row(modifier = Modifier.fillMaxWidth().offset(y = screenHeight * 0.08f)) {
            GlassBox(modifier = Modifier.size(screenWidth / 2 - 10.dp, screenHeight * 0.25f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (hasRoot) "工作中<ROOT>" else "工作中<NORMAL>",
                        color = if (hasRoot) Color(0xFF1B5E20) else Color(0xFF37474F),
                        fontSize = 18.sp
                    )
                    Text("版本: $version", color = Color.DarkGray)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                GlassBox(
                    modifier = Modifier.size(
                        screenWidth / 2 - 10.dp,
                        screenHeight * 0.25f * 0.5f - 5.dp
                    )
                ) {
                    Text(
                        if (isDriverInstalled) "驱动: 已就绪" else "驱动: 未安装",
                        color = if (isDriverInstalled) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                GlassBox(
                    modifier = Modifier.size(
                        screenWidth / 2 - 10.dp,
                        screenHeight * 0.25f * 0.5f - 5.dp
                    )
                ) {
                    Text(
                        activeScheme?.name ?: "未选方案",
                        color = Color(0xFF1565C0),
                        maxLines = 1
                    )
                }
            }
        }

        // ------------------------------------------------------------
        // 一键启动区域
        // ------------------------------------------------------------
        Row(modifier = Modifier.fillMaxWidth().offset(y = screenHeight * 0.35f)) {
            Button(
                onClick = {
                    if (activeScheme == null) {
                        logText += ">>> 尚未选择方案，请先前往方案页配置\n"
                        return@Button
                    }

                    scope.launch {
                        logText = ">>> 开始执行方案...\n"

                        val session = ShellSession(hasRoot)
                        if (!session.start()) {
                            logText += ">>> 错误: 无法启动 Shell 会话\n"
                            return@launch
                        }
                        activeSession = session

                        // 后台收集 stdout / stderr
                        val stdoutJob = launch(Dispatchers.IO) {
                            try {
                                session.inputStream()?.bufferedReader()?.use { reader ->
                                    val buf = CharArray(2048)
                                    var n: Int
                                    while (reader.read(buf).also { n = it } != -1) {
                                        val chunk = String(buf, 0, n)
                                        withContext(Dispatchers.Main) { appendLog(chunk) }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        val stderrJob = launch(Dispatchers.IO) {
                            try {
                                session.errorStream()?.bufferedReader()?.use { reader ->
                                    val buf = CharArray(2048)
                                    var n: Int
                                    while (reader.read(buf).also { n = it } != -1) {
                                        val chunk = String(buf, 0, n)
                                        withContext(Dispatchers.Main) { appendLog(chunk) }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        withContext(Dispatchers.IO) {
                            try {
                                // -------- 阶段 1：驱动脚本 --------
                                if (!isDriverInstalled && activeScheme.driverUri != null) {
                                    val ok = executeScript(
                                        context = context,
                                        session = session,
                                        uri = activeScheme.driverUri!!,
                                        inputs = activeScheme.driverInputs,
                                        tempName = "temp_driver.sh",
                                        useRoot = hasRoot
                                    ) { text -> withContext(Dispatchers.Main) { appendLog(text) } }

                                    if (!ok) {
                                        withContext(Dispatchers.Main) {
                                            appendLog(">>> 驱动文件异常，终止流程...\n")
                                        }
                                        return@withContext
                                    }
                                    withContext(Dispatchers.Main) { onDriverRun() }
                                } else if (activeScheme.driverUri == null) {
                                    withContext(Dispatchers.Main) {
                                        appendLog(">>> 未配置驱动脚本，直接进入下一步...\n")
                                    }
                                }

                                // -------- 阶段 2：启动目标应用 --------
                                withContext(Dispatchers.Main) {
                                    appendLog(">>> 驱动阶段完成，启动目标应用...\n")
                                }
                                activeScheme.gamePackage?.let { pkg ->
    val launchLogs = mutableListOf<String>()
    val result = AppLauncher.launch(context, pkg, session, hasRoot) { launchLogs.add(it) }
    withContext(Dispatchers.Main) {
        launchLogs.forEach { appendLog(it) }
        appendLog(
            when (result) {
                LaunchResult.SUCCESS -> ">>> 已启动 $pkg\n"
                LaunchResult.FALLBACK -> ">>> 已通过 Shell 兜底启动 $pkg\n"
                LaunchResult.FAILED -> ">>> 无法启动应用: $pkg\n"
            }
        )
    }
} ?: withContext(Dispatchers.Main) {
    appendLog(">>> 未配置目标应用，跳过启动\n")
}

                                // -------- 阶段 3：耳机脚本 --------
                                if (activeScheme.earphoneUri != null) {
                                    executeScript(
                                        context = context,
                                        session = session,
                                        uri = activeScheme.earphoneUri!!,
                                        inputs = activeScheme.earphoneInputs,
                                        tempName = "temp_ear.sh",
                                        useRoot = hasRoot
                                    ) { text -> withContext(Dispatchers.Main) { appendLog(text) } }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    appendLog(">>> 执行异常: ${e.message}\n")
                                }
                            } finally {
                                session.close()
                            }
                        }

                        // 等待输出收集完成
                        stdoutJob.join()
                        stderrJob.join()

                        withContext(Dispatchers.Main) {
                            activeSession = null
                            appendLog(">>> 执行完毕！\n")
                        }
                    }
                },
                enabled = activeSession?.isAlive != true,
                modifier = Modifier.weight(1f).height(70.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text(
                    if (activeSession?.isAlive == true) "执行中..." else "一键启动",
                    fontSize = 24.sp
                )
            }

            if (activeSession?.isAlive == true) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showInputDialog = true },
                    modifier = Modifier.size(70.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "输入")
                }
            }
        }

        // ------------------------------------------------------------
        // 日志窗口
        // ------------------------------------------------------------
        Box(
            modifier = Modifier
                .offset(y = screenHeight * 0.46f)
                .fillMaxWidth()
                .height(screenHeight * 0.35f)
                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    RoundedCornerShape(20.dp)
                )
                .padding(12.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(text = logText, color = Color(0xFF37474F), fontSize = 14.sp)
                }
            }
        }
    }

    // 手动输入对话框
    if (showInputDialog) {
        var inputVal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text("脚本交互输入") },
            text = { TextField(value = inputVal, onValueChange = { inputVal = it }) },
            confirmButton = {
                Button(onClick = {
                    val session = activeSession
                    if (session != null && session.isAlive) {
                        scope.launch(Dispatchers.IO) {
                            session.send(inputVal.trim())
                        }
                        logText += "> $inputVal\n"
                    }
                    showInputDialog = false
                }) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) { Text("取消") }
            }
        )
    }
}

// ============================================================
//  方案管理
// ============================================================

@Composable
fun SchemeScreen(
    schemes: List<Scheme>,
    activeSchemeId: String?,
    hasRoot: Boolean,
    onActivate: (String) -> Unit,
    onAddScheme: (Scheme) -> Unit,
    onUpdateScheme: (Scheme) -> Unit,
    onDeleteScheme: (String) -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Scheme?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column {
            Text(
                text = "方案管理",
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF1A237E)
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(schemes) { scheme ->
                    val isActive = scheme.id == activeSchemeId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isActive) Color(0xFFE3F2FD) else Color.White.copy(alpha = 0.6f),
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                if (isActive) BorderStroke(2.dp, Color(0xFF2196F3))
                                else BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(scheme.name, color = Color.Black, fontSize = 20.sp)
                                Text(
                                    "应用: ${scheme.gameName}",
                                    color = Color.DarkGray,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = {
                                editTarget = scheme
                                showEdit = true
                            }) {
                                Icon(Icons.Default.Edit, "编辑", tint = Color.DarkGray)
                            }
                            IconButton(onClick = { onDeleteScheme(scheme.id) }) {
                                Icon(Icons.Default.Delete, "删除", tint = Color(0xFFC62828))
                            }
                            Button(
                                onClick = { onActivate(scheme.id) },
                                enabled = !isActive
                            ) {
                                Text(if (isActive) "已启用" else "激活")
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                editTarget = null
                showEdit = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 80.dp),
            containerColor = Color(0xFF2196F3)
        ) {
            Icon(Icons.Default.Add, "添加", tint = Color.White)
        }
    }

    if (showEdit) {
        SchemeEditDialog(
            initialScheme = editTarget,
            hasRoot = hasRoot,
            onDismiss = { showEdit = false },
            onSave = {
                if (editTarget == null) onAddScheme(it) else onUpdateScheme(it)
                showEdit = false
            }
        )
    }
}

@Composable
fun SchemeEditDialog(
    initialScheme: Scheme?,
    hasRoot: Boolean,
    onDismiss: () -> Unit,
    onSave: (Scheme) -> Unit
) {
    var name by remember { mutableStateOf(initialScheme?.name ?: "") }
    var dIn by remember { mutableStateOf(initialScheme?.driverInputs ?: "") }
    var eIn by remember { mutableStateOf(initialScheme?.earphoneInputs ?: "") }
    var current by remember { mutableStateOf(initialScheme?.copy() ?: Scheme()) }
    var showApps by remember { mutableStateOf(false) }
    var showPickerFor by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .imePadding()
                .padding(vertical = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "配置方案",
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("方案名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item { SettingRow("驱动脚本", current.driverName) { showPickerFor = "driver" } }
                item {
                    OutlinedTextField(
                        value = dIn,
                        onValueChange = { dIn = it },
                        label = { Text("驱动自动输入") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
                item { SettingRow("耳机脚本", current.earphoneName) { showPickerFor = "earphone" } }
                item {
                    OutlinedTextField(
                        value = eIn,
                        onValueChange = { eIn = it },
                        label = { Text("耳机自动输入") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
                item { SettingRow("目标应用", current.gameName) { showApps = true } }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            onSave(
                                current.copy(
                                    name = name,
                                    driverInputs = dIn,
                                    earphoneInputs = eIn
                                )
                            )
                        }) { Text("保存") }
                    }
                }
            }
        }
    }

    if (showApps) {
        AppPickerDialog({ showApps = false }) { pkg, lbl ->
            current = current.copy(gamePackage = pkg, gameName = lbl)
            showApps = false
        }
    }

    if (showPickerFor != null) {
        PCFileExplorerDialog(
            hasRoot = hasRoot,
            onFileSelected = { uri, fileName ->
                current = if (showPickerFor == "driver") {
                    current.copy(driverUri = uri, driverName = fileName)
                } else {
                    current.copy(earphoneUri = uri, earphoneName = fileName)
                }
                showPickerFor = null
            },
            onDismiss = { showPickerFor = null }
        )
    }
}

// ============================================================
//  文件选择器
// ============================================================

data class ExplorerFile(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val length: Long,
    val lastModified: Long
)

@Composable
fun PCFileExplorerDialog(
    hasRoot: Boolean,
    onFileSelected: (Uri, String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialDir = if (hasRoot) "/storage/emulated/0" else "/"
    var currentPath by remember { mutableStateOf(initialDir) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    val files = remember(currentPath) {
        val currentDir = File(currentPath)
        val list = currentDir.listFiles()

        if (list != null) {
            list.map {
                ExplorerFile(
                    it.name,
                    it.absolutePath,
                    it.isDirectory,
                    it.length(),
                    it.lastModified()
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else if (hasRoot) {
            val rootFiles = mutableListOf<ExplorerFile>()
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -1p \"$currentPath\""))
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank() && line != "./" && line != "../") {
                            val isDir = line.endsWith("/")
                            val name = if (isDir) line.dropLast(1) else line
                            val absPath = if (currentPath.endsWith("/")) "$currentPath$name"
                            else "$currentPath/$name"
                            rootFiles.add(ExplorerFile(name, absPath, isDir, 0L, 0L))
                        }
                    }
                }
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            rootFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else {
            emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF3F3F3),
            border = BorderStroke(1.dp, Color(0xFFCCCCCC))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFFE0E0E0))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = Color(0xFF0078D4),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "资源管理器 - 文件选择",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onDismiss() }
                            .padding(4.dp),
                        tint = Color.DarkGray
                    )
                }

                // 工具栏
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoUp = currentPath != "/"
                    IconButton(
                        onClick = {
                            if (canGoUp) currentPath = File(currentPath).parent ?: "/"
                        },
                        enabled = canGoUp,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "向上",
                            tint = if (canGoUp) Color.Black else Color.LightGray
                        )
                    }

                    IconButton(
                        onClick = { currentPath = currentPath },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.Black)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        modifier = Modifier.weight(1f).height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFBDBDBD)),
                        color = Color(0xFFFAFAFA)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            var editingPath by remember(currentPath) { mutableStateOf(currentPath) }

                            BasicTextField(
                                value = editingPath,
                                onValueChange = { editingPath = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = Color.Black
                                ),
                                modifier = Modifier.weight(1f),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        val file = File(editingPath)
                                        if (file.absolutePath == "/" || file.isDirectory || hasRoot) {
                                            currentPath = editingPath
                                        }
                                    }
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE0E0E0))

                // 主体
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.width(110.dp).fillMaxHeight()
                            .background(Color(0xFFF0F0F0)).padding(vertical = 8.dp)
                    ) {
                        Text(
                            "快速访问",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        PCSidebarItem("内部存储", Icons.Default.SdStorage) {
                            currentPath = "/storage/emulated/0"
                        }
                        PCSidebarItem("Download", Icons.Default.Download) {
                            currentPath = "/storage/emulated/0/Download"
                        }
                        PCSidebarItem("Documents", Icons.Default.Description) {
                            currentPath = "/storage/emulated/0/Documents"
                        }
                        PCSidebarItem("根目录 /", Icons.Default.Dns) { currentPath = "/" }
                    }

                    VerticalDivider(color = Color(0xFFE0E0E0))

                    Column(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White)) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0xFFF5F5F5))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "名称",
                                modifier = Modifier.weight(2f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            Text(
                                "大小",
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            Text(
                                "修改日期",
                                modifier = Modifier.weight(1.2f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE))

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(files) { file ->
                                val isDir = file.isDirectory
                                val isSelected =
                                    selectedFile?.absolutePath == file.absolutePath

                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFFCCE8FF) else Color.Transparent
                                        )
                                        .clickable {
                                            if (isDir) {
                                                currentPath = file.absolutePath
                                                selectedFile = null
                                            } else {
                                                selectedFile = File(file.absolutePath)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(2f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                isDir -> Icons.Default.Folder
                                                file.name.endsWith(".sh") -> Icons.Default.Code
                                                else -> Icons.Default.InsertDriveFile
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                isDir -> Color(0xFFFFC107)
                                                file.name.endsWith(".sh") -> Color(0xFF4CAF50)
                                                else -> Color(0xFF9E9E9E)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            file.name,
                                            fontSize = 13.sp,
                                            color = Color.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = if (isDir) "文件夹" else formatFileSize(file.length),
                                        modifier = Modifier.weight(1f),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )

                                    Text(
                                        text = formatDate(file.lastModified),
                                        modifier = Modifier.weight(1.2f),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE0E0E0))

                // 底部状态栏
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFFF9F9F9))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (selectedFile != null) "已选择: ${selectedFile?.name}" else "请选择目标文件",
                        fontSize = 12.sp,
                        color = if (selectedFile != null) Color(0xFF0078D4) else Color.Gray,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) { Text("取消", fontSize = 12.sp) }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                selectedFile?.let {
                                    onFileSelected(Uri.fromFile(it), it.name)
                                }
                            },
                            enabled = selectedFile != null,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D4)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) { Text("选择", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun PCSidebarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF555555),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onAppSelected: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember {
        ctx.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .sortedBy { ctx.packageManager.getApplicationLabel(it).toString().lowercase() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxHeight(0.8f).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            LazyColumn {
                items(apps) { app ->
                    val lbl = ctx.packageManager.getApplicationLabel(app).toString()
                    Text(
                        text = lbl,
                        color = Color.Black,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onAppSelected(app.packageName, lbl) }
                            .padding(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Black)
        Text(value, color = Color(0xFF1E88E5), maxLines = 1)
    }
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val v = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Box(
            modifier = Modifier.size(120.dp)
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(30.dp))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    RoundedCornerShape(30.dp)
                )
                .clip(RoundedCornerShape(30.dp))
        ) {
            AndroidView(
                factory = { c ->
                    ImageView(c).apply {
                        setImageDrawable(c.packageManager.getApplicationIcon(c.packageName))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "BEI Ultra",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF1A237E)
        )
        Text(text = "版本 $v", color = Color.Gray)
        Spacer(modifier = Modifier.height(40.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Text(
                text = "BEI Ultra 由 BEI Team 开发。\n专为极客设计的自动化工具。\n反馈地址: shandian145108@qq.com",
                fontSize = 14.sp,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
fun BottomNavigationBar(current: String, onSel: (String) -> Unit) {
    var touchOffset by remember { mutableStateOf(Offset.Zero) }
    var isPressed by remember { mutableStateOf(false) }

    val animatedOffset by animateOffsetAsState(
        targetValue = touchOffset,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "glass_offset"
    )
    val animatedAmount by animateFloatAsState(
        targetValue = if (isPressed) 20f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glass_amount"
    )
    val animatedWidth by animateDpAsState(
        targetValue = if (isPressed) 140.dp else 40.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "glass_width"
    )
    val animatedHeight by animateDpAsState(
        targetValue = if (isPressed) 90.dp else 30.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "glass_height"
    )

    val density = LocalDensity.current
    val lensSizePx = with(density) { Size(animatedWidth.toPx(), animatedHeight.toPx()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .vibrancy(saturation = 1.4f, contrast = 1.05f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        if (change.pressed) {
                            isPressed = true
                            touchOffset = change.position
                        } else {
                            isPressed = false
                        }
                    }
                }
            }
            .liquidGlassEffect(
                amount = animatedAmount,
                lensSize = lensSizePx,
                lensCenter = animatedOffset,
                blurRadius = 30f,
                cornerRadii = floatArrayOf(40f, 40f, 40f, 40f)
            )
            .background(
                Color.White.copy(alpha = 0.2f),
                RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavIcon(Icons.Default.Home, "主页", current == "home") { onSel("home") }
            NavIcon(Icons.AutoMirrored.Filled.List, "方案", current == "scheme") { onSel("scheme") }
            NavIcon(Icons.Default.Info, "关于", current == "about") { onSel("about") }
        }
    }
}

@Composable
fun NavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sel: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (sel) Color(0xFF1E88E5) else Color.Gray.copy(alpha = 0.8f),
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (sel) Color(0xFF1E88E5) else Color.Gray.copy(alpha = 0.9f)
            )
        }
    }
}

// ============================================================
//  文件工具
// ============================================================

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Float = 20f,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.dp),
        color = Color.White.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

fun copyAndSanitizeUriToFile(context: Context, uri: Uri, targetFile: File): Boolean {
    return try {
        var bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) {
            return false
        }

        // 1. GZIP 魔数解压
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            try {
                bytes = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } catch (_: Exception) {
            }
        }

        // 2. ELF 直接原样写入
        val isElf = bytes.size >= 4 &&
                bytes[0] == 0x7F.toByte() &&
                bytes[1] == 'E'.toByte() &&
                bytes[2] == 'L'.toByte() &&
                bytes[3] == 'F'.toByte()

        if (isElf) {
            targetFile.writeBytes(bytes)
        } else {
            // 3. 文本判定后规范化换行
            val isText = bytes.all { b ->
                val u = b.toInt() and 0xFF
                u == 0x09 || u == 0x0A || u == 0x0D || (u in 0x20..0x7E) || u >= 0x80
            }
            if (isText) {
                val sanitized = String(bytes, Charsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                targetFile.writeText(sanitized, Charsets.UTF_8)
            } else {
                targetFile.writeBytes(bytes)
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun isElfFile(file: File): Boolean {
    if (!file.exists()) return false
    return try {
        file.inputStream().use {
            val h = ByteArray(4)
            it.read(h) == 4 &&
                    h[0] == 0x7F.toByte() &&
                    h[1] == 'E'.toByte() &&
                    h[2] == 'L'.toByte() &&
                    h[3] == 'F'.toByte()
        }
    } catch (_: Exception) {
        false
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "--"
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f KB", kb)
        else -> "$size B"
    }
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "--"
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
