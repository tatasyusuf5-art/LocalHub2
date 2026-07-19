package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.TagEntity
import com.example.ui.viewmodel.AppViewModel
import java.io.File
import com.example.FlowRow
import com.example.ui.UserPickerDialog
import com.example.data.db.UserEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoImportDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    val tempThumbnails by viewModel.tempThumbnails.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()

    var selectedTags = remember { mutableStateListOf<TagEntity>() }
    val pickedUri by viewModel.pickedVideoUri.collectAsStateWithLifecycle()
    
    val initialTitle = remember(pickedUri) {
        var name = ""
        pickedUri?.let { uri ->
            if (uri.scheme == "file") {
                name = java.io.File(uri.path!!).nameWithoutExtension
            } else {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            val displayName = it.getString(displayNameIndex)
                            val dotIndex = displayName.lastIndexOf('.')
                            name = if (dotIndex != -1) displayName.substring(0, dotIndex) else displayName
                        }
                    }
                }
            }
        }
        name
    }

    var customTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var timeInput by remember { mutableStateOf("") }

    // Kullanıcı seçimi
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var showUserPicker by remember { mutableStateOf(false) }
    val selectedUser: UserEntity? = allUsers.find { it.id == selectedUserId }

    if (showUserPicker) {
        UserPickerDialog(
            viewModel = viewModel,
            selectedUserId = selectedUserId,
            onSelect = { selectedUserId = it },
            onDismiss = { showUserPicker = false }
        )
    }

    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Color.DarkGray,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("İçe Aktarılıyor") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { importProgress },
                        color = Color(0xFFFF9800),
                        trackColor = Color.DarkGray
                    )
                    Text(text = importStatus, color = Color.White, fontSize = 16.sp)
                }
            },
            confirmButton = {}
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.DarkGray,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        title = { Text("Yeni Video Ekle") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Video Başlığı", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Gray,
                        unfocusedBorderColor = Color.Gray,
                        disabledTextColor = Color.White,
                        disabledBorderColor = Color.Gray,
                        disabledLabelColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Kullanıcı seçme butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kullanıcı", fontWeight = FontWeight.Bold, color = Color.White)
                    TextButton(onClick = { showUserPicker = true }) {
                        Text(
                            text = selectedUser?.name ?: "Seç (opsiyonel)",
                            color = Color(0xFFFF9800)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Etiketler", fontWeight = FontWeight.Bold, color = Color.White)
                    var newTagName by remember { mutableStateOf("") }
                    var showAddTagDialog by remember { mutableStateOf(false) }

                    TextButton(onClick = { showAddTagDialog = true }) {
                        Text("+ Yeni Etiket", color = Color(0xFFFF9800))
                    }

                    if (showAddTagDialog) {
                        AlertDialog(
                            onDismissRequest = { showAddTagDialog = false },
                            containerColor = Color.DarkGray,
                            title = { Text("Yeni Etiket", color = Color.White) },
                            text = {
                                OutlinedTextField(
                                    value = newTagName,
                                    onValueChange = { newTagName = it },
                                    label = { Text("Etiket Adı", color = Color.Gray) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFFF9800),
                                        unfocusedBorderColor = Color.Gray
                                    )
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (newTagName.isNotBlank()) {
                                            viewModel.addTag(newTagName.trim())
                                            newTagName = ""
                                            showAddTagDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                ) { Text("Ekle", color = Color.White) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddTagDialog = false }) { Text("İptal", color = Color.Gray) }
                            }
                        )
                    }
                }

                if (tags.isEmpty()) {
                    Text("Henüz etiket yok.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            val isSelected = selectedTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                                },
                                label = { Text(tag.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFF9800),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Text("Kapak Fotoğrafları (Thumbnails)", fontWeight = FontWeight.Bold, color = Color.White)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        label = { Text("Sn (örn: 5.5)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Button(
                        onClick = {
                            val timeSeconds = timeInput.toFloatOrNull()
                            if (timeSeconds != null && timeSeconds >= 0) {
                                viewModel.addTempThumbnailAtTime(context, timeSeconds)
                                timeInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Ekle", color = Color.White)
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tempThumbnails) { thumb ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            val bitmap = remember(thumb.encryptedFilePath) {
                                try {
                                    android.graphics.BitmapFactory.decodeFile(thumb.encryptedFilePath)?.asImageBitmap()
                                } catch (e: Exception) { null }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteTempThumbnail(thumb.id) },
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Sil", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${thumb.timeMs / 1000}s",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha=0.6f)).padding(2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.finalizeVideoImport(context, customTitle, selectedTags, selectedUserId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("Kaydet", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = Color.Gray)
            }
        }
    )
}
