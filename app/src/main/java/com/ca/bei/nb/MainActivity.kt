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
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlin.math.abs

// --- 方案数据模型 ---
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

object AppLaunchTracker {
    private const val PREF_NAME = "app_launch_prefs_v12"
    private const val KEY_DRIVER_RUN_BOOT = "driver_run_boot_time"
    private const val KEY_DRIVER_SCHEME_ID = "driver_scheme_id"
    private const val KEY_SCHEMES = "saved_schemes_data"
    private const val KEY_ACTIVE_SCHEME_ID = "active_id"

    private fun getBootTimestamp(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

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
        prefs.edit().putString(KEY_SCHEMES, array.toString()).putString(KEY_ACTIVE_SCHEME_ID, activeId ?: "").apply()
    }

    fun loadAllData(context: Context): Pair<List<Scheme>, String?> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SCHEMES, null)
        val activeId = prefs.getString(KEY_ACTIVE_SCHEME_ID, null)?.takeIf { it.isNotEmpty() }
        val list = mutableListOf<Scheme>()
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) { list.add(Scheme.fromJson(array.getString(i))) }
            } catch (ignored: Exception) { }
        }
        return list to activeId
    }

    fun checkRootPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.use { it.write("exit\n".toByteArray()) }
            process.waitFor() == 0
        } catch (ignored: Exception) { false }
    }
}

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
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        }
                    }

                    val (loaded, id) = AppLaunchTracker.loadAllData(context)
                    schemes.addAll(loaded)
                    activeSchemeId = id
                    isDriverInstalled = AppLaunchTracker.isDriverRunThisBoot(context, id)
                    withContext(Dispatchers.IO) { hasRoot = AppLaunchTracker.checkRootPermission() }
                }

                LaunchedEffect(activeSchemeId) {
                    isDriverInstalled = AppLaunchTracker.isDriverRunThisBoot(context, activeSchemeId)
                }

                fun triggerSave() { AppLaunchTracker.saveAllData(context, schemes.toList(), activeSchemeId) }
                val activeScheme = schemes.find { it.id == activeSchemeId }

                val backgroundBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFFE3F2FD), Color(0xFFF3E5F5))
                )

                Surface(modifier = Modifier.fillMaxSize().background(backgroundBrush), color = Color.Transparent) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        bottomBar = { BottomNavigationBar(currentScreen) { currentScreen = it } }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentScreen) {
                                "home" -> HomeScreen(isDriverInstalled, hasRoot, activeScheme) {
                                    AppLaunchTracker.markDriverRun(context, activeScheme?.id)
                                    isDriverInstalled = true
                                }
                                "scheme" -> SchemeScreen(schemes, activeSchemeId, hasRoot,
                                    { activeSchemeId = it; triggerSave() },
                                    { schemes.add(it); triggerSave() },
                                    { updated -> val idx = schemes.indexOfFirst { it.id == updated.id }; if(idx!=-1){schemes[idx]=updated; triggerSave()} },
                                    { id -> schemes.removeAll { it.id == id }; if(activeSchemeId==id)activeSchemeId=null; triggerSave() }
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

@Composable
fun HomeScreen(isDriverInstalled: Boolean, hasRoot: Boolean, activeScheme: Scheme?, onDriverRun: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    var logText by remember { mutableStateOf("系统就绪...\n") }
    var activeProcess by remember { mutableStateOf<Process?>(null) }
    var showInputDialog by remember { mutableStateOf(false) }

    val version = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (ignored: Exception) { "1.0" } }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "BEI Ultra", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF1A237E))

        Row(modifier = Modifier.fillMaxWidth().offset(y = screenHeight * 0.08f)) {
            GlassBox(modifier = Modifier.size(screenWidth / 2 - 10.dp, screenHeight * 0.25f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (hasRoot) "工作中<ROOT>" else "工作中<NORMAL>", color = if (hasRoot) Color(0xFF1B5E20) else Color(0xFF37474F), fontSize = 18.sp)
                    Text("版本: $version", color = Color.DarkGray)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                GlassBox(modifier = Modifier.size(screenWidth / 2 - 10.dp, screenHeight * 0.25f * 0.5f - 5.dp)) {
                    Text(if (isDriverInstalled) "驱动: 已就绪" else "驱动: 未安装", color = if (isDriverInstalled) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                }
                Spacer(modifier = Modifier.height(10.dp))
                GlassBox(modifier = Modifier.size(screenWidth / 2 - 10.dp, screenHeight * 0.25f * 0.5f - 5.dp)) {
                    Text(activeScheme?.name ?: "未选方案", color = Color(0xFF1565C0), maxLines = 1)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().offset(y = screenHeight * 0.35f)) {
            Button(
                onClick = {
                    if (activeScheme == null) return@Button
                    scope.launch {
                        logText = ">>> 开始执行方案...\n"
                        withContext(Dispatchers.IO) {
                            try {
                                val shellCmd = if (hasRoot) "su" else "sh"
                                val process = Runtime.getRuntime().exec(shellCmd)
                                activeProcess = process
                                val os = process.outputStream

                                launch {
                                    val reader = process.inputStream.bufferedReader()
                                    val buffer = CharArray(2048)
                                    var count: Int
                                    while (reader.read(buffer).also { count = it } != -1) {
                                        val str = String(buffer, 0, count)
                                        withContext(Dispatchers.Main) { logText += str }
                                    }
                                }
                                launch {
                                    val errReader = process.errorStream.bufferedReader()
                                    val buffer = CharArray(2048)
                                    var count: Int
                                    while (errReader.read(buffer).also { count = it } != -1) {
                                        val str = String(buffer, 0, count)
                                        withContext(Dispatchers.Main) { logText += str }
                                    }
                                }

                                suspend fun runSection(uri: Uri?, inputs: String, tempName: String): Boolean {
    if (uri == null) return false

    val localCacheFile = File(context.cacheDir, tempName)
    
    // 增加：接收拷贝结果，失败则阻断
    val copySuccess = copyAndSanitizeUriToFile(context, uri, localCacheFile)
    if (!copySuccess) {
        withContext(Dispatchers.Main) { logText += ">>> 错误: 无法读取或复制文件 $tempName\n" }
        return false
    }

    val execPath: String
    if (hasRoot) {
        val tmpPath = "/data/local/tmp/$tempName"
        os.write("cp -f ${localCacheFile.absolutePath} $tmpPath\n".toByteArray())
        os.write("chmod 777 $tmpPath\n".toByteArray())
        os.flush()
        execPath = tmpPath
    } else {
        Runtime.getRuntime().exec("chmod 777 ${localCacheFile.absolutePath}").waitFor()
        execPath = localCacheFile.absolutePath
    }

    val isElf = isElfFile(localCacheFile)
    val commandLine = if (isElf) execPath else "sh $execPath"

    withContext(Dispatchers.Main) {
        logText += ">>> 执行脚本: $commandLine\n"
    }

    os.write("$commandLine\n".toByteArray())
    os.flush()
    delay(1200)

    if (inputs.isNotBlank()) {
        inputs.split("\n").forEach { input ->
            if (input.isNotBlank()) {
                os.write("${input.trim()}\n".toByteArray())
                os.flush()
                withContext(Dispatchers.Main) { logText += "> 自动输入: ${input.trim()}\n" }
                delay(800)
            }
        }
    }
    return true // 增加：执行完毕返回 true
}

// ----------------------------------------------------
// 修改原有的驱动安装判断逻辑
if (!isDriverInstalled) {
    if (activeScheme.driverUri != null) {
        val success = runSection(activeScheme.driverUri, activeScheme.driverInputs, "temp_driver.sh")
        if (success) {
            // 只有文件存在且成功移交 Shell 执行后，才标记已就绪
            withContext(Dispatchers.Main) { onDriverRun() }
        } else {
            // 失败时给出明确的 UI 日志反馈，并终止执行流程
            withContext(Dispatchers.Main) { logText += ">>> 驱动文件异常，终止流程...\n" }
            os.write("exit\n".toByteArray())
            os.flush()
            process.waitFor()
            activeProcess = null
            return@withContext
        }
    } else {
        withContext(Dispatchers.Main) { logText += ">>> 未配置驱动脚本，直接进入下一步...\n" }
    }
}

                                withContext(Dispatchers.Main) {
                                    logText += ">>> 驱动阶段完成，启动目标应用...\n"
                                    activeScheme.gamePackage?.let { pkg ->
                                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                        if (intent != null) context.startActivity(intent)
                                    }
                                }

                                runSection(activeScheme.earphoneUri, activeScheme.earphoneInputs, "temp_ear.sh")

                                os.write("exit\n".toByteArray())
                                os.flush()
                                process.waitFor()
                                activeProcess = null
                                withContext(Dispatchers.Main) { logText += ">>> 执行完毕！\n" }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { logText += "\n执行异常: ${e.message}\n" }
                                activeProcess = null
                            }
                        }
                    }
                },
                enabled = activeProcess == null,
                modifier = Modifier.weight(1f).height(70.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) { Text(if (activeProcess == null) "一键启动" else "执行中...", fontSize = 24.sp) }

            if (activeProcess != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showInputDialog = true }, modifier = Modifier.size(70.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "输入")
                }
            }
        }

        Box(modifier = Modifier.offset(y = screenHeight * 0.46f).fillMaxWidth().height(screenHeight * 0.35f)
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(20.dp))
            .padding(12.dp)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) { item { Text(text = logText, color = Color(0xFF37474F), fontSize = 14.sp) } }
        }
    }

    if (showInputDialog) {
        var inputVal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text("脚本交互输入") },
            text = { TextField(value = inputVal, onValueChange = { inputVal = it }) },
            confirmButton = {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        try { activeProcess?.outputStream?.let { it.write((inputVal.trim() + "\n").toByteArray()); it.flush() } } catch (ignored: Exception) { }
                    }
                    logText += "> $inputVal\n"
                    showInputDialog = false
                }) { Text("发送") }
            },
            dismissButton = { TextButton(onClick = { showInputDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun SchemeScreen(schemes: List<Scheme>, activeSchemeId: String?, hasRoot: Boolean, onActivate: (String) -> Unit, onAddScheme: (Scheme) -> Unit, onUpdateScheme: (Scheme) -> Unit, onDeleteScheme: (String) -> Unit) {
    var showEdit by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Scheme?>(null) }
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column {
            Text(text = "方案管理", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF1A237E))
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(schemes) { scheme ->
                    val isActive = scheme.id == activeSchemeId
                    Box(modifier = Modifier.fillMaxWidth()
                        .background(if (isActive) Color(0xFFE3F2FD) else Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .border(if (isActive) BorderStroke(2.dp, Color(0xFF2196F3)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(16.dp))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(scheme.name, color = Color.Black, fontSize = 20.sp)
                                Text("应用: ${scheme.gameName}", color = Color.DarkGray, fontSize = 12.sp)
                            }
                            IconButton(onClick = { editTarget = scheme; showEdit = true }) { Icon(Icons.Default.Edit, "编辑", tint = Color.DarkGray) }
                            IconButton(onClick = { onDeleteScheme(scheme.id) }) { Icon(Icons.Default.Delete, "删除", tint = Color(0xFFC62828)) }
                            Button(onClick = { onActivate(scheme.id) }, enabled = !isActive) {
                                Text(if (isActive) "已启用" else "激活")
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editTarget = null; showEdit = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 80.dp), containerColor = Color(0xFF2196F3)) { Icon(Icons.Default.Add, "添加", tint = Color.White) }
    }
    if (showEdit) {
        SchemeEditDialog(editTarget, hasRoot, { showEdit = false }, { if (editTarget == null) onAddScheme(it) else onUpdateScheme(it); showEdit = false })
    }
}

@Composable
fun SchemeEditDialog(initialScheme: Scheme?, hasRoot: Boolean, onDismiss: () -> Unit, onSave: (Scheme) -> Unit) {
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
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Text("配置方案", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold) }

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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onSave(current.copy(name = name, driverInputs = dIn, earphoneInputs = eIn)) }) { Text("保存") }
                    }
                }
            }
        }
    }

    if (showApps) { AppPickerDialog({ showApps = false }) { pkg, lbl -> current = current.copy(gamePackage = pkg, gameName = lbl); showApps = false } }

    if (showPickerFor != null) {
        PCFileExplorerDialog(
            hasRoot = hasRoot,
            onFileSelected = { uri, fileName ->
                if (showPickerFor == "driver") {
                    current = current.copy(driverUri = uri, driverName = fileName)
                } else {
                    current = current.copy(earphoneUri = uri, earphoneName = fileName)
                }
                showPickerFor = null
            },
            onDismiss = { showPickerFor = null }
        )
    }
}

// --- 用于统一标准文件 API 与 Root Shell 输出的文件数据类 ---
data class ExplorerFile(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val length: Long,
    val lastModified: Long
)

// --- 电脑（Windows 资源管理器）风格文件选择器 ---
@Composable
fun PCFileExplorerDialog(hasRoot: Boolean, onFileSelected: (Uri, String) -> Unit, onDismiss: () -> Unit) {
    val initialDir = if (hasRoot) "/storage/emulated/0" else "/"
    var currentPath by remember { mutableStateOf(initialDir) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    val files = remember(currentPath) {
        val currentDir = File(currentPath)
        val list = currentDir.listFiles()

        if (list != null) {
            list.map {
                ExplorerFile(it.name, it.absolutePath, it.isDirectory, it.length(), it.lastModified())
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
                            val absPath = if (currentPath.endsWith("/")) currentPath + name else "$currentPath/$name"
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
                // 1. 窗口顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = Color(0xFF0078D4), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("资源管理器 - 文件选择", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onDismiss() }.padding(4.dp), tint = Color.DarkGray)
                }

                // 2. 导航工具栏与地址栏
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoUp = currentPath != "/"
                    IconButton(
                        onClick = { if (canGoUp) currentPath = File(currentPath).parent ?: "/" },
                        enabled = canGoUp,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "向上", tint = if (canGoUp) Color.Black else Color.LightGray)
                    }

                    IconButton(
                        onClick = { currentPath = currentPath },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.Black)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 地址栏
                    Surface(
                        modifier = Modifier.weight(1f).height(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFBDBDBD)),
                        color = Color(0xFFFAFAFA)
                    ) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            var editingPath by remember(currentPath) { mutableStateOf(currentPath) }

                            BasicTextField(
                                value = editingPath,
                                onValueChange = { editingPath = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.Black),
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

                // 3. 主体内容区域
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // 左侧导航栏
                    Column(
                        modifier = Modifier.width(110.dp).fillMaxHeight().background(Color(0xFFF0F0F0)).padding(vertical = 8.dp)
                    ) {
                        Text("快速访问", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                        PCSidebarItem("内部存储", Icons.Default.SdStorage) { currentPath = "/storage/emulated/0" }
                        PCSidebarItem("Download", Icons.Default.Download) { currentPath = "/storage/emulated/0/Download" }
                        PCSidebarItem("Documents", Icons.Default.Description) { currentPath = "/storage/emulated/0/Documents" }
                        PCSidebarItem("根目录 /", Icons.Default.Dns) { currentPath = "/" }
                    }

                    VerticalDivider(color = Color(0xFFE0E0E0))

                    // 右侧文件表格视图
                    Column(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("名称", modifier = Modifier.weight(2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Text("大小", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Text("修改日期", modifier = Modifier.weight(1.2f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE))

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(files) { file ->
                                val isDir = file.isDirectory
                                val isSelected = selectedFile?.absolutePath == file.absolutePath

                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(if (isSelected) Color(0xFFCCE8FF) else Color.Transparent)
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
                                    Row(modifier = Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isDir) Icons.Default.Folder else if (file.name.endsWith(".sh")) Icons.Default.Code else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (isDir) Color(0xFFFFC107) else if (file.name.endsWith(".sh")) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(file.name, fontSize = 13.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

                // 4. 底部状态与操作栏
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF9F9F9)).padding(12.dp),
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
fun PCSidebarItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onAppSelected: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).sortedBy { ctx.packageManager.getApplicationLabel(it).toString().lowercase() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxHeight(0.8f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White) {
            LazyColumn { items(apps) { app -> val lbl = ctx.packageManager.getApplicationLabel(app).toString(); Text(text = lbl, color = Color.Black, modifier = Modifier.fillMaxWidth().clickable { onAppSelected(app.packageName, lbl) }.padding(15.dp)) } }
        }
    }
}

@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Black); Text(value, color = Color(0xFF1E88E5), maxLines = 1)
    }
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val v = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0" } catch (ignored: Exception) { "1.0" } }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.size(120.dp)
            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(30.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))) {
            AndroidView(factory = { c -> ImageView(c).apply { setImageDrawable(c.packageManager.getApplicationIcon(c.packageName)) } }, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "BEI Ultra", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF1A237E)); Text(text = "版本 $v", color = Color.Gray)
        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
            .padding(20.dp)) {
            Text(text = "BEI Ultra 由 BEI Team 开发。\n专为极客设计的自动化工具。\n反馈地址: shandian145108@qq.com", fontSize = 14.sp, color = Color.DarkGray)
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
fun NavIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sel: Boolean, onClick: () -> Unit) {
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

// 拷贝 Uri 文件至本地缓存，自动检测解压 GZIP 数据并转换换行符
fun copyAndSanitizeUriToFile(context: Context, uri: Uri, targetFile: File): Boolean {
    return try {
        var bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) {
            println("=== 读取失败: Uri 返回空数据 ===")
            return false // 增加：读取失败返回 false
        }

        // 1. 自动检测 GZIP 魔数 (0x1F, 0x8B) 并解压数据
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            try {
                bytes = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } catch (e: Exception) {
                println("=== GZIP 解压异常，使用原始字节: ${e.message} ===")
            }
        }

        // 2. 检测 ELF 可执行二进制文件 (0x7F 'E' 'L' 'F')
        val isElf = bytes.size >= 4 &&
                bytes[0] == 0x7F.toByte() &&
                bytes[1] == 'E'.toByte() &&
                bytes[2] == 'L'.toByte() &&
                bytes[3] == 'F'.toByte()

        if (isElf) {
            targetFile.writeBytes(bytes)
        } else {
            // 3. 判断是否为可读文本脚本（避免损坏非 ELF 的二进制文件）
            val isText = bytes.all { b ->
                val u = b.toInt() and 0xFF
                u == 0x09 || u == 0x0A || u == 0x0D || (u in 0x20..0x7E) || u >= 0x80
            }

            if (isText) {
                val sanitized = String(bytes, Charsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n")
                targetFile.writeText(sanitized, Charsets.UTF_8)
            } else {
                targetFile.writeBytes(bytes)
            }
        }
        true // 增加：成功写入返回 true
    } catch (e: Exception) {
        e.printStackTrace()
        println("=== 拷贝文件异常: ${e.message} ===")
        false // 增加：异常返回 false
    }
}

fun isElfFile(file: File): Boolean {
    if (!file.exists()) return false
    return try { file.inputStream().use { val h = ByteArray(4); it.read(h) == 4 && h[0] == 0x7F.toByte() && h[1] == 'E'.toByte() && h[2] == 'L'.toByte() && h[3] == 'F'.toByte() } } catch (ignored: Exception) { false }
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