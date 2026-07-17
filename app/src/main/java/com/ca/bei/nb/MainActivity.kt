package com.ca.bei.nb

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.ca.bei.nb.ui.liquid.liquidGlassEffect
import com.ca.bei.nb.ui.liquid.vibrancy
import com.ca.bei.nb.ui.theme.BEIUltraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
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
    private const val KEY_SCHEMES = "saved_schemes_data"
    private const val KEY_ACTIVE_SCHEME_ID = "active_id"

    private fun getBootTimestamp(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    fun isDriverRunThisBoot(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return abs(getBootTimestamp() - prefs.getLong(KEY_DRIVER_RUN_BOOT, 0L)) < 2000
    }

    fun markDriverRun(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_DRIVER_RUN_BOOT, getBootTimestamp()).apply()
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
                    val (loaded, id) = AppLaunchTracker.loadAllData(context)
                    schemes.addAll(loaded)
                    activeSchemeId = id
                    isDriverInstalled = AppLaunchTracker.isDriverRunThisBoot(context)
                    withContext(Dispatchers.IO) { hasRoot = AppLaunchTracker.checkRootPermission() }
                }

                LaunchedEffect(activeSchemeId) {
                    if (activeSchemeId != null) { isDriverInstalled = false }
                }

                fun triggerSave() { AppLaunchTracker.saveAllData(context, schemes.toList(), activeSchemeId) }
                val activeScheme = schemes.find { it.id == activeSchemeId }

                // 液态玻璃背景：渐变效果
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
                                    AppLaunchTracker.markDriverRun(context)
                                    isDriverInstalled = true
                                }
                                "scheme" -> SchemeScreen(schemes, activeSchemeId, 
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
    Box(
        modifier = modifier
            .vibrancy(saturation = 1.6f, contrast = 1.1f)
            .liquidGlassEffect(
                amount = 25f,
                height = 50f,
                chromaticAberration = 0.6f,
                cornerRadii = floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
            )
            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(cornerRadius.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), RoundedCornerShape(cornerRadius.dp)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun HomeScreen(isDriverInstalled: Boolean, hasRoot: Boolean, activeScheme: Scheme?, onDriverRun: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    var logText by remember { mutableStateOf("系统就绪...") }
    var activeProcess by remember { mutableStateOf<Process?>(null) }
    var showInputDialog by remember { mutableStateOf(false) }

    val version = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (ignored: Exception) { "1.0" } }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "BEI Ultra", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF1A237E))

        Row(modifier = Modifier.fillMaxWidth().offset(y = screenHeight * 0.08f)) {
            // 状态卡片使用液态玻璃效果
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
                        logText = ">>> 初始化 Shell...\n"
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

                                suspend fun runSection(uri: Uri?, inputs: String, tempName: String) {
                                    uri?.let {
                                        val tempFile = File(context.cacheDir, tempName)
                                        copyUriToFile(context, it, tempFile)
                                        Runtime.getRuntime().exec("chmod 777 ${tempFile.absolutePath}").waitFor()
                                        val isElf = isElfFile(tempFile)
                                        os.write("${if (isElf) tempFile.absolutePath else "sh " + tempFile.absolutePath}\n".toByteArray())
                                        os.flush()
                                        delay(1500)
                                        inputs.split("\n").forEach { input ->
                                            if (input.isNotBlank()) {
                                                os.write((input.trim() + "\n").toByteArray())
                                                os.flush()
                                                withContext(Dispatchers.Main) { logText += "> 自动输入: $input\n" }
                                                delay(1000)
                                            }
                                        }
                                    }
                                }

                                if (!isDriverInstalled) {
                                    runSection(activeScheme.driverUri, activeScheme.driverInputs, "temp_driver.sh")
                                    withContext(Dispatchers.Main) { onDriverRun() }
                                }

                                withContext(Dispatchers.Main) {
                                    logText += ">>> 阶段完成，启动应用...\n"
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
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { logText += "\n异常: ${e.message}" }
                                activeProcess = null
                            }
                        }
                    }
                },
                enabled = activeProcess == null,
                modifier = Modifier.weight(1f).height(70.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) { Text(if (activeProcess == null) "一键启动" else "执行中", fontSize = 24.sp) }

            if (activeProcess != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showInputDialog = true }, modifier = Modifier.size(70.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "输入")
                }
            }
        }

        // 玻璃质感日志区
        Box(modifier = Modifier.offset(y = screenHeight * 0.46f).fillMaxWidth().height(screenHeight * 0.35f)
            .vibrancy()
            .liquidGlassEffect(amount = 15f, height = 30f, chromaticAberration = 0.3f)
            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(20.dp))
            .padding(12.dp)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) { item { Text(text = logText, color = Color(0xFF37474F), fontSize = 14.sp) } }
        }
    }

    if (showInputDialog) {
        var inputVal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text("脚本交互") },
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
fun SchemeScreen(schemes: List<Scheme>, activeSchemeId: String?, onActivate: (String) -> Unit, onAddScheme: (Scheme) -> Unit, onUpdateScheme: (Scheme) -> Unit, onDeleteScheme: (String) -> Unit) {
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
                        .vibrancy(saturation = if(isActive) 1.8f else 1.2f)
                        .liquidGlassEffect(amount = if(isActive) 25f else 10f)
                        .background(if (isActive) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
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
        SchemeEditDialog(editTarget, { showEdit = false }, { if (editTarget == null) onAddScheme(it) else onUpdateScheme(it); showEdit = false })
    }
}

@Composable
fun SchemeEditDialog(initialScheme: Scheme?, onDismiss: () -> Unit, onSave: (Scheme) -> Unit) {
    var name by remember { mutableStateOf(initialScheme?.name ?: "") }
    var dIn by remember { mutableStateOf(initialScheme?.driverInputs ?: "") }
    var eIn by remember { mutableStateOf(initialScheme?.earphoneInputs ?: "") }
    var current by remember { mutableStateOf(initialScheme?.copy() ?: Scheme()) }
    var showApps by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val dPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); current = current.copy(driverUri = it, driverName = it.lastPathSegment ?: "已选") } }
    val ePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); current = current.copy(earphoneUri = it, earphoneName = it.lastPathSegment ?: "已选") } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            LazyColumn(modifier = Modifier.padding(20.dp)) {
                item { Text("配置方案", color = Color.Black, fontSize = 20.sp); Spacer(modifier = Modifier.height(10.dp)) }
                item { TextField(value = name, onValueChange = { name = it }, label = { Text("方案名称") }, modifier = Modifier.fillMaxWidth()) }
                item { SettingRow("驱动脚本", current.driverName) { dPicker.launch(arrayOf("*/*")) } }
                item { TextField(value = dIn, onValueChange = { dIn = it }, label = { Text("自动输入指令") }, modifier = Modifier.fillMaxWidth()) }
                item { SettingRow("耳机脚本", current.earphoneName) { ePicker.launch(arrayOf("*/*")) } }
                item { TextField(value = eIn, onValueChange = { eIn = it }, label = { Text("自动输入指令") }, modifier = Modifier.fillMaxWidth()) }
                item { SettingRow("目标应用", current.gameName) { showApps = true } }
                item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = { onSave(current.copy(name = name, driverInputs = dIn, earphoneInputs = eIn)) }) { Text("保存") }
                } }
            }
        }
    }
    if (showApps) { AppPickerDialog({ showApps = false }) { pkg, lbl -> current = current.copy(gamePackage = pkg, gameName = lbl); showApps = false } }
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
            .vibrancy()
            .liquidGlassEffect()
            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(30.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))) { 
            AndroidView(factory = { c -> ImageView(c).apply { setImageDrawable(c.packageManager.getApplicationIcon(c.packageName)) } }, modifier = Modifier.fillMaxSize()) 
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "BEI Ultra", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF1A237E)); Text(text = "版本 $v", color = Color.Gray)
        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.fillMaxWidth()
            .vibrancy()
            .liquidGlassEffect()
            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
            .padding(20.dp)) {
            Text(text = "BEI Ultra 由 BEI Team 开发。\n专为极客设计的自动化工具。\n反馈地址: shandian145108@qq.com", fontSize = 14.sp, color = Color.DarkGray) 
        }
    }
}

@Composable
fun BottomNavigationBar(current: String, onSel: (String) -> Unit) {
    Surface(tonalElevation = 8.dp, color = Color.White.copy(alpha = 0.8f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            NavIcon(Icons.Default.Home, "主页", current == "home") { onSel("home") }
            NavIcon(Icons.AutoMirrored.Filled.List, "方案", current == "scheme") { onSel("scheme") }
            NavIcon(Icons.Default.Info, "关于", current == "about") { onSel("about") }
        }
    }
}

@Composable
fun NavIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sel: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, label, tint = if (sel) Color(0xFF1E88E5) else Color.Gray, modifier = Modifier.size(28.dp)); Text(label, fontSize = 10.sp, color = if (sel) Color(0xFF1E88E5) else Color.Gray) } }
}

fun readTextFromUri(context: Context, uri: Uri): String {
    return try { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText().replace("\r", "") } ?: "" } catch (ignored: Exception) { "" }
}

fun copyUriToFile(context: Context, uri: Uri, targetFile: File) {
    try { context.contentResolver.openInputStream(uri)?.use { input -> targetFile.outputStream().use { output -> input.copyTo(output) } } } catch (ignored: Exception) { }
}

fun isElfFile(file: File): Boolean {
    if (!file.exists()) return false
    return try { file.inputStream().use { val h = ByteArray(4); it.read(h) == 4 && h[0] == 0x7F.toByte() && h[1] == 'E'.toByte() && h[2] == 'L'.toByte() && h[3] == 'F'.toByte() } } catch (ignored: Exception) { false }
}
