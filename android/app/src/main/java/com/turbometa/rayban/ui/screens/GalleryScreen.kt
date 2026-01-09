package com.tourmeta.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tourmeta.app.R
import com.tourmeta.app.data.QuickVisionStorage
import com.tourmeta.app.models.QuickVisionRecord
import com.tourmeta.app.ui.components.ConfirmDialog
import com.tourmeta.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { QuickVisionStorage.getInstance(context) }
    var records by remember { mutableStateOf<List<QuickVisionRecord>>(emptyList()) }
    var selectedRecord by remember { mutableStateOf<QuickVisionRecord?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        records = storage.getAllRecords()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.gallery),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceLight
                )
            )
        }
    ) { paddingValues ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = TextTertiaryLight
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.large))

                    Text(
                        text = stringResource(R.string.gallery_empty),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryLight
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    Text(
                        text = "Photos taken with Live AI will appear here",
                        fontSize = 14.sp,
                        color = TextSecondaryLight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = AppSpacing.extraLarge)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(SurfaceLight),
                contentPadding = PaddingValues(AppSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                items(records) { record ->
                    val displayBitmap = remember(record.imagePath, record.thumbnailPath) {
                        try {
                            val primaryPath = record.imagePath
                            val file = if (!primaryPath.isNullOrBlank()) File(primaryPath) else File(record.thumbnailPath)
                            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                        } catch (e: Exception) {
                            null
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedRecord = record }
                            ),
                        shape = RoundedCornerShape(AppRadius.medium)
                    ) {
                        if (displayBitmap != null) {
                            Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3f)
                                    .clip(RoundedCornerShape(AppRadius.medium)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.gallery_empty),
                                    color = TextSecondaryLight
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRecord?.let { rec ->
        Dialog(
            onDismissRequest = { selectedRecord = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val displayPath = rec.imagePath ?: rec.thumbnailPath
                val bitmap = remember(displayPath) {
                    try {
                        val file = File(displayPath)
                        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    var scale by remember { mutableStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 5f)
                        offset += panChange
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .transformable(transformState)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            scale = 1f
                                            offset = Offset.Zero
                                        }
                                    )
                                },
                            contentScale = ContentScale.Fit
                        )

                        IconButton(
                            onClick = { selectedRecord = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(AppSpacing.medium)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(AppSpacing.medium)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && selectedRecord != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_record),
            message = stringResource(R.string.delete_record_confirm),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val id = selectedRecord!!.id
                val success = storage.deleteRecord(id)
                if (success) {
                    records = records.filter { it.id != id }
                    selectedRecord = null
                }
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
