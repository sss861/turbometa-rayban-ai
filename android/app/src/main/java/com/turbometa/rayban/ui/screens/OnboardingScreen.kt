package com.tourmeta.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.tourmeta.app.utils.APIKeyManager
import com.tourmeta.app.utils.OutputLanguage
import com.tourmeta.app.utils.GuideStyle
import com.tourmeta.app.utils.AgeGroup
import com.tourmeta.app.managers.LanguageManager
import com.tourmeta.app.managers.AppLanguage
import com.tourmeta.app.managers.LiveAIModeManager
import com.tourmeta.app.models.LiveAIMode
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Scaffold
import com.tourmeta.app.ui.components.GradientButton
import com.tourmeta.app.ui.components.StatusBadge
import com.tourmeta.app.ui.components.SectionHeader
import com.tourmeta.app.ui.components.TurboMetaTopBar
import com.tourmeta.app.ui.theme.AppRadius
import com.tourmeta.app.ui.theme.AppSpacing
import com.tourmeta.app.ui.theme.Primary
import com.tourmeta.app.viewmodels.WearablesViewModel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.tourmeta.app.R

@Composable
fun OnboardingScreen(wearablesViewModel: WearablesViewModel, onFinished: () -> Unit) {
    val context = LocalContext.current
    val api = remember { APIKeyManager.getInstance(context) }
    var step by remember { mutableStateOf(1) }
    var selectedLanguage by remember { mutableStateOf(api.getOutputLanguage()) }
    var ageInput by remember { mutableStateOf(api.getVisitorAge().toString()) }
    val ageNumber = ageInput.toIntOrNull()
    val ageGroup = remember(ageInput) {
        ageNumber?.let {
            when {
                it < 12 -> AgeGroup.CHILD_UNDER_12
                it in 12..18 -> AgeGroup.TEEN_12_18
                it in 19..30 -> AgeGroup.ADULT_18_30
                it in 31..50 -> AgeGroup.ADULT_30_50
                else -> AgeGroup.SENIOR_OVER_50
            }
        }
    }
    var style by remember { mutableStateOf(api.getGuideStyle()) }
    var blind by remember { mutableStateOf(api.isBlindMode()) }
    val haptic = LocalHapticFeedback.current
    val tts = remember { TextToSpeech(context) { } }
    val connectionState by wearablesViewModel.connectionState.collectAsState()
    val hasActiveDevice by wearablesViewModel.hasActiveDevice.collectAsState()
    val languages = remember {
        listOf(
            OutputLanguage.CHINESE,
            OutputLanguage.TRADITIONAL_CHINESE,
            OutputLanguage.ENGLISH,
            OutputLanguage.JAPANESE,
            OutputLanguage.KOREAN,
            OutputLanguage.SPANISH,
            OutputLanguage.FRENCH
        )
    }
    LaunchedEffect(selectedLanguage) {
        val locale = when (selectedLanguage) {
            "zh-CN" -> Locale.CHINESE
            "zh-HK" -> Locale("zh", "HK")
            "en-US" -> Locale.US
            "ja-JP" -> Locale.JAPANESE
            "ko-KR" -> Locale.KOREAN
            "es-ES" -> Locale("es", "ES")
            "fr-FR" -> Locale.FRENCH
            else -> Locale.US
        }
        tts.language = locale
        val intro = when (selectedLanguage) {
            "zh-CN" -> "引导页。单击切换语言，长按切换视障模式，双击完成。"
            "zh-HK" -> "引導頁。單擊切換語言，長按切換視障模式，雙擊完成。"
            "ja-JP" -> "オンボーディング。タップで言語を切り替え、長押しでアクセシビリティモード、ダブルタップで完了。"
            "ko-KR" -> "온보딩. 탭하여 언어 변경, 길게 눌러 접근성 모드, 두 번 탭하여 완료."
            "es-ES" -> "Introducción. Toca para cambiar idioma, mantén para modo accesibilidad, doble toque para finalizar."
            "fr-FR" -> "Accueil. Touchez pour changer la langue, appui long pour le mode accessibilité, double-touchez pour terminer."
            "en-US" -> "Onboarding. Single tap to change language, long press for accessibility mode, double tap to finish."
            else -> "Onboarding. Single tap to change language, long press for accessibility mode, double tap to finish."
        }
        tts.speak(intro, TextToSpeech.QUEUE_FLUSH, null, "intro")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TurboMetaTopBar(title = stringResource(R.string.topbar_title))
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                if (step == 1) {
                    Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    SectionHeader(title = stringResource(R.string.onboarding_select_language))
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.medium),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(languages) { lang ->
                                LanguageOption(
                                    selected = selectedLanguage == lang.code,
                                    title = lang.nativeName,
                                    subtitle = lang.displayName,
                                    languageCode = lang.code,
                                    onClick = {
                                        selectedLanguage = lang.code
                                        api.saveOutputLanguage(lang.code)
                                        val appLang = when (lang.code) {
                                            "zh-CN" -> AppLanguage.CHINESE
                                            "zh-HK" -> AppLanguage.TRADITIONAL_CHINESE
                                            "en-US" -> AppLanguage.ENGLISH
                                            "ja-JP" -> AppLanguage.JAPANESE
                                            "ko-KR" -> AppLanguage.KOREAN
                                            "es-ES" -> AppLanguage.SPANISH
                                            "fr-FR" -> AppLanguage.FRENCH
                                            else -> AppLanguage.ENGLISH
                                        }
                                        LanguageManager.setLanguage(context, appLang)
                                    }
                                )
                            }
                        }
                    }
                    SectionHeader(title = stringResource(R.string.onboarding_device_connection))
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.medium),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.medium),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                        ) {
                            val statusText = when (connectionState) {
                                is com.tourmeta.app.viewmodels.WearablesViewModel.ConnectionState.Connected -> stringResource(R.string.connected)
                                is com.tourmeta.app.viewmodels.WearablesViewModel.ConnectionState.Searching -> stringResource(R.string.searching)
                                is com.tourmeta.app.viewmodels.WearablesViewModel.ConnectionState.Connecting -> stringResource(R.string.connecting)
                                is com.tourmeta.app.viewmodels.WearablesViewModel.ConnectionState.Error -> stringResource(R.string.error)
                                else -> stringResource(R.string.disconnected)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (hasActiveDevice) Primary else MaterialTheme.colorScheme.onSurfaceVariant, androidx.compose.foundation.shape.CircleShape)
                                )
                                Text(statusText, fontWeight = FontWeight.Medium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                                FilterChip(
                                    selected = false,
                                    onClick = { wearablesViewModel.connectWithRetry() },
                                    label = { Text(stringResource(R.string.connect_glasses)) }
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { wearablesViewModel.disconnect() },
                                    label = { Text(stringResource(R.string.disconnect)) }
                                )
                            }
                        }
                    }
                    SectionHeader(title = stringResource(R.string.accessibility))
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.medium),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                        ) {
                            Text(stringResource(R.string.blind_mode_title), fontWeight = FontWeight.Medium)
                            Switch(checked = blind, onCheckedChange = {
                                blind = it
                                api.saveBlindMode(it)
                            })
                        }
                    }
                    Spacer(modifier = Modifier.height(AppSpacing.medium))
                    GradientButton(
                        text = stringResource(R.string.onboarding_next),
                        onClick = { step = 2 },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.large))
                } else {
                    Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

                SectionHeader(title = stringResource(R.string.onboarding_age))
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.medium),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { input ->
                                ageInput = input.filter { it.isDigit() }.take(3)
                            },
                            label = { Text(stringResource(R.string.onboarding_age_input_label)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (ageNumber != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                                Text(stringResource(R.string.onboarding_age_group_label), fontWeight = FontWeight.Medium)
                                ageGroup?.let { StatusBadge(text = it.getDisplayName(context), color = Primary) }
                            }
                        } else {
                            Text(stringResource(R.string.onboarding_age_invalid), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                SectionHeader(title = stringResource(R.string.onboarding_guide_style))
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.medium),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                    ) {
                        listOf(GuideStyle.CONCISE, GuideStyle.STORYTELLING, GuideStyle.ACADEMIC).forEach { s ->
                            FilterChip(
                                selected = style == s,
                                onClick = { style = s },
                                label = { Text(s.getDisplayName(context)) }
                            )
                        }
                    }
                }

                SectionHeader(title = stringResource(R.string.accessibility))
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.medium),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                    ) {
                        Text(stringResource(R.string.blind_mode_title), fontWeight = FontWeight.Medium)
                        Switch(checked = blind, onCheckedChange = { blind = it })
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.medium))
                GradientButton(
                    text = stringResource(R.string.onboarding_start_visit),
                    onClick = {
                        api.saveOutputLanguage(selectedLanguage)
                        val age = ageNumber ?: api.getVisitorAge()
                        api.saveVisitorAge(age)
                        ageGroup?.let { api.saveVisitorAgeGroup(it) }
                        api.saveGuideStyle(style)
                        api.saveBlindMode(blind)
                        api.setOnboardingDone(true)
                        val appLang = when (selectedLanguage) {
                            "zh-CN" -> AppLanguage.CHINESE
                            "zh-HK" -> AppLanguage.TRADITIONAL_CHINESE
                            "en-US" -> AppLanguage.ENGLISH
                            "ja-JP" -> AppLanguage.JAPANESE
                            "ko-KR" -> AppLanguage.KOREAN
                            "es-ES" -> AppLanguage.SPANISH
                            "fr-FR" -> AppLanguage.FRENCH
                            else -> AppLanguage.ENGLISH
                        }
                        LanguageManager.setLanguage(context, appLang)
                        if (blind) {
                            LiveAIModeManager.getInstance(context).setMode(LiveAIMode.BLIND)
                        } else {
                            val targetMode = when (ageGroup) {
                                AgeGroup.CHILD_UNDER_12 -> LiveAIMode.CHILD
                                AgeGroup.SENIOR_OVER_50 -> LiveAIMode.SENIOR
                                else -> LiveAIMode.MUSEUM
                            }
                            LiveAIModeManager.getInstance(context).setMode(targetMode)
                        }
                        onFinished()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppSpacing.large))
                }
            }
        }

        if (blind && step == 2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedLanguage, blind, ageInput) {
                        detectTapGestures(
                            onTap = {
                                val idx = languages.indexOfFirst { it.code == selectedLanguage }
                                val next = if (idx == -1) languages.first() else languages[(idx + 1) % languages.size]
                                selectedLanguage = next.code
                                val name = "${next.nativeName} ${next.displayName}"
                                tts.speak(name, TextToSpeech.QUEUE_FLUSH, null, "lang")
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDoubleTap = {
                                val age = ageNumber ?: api.getVisitorAge()
                                api.saveOutputLanguage(selectedLanguage)
                                api.saveVisitorAge(age)
                                ageGroup?.let { api.saveVisitorAgeGroup(it) }
                                api.saveGuideStyle(style)
                                api.saveBlindMode(blind)
                                api.setOnboardingDone(true)
                                val appLang = when (selectedLanguage) {
                                    "zh-CN" -> AppLanguage.CHINESE
                                    "en-US" -> AppLanguage.ENGLISH
                                    "ja-JP" -> AppLanguage.JAPANESE
                                    "ko-KR" -> AppLanguage.KOREAN
                                    "es-ES" -> AppLanguage.SPANISH
                                    "fr-FR" -> AppLanguage.FRENCH
                                    else -> AppLanguage.ENGLISH
                                }
                                LanguageManager.setLanguage(context, appLang)
                                if (blind) {
                                    LiveAIModeManager.getInstance(context).setMode(LiveAIMode.BLIND)
                                } else {
                                    val targetMode = when (ageGroup) {
                                        AgeGroup.CHILD_UNDER_12 -> LiveAIMode.CHILD
                                        AgeGroup.SENIOR_OVER_50 -> LiveAIMode.SENIOR
                                        else -> LiveAIMode.MUSEUM
                                    }
                                    LiveAIModeManager.getInstance(context).setMode(targetMode)
                                }
                            val doneText = when (selectedLanguage) {
                                "zh-CN" -> "设置完成，开始参观"
                                "zh-HK" -> "設定完成，開始參觀"
                                "ja-JP" -> "設定が完了しました。見学を開始します"
                                "ko-KR" -> "설정이 완료되었습니다. 관람을 시작합니다"
                                "es-ES" -> "Configuración completada, comienza la visita"
                                "fr-FR" -> "Configuration terminée, commencez la visite"
                                else -> "Setup complete, start tour"
                            }
                            tts.speak(doneText, TextToSpeech.QUEUE_FLUSH, null, "done")
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onFinished()
                        },
                            onLongPress = {
                                blind = !blind
                                val msg = when (selectedLanguage) {
                                    "zh-CN" -> if (blind) "已开启视障模式" else "已关闭视障模式"
                                    "zh-HK" -> if (blind) "已開啟視障模式" else "已關閉視障模式"
                                    "ja-JP" -> if (blind) "アクセシビリティモードをオンにしました" else "アクセシビリティモードをオフにしました"
                                    "ko-KR" -> if (blind) "접근성 모드를 켰습니다" else "접근성 모드를 껐습니다"
                                    "es-ES" -> if (blind) "Modo accesibilidad activado" else "Modo accesibilidad desactivado"
                                    "fr-FR" -> if (blind) "Mode accessibilité activé" else "Mode accessibilité désactivé"
                                    else -> if (blind) "Accessibility mode enabled" else "Accessibility mode disabled"
                                }
                                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "blind")
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
            )
        }
    }
}

@Composable
private fun LanguageOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    languageCode: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.97f else 1f
    val bg = if (selected) {
        Brush.horizontalGradient(listOf(Primary, Primary.copy(alpha = 0.7f)))
    } else {
        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
    }
    Card(
        shape = RoundedCornerShape(AppRadius.medium),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .width(160.dp)
            .height(96.dp)
            .scale(scale)
            .background(bg, RoundedCornerShape(AppRadius.medium))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(AppSpacing.medium)) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val flag = when (languageCode) {
                        "zh-CN" -> "🇨🇳"
                        "zh-HK" -> "🇭🇰"
                        "en-US" -> "🇺🇸"
                        "ja-JP" -> "🇯🇵"
                        "ko-KR" -> "🇰🇷"
                        "es-ES" -> "🇪🇸"
                        "fr-FR" -> "🇫🇷"
                        else -> "🏳️"
                    }
                    Text(
                        text = flag,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = title,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = subtitle,
                    color = if (selected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Start
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}
