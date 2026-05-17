package com.example.asea.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Sleek dark palette (WCAG AAA contrast ratios)
val DarkBackground = Color(0xFF0F0F10)
val DarkSurface = Color(0xFF1B1B1D)
val DarkSurfaceCard = Color(0xFF27272A)
val BrightYellow = Color(0xFFFFD60A)
val SOSCrimson = Color(0xFFFF453A)
val SOSOrange = Color(0xFFFF9F0A)
val CalmCyan = Color(0xFF64D2FF)
val CalmBlue = Color(0xFF0A84FF)
val EmeraldGreen = Color(0xFF30D158)
val DarkGreyText = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val ayarlar by viewModel.ayarlar.collectAsState()
    val ilaclar by viewModel.ilaclar.collectAsState()
    val saglikGecmisi by viewModel.saglikGecmisi.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Dashboard, 1: Medicines, 2: Settings

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.height(90.dp)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Text("🏠", fontSize = 28.sp) },
                    label = { Text("Panel", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrightYellow,
                        selectedTextColor = BrightYellow,
                        unselectedIconColor = DarkGreyText,
                        unselectedTextColor = DarkGreyText,
                        indicatorColor = DarkSurfaceCard
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Text("💊", fontSize = 28.sp) },
                    label = { Text("İlaçlar", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldGreen,
                        selectedTextColor = EmeraldGreen,
                        unselectedIconColor = DarkGreyText,
                        unselectedTextColor = DarkGreyText,
                        indicatorColor = DarkSurfaceCard
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Text("⚙️", fontSize = 28.sp) },
                    label = { Text("Ayarlar", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CalmCyan,
                        selectedTextColor = CalmCyan,
                        unselectedIconColor = DarkGreyText,
                        unselectedTextColor = DarkGreyText,
                        indicatorColor = DarkSurfaceCard
                    )
                )
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
        ) {
            when (activeTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    saglikGecmisi = saglikGecmisi
                )
                1 -> MedicinesScreen(
                    viewModel = viewModel,
                    ilaclar = ilaclar
                )
                2 -> SettingsScreen(
                    viewModel = viewModel,
                    currentSettings = ayarlar
                )
            }
        }
    }
}

// ------------------------------------------------------------------ //
// 1. Dashboard (Main Screen)
// ------------------------------------------------------------------ //
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    saglikGecmisi: List<com.example.asea.data.local.entity.SaglikGecmisiEntity>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Akıllı Erişilebilirlik Asistanı",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 🚨 1. DEVE SOS BUTTON (1.5s Long Press)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LongPressSosButton(
                onTrigger = { viewModel.tetikleAcilDurum() }
            )
        }

        // 🧘 2. DEVE CALM DOWN BUTTON (Swipe To Calm)
        Box(
            modifier = Modifier
                .height(110.dp)
                .fillMaxWidth()
        ) {
            SwipeCalmButton(
                onTrigger = { viewModel.tetikleCbtSakinlesme() }
            )
        }

        // 📝 Son Sağlık Geçmişi (Log Görünümü)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "📋 Son Aktiviteler",
                    color = BrightYellow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (saglikGecmisi.isEmpty()) {
                    Text(
                        text = "Henüz bir aktivite kaydı bulunmuyor.",
                        color = DarkGreyText,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(saglikGecmisi.take(3)) { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• [${log.kayitTipi}] ${log.icerik}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ //
// SOS Button with Progressive Loading Ring (1.5s Long Press)
// ------------------------------------------------------------------ //
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressSosButton(onTrigger: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val startTime = System.currentTimeMillis()
            while (isPressed && progress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed.toFloat() / 1500f).coerceAtMost(1f)
                delay(16) // ~60 FPS
            }
            if (progress >= 1f) {
                onTrigger()
                delay(500) // Debounce
                progress = 0f
            }
        } else {
            progress = 0f
        }
    }

    val animatedScale by animateFloatAsState(if (isPressed) 0.93f else 1f, label = "scale")
    val animatedProgressColor by animateColorAsState(
        if (progress > 0.8f) BrightYellow else SOSCrimson, label = "color"
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onLongClick = {}
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SOSCrimson,
                            SOSOrange
                        )
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background Progressive Ring
            CircularProgressIndicator(
                progress = progress,
                color = animatedProgressColor,
                strokeWidth = 14.dp,
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .clip(CircleShape)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🚨",
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ACİL SOS",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isPressed) "Bırakma! Tetikleniyor..." else "Tetiklemek için 1.5 sn basılı tut",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ------------------------------------------------------------------ //
// Calming Button using Slide/Swipe to Unlock gesture
// ------------------------------------------------------------------ //
@Composable
fun SwipeCalmButton(onTrigger: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var offsetX by remember { mutableStateOf(0f) }
    val trackWidth = 320.dp
    val handleWidth = 80.dp

    val density = LocalDensity.current
    val maxOffsetPx = with(density) { (trackWidth - handleWidth).toPx() }

    val progress = (offsetX / maxOffsetPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurfaceCard)
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track Background gradient overlay as you swipe
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            CalmBlue.copy(alpha = 0.6f),
                            CalmCyan
                        )
                    )
                )
        )

        // Text Guidance
        Text(
            text = "🧘 Sakinleşmek için Sağa Kaydır",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // Drag Handle (Slider Knob)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(width = handleWidth, height = 84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CalmCyan, CalmBlue)
                    )
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX >= maxOffsetPx * 0.8f) {
                                offsetX = maxOffsetPx
                                onTrigger()
                            }
                            // Spring back
                            coroutineScope.launch {
                                val start = offsetX
                                val steps = 15
                                for (i in 1..steps) {
                                    offsetX = start - (start * (i.toFloat() / steps.toFloat()))
                                    delay(10)
                                }
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(0f, maxOffsetPx)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "▶▶", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------ //
// 2. Medicines Screen
// ------------------------------------------------------------------ //
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicinesScreen(viewModel: MainViewModel, ilaclar: List<com.example.asea.data.local.entity.IlacTakipEntity>) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💊 İlaç Takip Paneli",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                // Large Add Button
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(55.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ekle", tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Yeni İlaç", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (ilaclar.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kayıtlı aktif bir ilacınız bulunmuyor.",
                        color = DarkGreyText,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ilaclar) { ilac ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = ilac.ilacAdi,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${ilac.dozaj} | Saat: ${ilac.hatirlatmaSaati}",
                                        color = DarkGreyText,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Massive toggle switch
                                    Switch(
                                        checked = ilac.aktifMi,
                                        onCheckedChange = { viewModel.ilacAktiflikGuncelle(ilac, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = EmeraldGreen,
                                            checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.scale(1.3f)
                                    )

                                    // Big Delete button (1.5s hold trigger to delete avoids accidental deletion!)
                                    IconButton(
                                        onClick = { viewModel.ilacSil(ilac) },
                                        modifier = Modifier
                                            .size(50.dp)
                                            .background(SOSCrimson.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Sil",
                                            tint = SOSCrimson,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Medicine Modal Dialog
        if (showAddDialog) {
            AddMedicineDialog(
                onDismiss = { showAddDialog = false },
                onSave = { ad, doz, saat ->
                    viewModel.ilacEkle(ad, doz, saat)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AddMedicineDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var ad by remember { mutableStateOf("") }
    var doz by remember { mutableStateOf("") }
    var saat by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💊 Yeni İlaç Ekle",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = ad,
                    onValueChange = { ad = it },
                    label = { Text("İlaç Adı", fontSize = 16.sp) },
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = doz,
                    onValueChange = { doz = it },
                    label = { Text("Dozaj (örn: 100mg)", fontSize = 16.sp) },
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = saat,
                    onValueChange = { saat = it },
                    label = { Text("Saat (örn: 08:00)", fontSize = 16.sp) },
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(55.dp)
                    ) {
                        Text("İptal", color = DarkGreyText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (ad.isNotBlank() && saat.isNotBlank()) {
                                onSave(ad, doz, saat)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(55.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Kaydet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ //
// 3. Settings Screen
// ------------------------------------------------------------------ //
@Composable
fun SettingsScreen(viewModel: MainViewModel, currentSettings: com.example.asea.data.local.entity.KullaniciAyarlariEntity?) {
    var wakeWord by remember { mutableStateOf("") }
    var emergencyText by remember { mutableStateOf("") }
    var emergencyContactNumber by remember { mutableStateOf("") }
    var geminiApiKey by remember { mutableStateOf("") }
    var volumeLevel by remember { mutableStateOf(100f) }
    var speechRate by remember { mutableStateOf(1.0f) }

    LaunchedEffect(currentSettings) {
        currentSettings?.let {
            wakeWord = it.wakeWord
            emergencyText = it.emergencyText
            emergencyContactNumber = it.emergencyContactNumber
            geminiApiKey = it.geminiApiKey
            volumeLevel = it.volumeLevel.toFloat()
            speechRate = it.speechRate
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "⚙️ Asistan Ayarları",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Wake Word
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🗣️ Asistan Aktivasyon Kelimesi (Wake-Word)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = wakeWord,
                    onValueChange = { wakeWord = it },
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // SOS Contact Number
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📞 Acil Durum Yakını Telefon Numarası (SMS için)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = emergencyContactNumber,
                    onValueChange = { emergencyContactNumber = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // SOS Message Text
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🚨 Acil Durum Açıklama Metni (Sesli & SMS)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = emergencyText,
                    onValueChange = { emergencyText = it },
                    textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Gemini API Key
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔑 Gemini AI API Anahtarı (Gelişmiş NLP)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = { geminiApiKey = it },
                    textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface
                    ),
                    placeholder = { Text("AIzaSy...", color = DarkGreyText) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Speech Rate Slider
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("🐢 Asistan Konuşma Hızı", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("%.2fx".format(speechRate), color = CalmCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.5f..1.5f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = CalmCyan,
                        thumbColor = CalmCyan
                    )
                )
            }
        }

        // Volume Level Slider
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("🔊 Maksimum Ses Seviyesi", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${volumeLevel.roundToInt()}%", color = CalmCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = volumeLevel,
                    onValueChange = { volumeLevel = it },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = CalmCyan,
                        thumbColor = CalmCyan
                    )
                )
            }
        }

        // Massive Save Button
        item {
            Button(
                onClick = {
                    viewModel.guncelleAyarlar(
                        wakeWord = wakeWord,
                        emergencyText = emergencyText,
                        emergencyContactNumber = emergencyContactNumber,
                        geminiApiKey = geminiApiKey,
                        volumeLevel = volumeLevel.roundToInt(),
                        speechRate = speechRate
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrightYellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Değişiklikleri Kaydet", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// Extension extension functions helper for compose
@Composable
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.pointerInput(Unit) {} // placeholder
)
