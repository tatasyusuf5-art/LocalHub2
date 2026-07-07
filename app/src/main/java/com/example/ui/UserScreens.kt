package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.UserEntity
import com.example.data.db.VideoWithTagsAndAssets
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppViewModel

// Renk sabitleri (theme'den bağımsız güvenli kullanım için)
private val UBlack = Color(0xFF000000)
private val UOrange = Color(0xFFFF6D00)
private val UCardBg = Color(0xFF1A1A1A)
private val UTextPrimary = Color(0xFFFFFFFF)
private val UTextSecondary = Color(0xFF9E9E9E)

// ============================================================
// Profil fotoğrafını dosyadan yükleyen yardımcı
// ============================================================
@Composable
fun rememberUserPhoto(filePath: String): ImageBitmap? {
    var bitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(filePath) {
        if (filePath.isEmpty()) { bitmap = null; return@LaunchedEffect }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) bitmap = bmp.asImageBitmap()
                } else bitmap = null
            } catch (e: Exception) { bitmap = null }
        }
    }
    return bitmap
}

// ============================================================
// 1. KULLANICI EKLEME DİYALOĞU
// ============================================================
@Composable
fun AddUserDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var followers by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) photoUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UCardBg,
        title = { Text("Kullanıcı Ekle", color = UOrange, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(UBlack)
                        .border(2.dp, UOrange, CircleShape)
                        .clickable { photoPicker.launch("image/*") }
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        val bmp = rememberUserPhoto(photoUri.toString())
                        Icon(Icons.Default.CheckCircle, "Seçildi", tint = UOrange, modifier = Modifier.size(40.dp))
                    } else {
                        Icon(Icons.Default.AddAPhoto, "Foto ekle", tint = UTextSecondary, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Kullanıcı Adı") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UOrange,
                        focusedLabelColor = UOrange,
                        cursorColor = UOrange,
                        focusedTextColor = UTextPrimary,
                        unfocusedTextColor = UTextPrimary
                    )
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = followers,
                    onValueChange = { followers = it.filter { c -> c.isDigit() } },
                    label = { Text("Takipçi Sayısı") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UOrange,
                        focusedLabelColor = UOrange,
                        cursorColor = UOrange,
                        focusedTextColor = UTextPrimary,
                        unfocusedTextColor = UTextPrimary
                    )
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = rank,
                    onValueChange = { rank = it.filter { c -> c.isDigit() } },
                    label = { Text("Sıra (1, 2, 3...)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UOrange,
                        focusedLabelColor = UOrange,
                        cursorColor = UOrange,
                        focusedTextColor = UTextPrimary,
                        unfocusedTextColor = UTextPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addUser(
                            context = context,
                            name = name.trim(),
                            followers = followers.toLongOrNull() ?: 0L,
                            rank = rank.toIntOrNull() ?: 999,
                            photoUri = photoUri
                        )
                        onDismiss()
                    }
                }
            ) { Text("Ekle", color = UOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = UTextSecondary) }
        }
    )
}

// ============================================================
// 2. SIRALAMA TABLOSU (Ranking)
// ============================================================
@Composable
fun RankingScreen(
    viewModel: AppViewModel,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UBlack)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Geri", tint = UTextPrimary)
            }
            Text("Sıralama", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = UOrange)
        }
        Spacer(Modifier.height(12.dp))

        if (users.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz kullanıcı eklenmedi.", color = UTextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users) { user ->
                    RankingRow(user = user, onClick = { onUserClick(user.id) })
                }
            }
        }
    }
}

@Composable
private fun RankingRow(user: UserEntity, onClick: () -> Unit) {
    val photo = rememberUserPhoto(user.profilePhotoPath)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UCardBg)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${user.rank}",
            color = UOrange,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.width(48.dp)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(UBlack),
            contentAlignment = Alignment.Center
        ) {
            if (photo != null) {
                Image(photo, "Profil", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Person, null, tint = UTextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, color = UTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${formatFollowers(user.followers)} takipçi", color = UTextSecondary, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = UTextSecondary)
    }
}

// ============================================================
// 3. KULLANICI PROFİL EKRANI
// ============================================================
@Composable
fun UserProfileScreen(
    userId: String,
    viewModel: AppViewModel,
    onVideoClick: (String) -> Unit,
    onPreviewStart: (String) -> Unit = {}, // YENİ: MainActivity'den bağlanacak
    onPreviewStop: () -> Unit = {},        // YENİ: MainActivity'den bağlanacak
    onBack: () -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val user = allUsers.find { it.id == userId }
    val userVideosFlow = remember(userId) { viewModel.getVideosByUser(userId) }
    val userVideos by userVideosFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UBlack)
    ) {
        // Üst bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Geri", tint = UTextPrimary)
            }
            Text(user?.name ?: "Profil", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = UOrange, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kullanıcı bulunamadı", color = UTextSecondary)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val photo = rememberUserPhoto(user.profilePhotoPath)
                        Box(
                            modifier = Modifier.size(110.dp).clip(CircleShape).background(UCardBg).border(2.dp, UOrange, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photo != null) {
                                Image(photo, "Profil", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.Person, null, tint = UTextSecondary, modifier = Modifier.size(55.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.name, color = UTextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, "Doğrulanmış", tint = Color(0xFF1DA1F2), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Default.EmojiEvents, "Ödül", tint = UOrange, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("#${user.rank}", color = UTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Sıra", color = UTextSecondary, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(formatFollowers(user.followers), color = UTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Takipçi", color = UTextSecondary, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${userVideos.size}", color = UTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Video", color = UTextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = UOrange),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Profili Görüntüle", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UOrange)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, tint = UOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Takip", color = UTextPrimary, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444))
                        ) {
                            Icon(Icons.Default.Message, null, tint = UTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mesaj", color = UTextPrimary, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Divider(color = Color(0xFF333333))
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "${user.name} - En Son Videolar",
                        color = UTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (userVideos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Bu kullanıcının videosu yok", color = UTextSecondary)
                    }
                }
            } else {
                items(userVideos) { video ->
                    ProfileLargeVideoCard(
                        video = video,
                        user = user,
                        onClick = { onVideoClick(video.video.id.toString()) },
                        // YENİ: Preview aksiyonları karta iletiliyor
                        onPreviewStart = { onPreviewStart(video.video.id.toString()) },
                        onPreviewStop = { onPreviewStop() }
                    )
                }
                
                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileLargeVideoCard(
    video: VideoWithTagsAndAssets,
    user: UserEntity,
    onClick: () -> Unit,
    onPreviewStart: () -> Unit,
    onPreviewStop: () -> Unit
) {
    // YENİ: Thumbnail artık her açılışta video klasöründen rastgele seçiliyor
    val randomThumbPath = remember(video.video.id) {
        video.thumbnails.randomOrNull()?.encryptedPath ?: ""
    }
    val thumb = rememberUserPhoto(randomThumbPath)
    val userPhoto = rememberUserPhoto(user.profilePhotoPath)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // YENİ: pointerInput ile basılı tutma (long press) algılama eklendi
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { 
                        onPreviewStart() // 300ms civarı basılı tutunca preview başlar
                    },
                    onPress = {
                        val release = tryAwaitRelease() // Parmak çekilene kadar bekler
                        onPreviewStop() // Parmak ekrandan kalkınca preview durur
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = UCardBg)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(UBlack),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = "Kapak",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie, 
                        contentDescription = null, 
                        tint = UTextSecondary, 
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = video.video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = UTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(6.dp))

                val tagsText = video.tags.joinToString(" ") { "#${it.name}" }
                Text(
                    text = tagsText.ifEmpty { "#Video" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = UTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(UBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userPhoto != null) {
                            Image(
                                bitmap = userPhoto,
                                contentDescription = "User Avatar",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person, 
                                contentDescription = null, 
                                tint = UTextSecondary, 
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = UTextPrimary
                    )
                }
            }
        }
    }
}

// ============================================================
// 4. KULLANICI SEÇME DİYALOĞU
// ============================================================
@Composable
fun UserPickerDialog(
    viewModel: AppViewModel,
    selectedUserId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UCardBg,
        title = { Text("Kullanıcı Seç", color = UOrange, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (selectedUserId == null) UOrange.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelect(null); onDismiss() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Block, null, tint = UTextSecondary)
                        Spacer(Modifier.width(12.dp))
                        Text("Kullanıcı atama", color = UTextPrimary)
                    }
                }
                items(users) { user ->
                    val photo = rememberUserPhoto(user.profilePhotoPath)
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (selectedUserId == user.id) UOrange.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelect(user.id); onDismiss() }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(UBlack), contentAlignment = Alignment.Center) {
                            if (photo != null) Image(photo, null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Person, null, tint = UTextSecondary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(user.name, color = UTextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat", color = UOrange) }
        }
    )
}

fun formatFollowers(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fB", count / 1_000.0)
        else -> count.toString()
    }
}
               }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat", color = UOrange) }
        }
    )
}

// Takipçi sayısını formatla (1500 -> 1.5B, 1000000 -> 1M)
fun formatFollowers(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fB", count / 1_000.0)
        else -> count.toString()
    }
}
