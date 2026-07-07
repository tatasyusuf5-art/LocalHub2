package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
// (MainActivity'deki rememberEncryptedImage ile aynı mantık,
//  isim çakışmasın diye rememberUserPhoto olarak ayrı yazıldı)
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
                    if (bmp != null) bitmap = androidx.compose.ui.graphics.asImageBitmap(bmp)
                } else bitmap = null
            } catch (e: Exception) { bitmap = null }
        }
    }
    return bitmap
}

// ============================================================
// 1. KULLANICI EKLEME DİYALOĞU
// (Ayarlar > Kullanıcı Ekle butonuna basınca açılır)
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
                // Profil fotoğrafı seçici
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
                        // Uri geçici olduğu için direkt AsyncImage yerine coil kullanmadan
                        // basit gösterim: seçildi ikonu
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
// (Ana ekranda en alttaki sıralama butonuna basınca açılır)
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
        // Sıra numarası
        Text(
            text = "#${user.rank}",
            color = UOrange,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.width(48.dp)
        )
        // Profil fotoğrafı
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
        // İsim + takipçi
        Column(Modifier.weight(1f)) {
            Text(user.name, color = UTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${formatFollowers(user.followers)} takipçi", color = UTextSecondary, fontSize = 13.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = UTextSecondary)
    }
}

// ============================================================
// 3. KULLANICI PROFİL EKRANI
// (Sıralamadan, aramadan veya video etiketinden tıklayınca açılır)
// ============================================================
@Composable
fun UserProfileScreen(
    userId: String,
    viewModel: AppViewModel,
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val user = allUsers.find { it.id == userId }
    val userVideos by viewModel.getVideosByUser(userId).collectAsStateWithLifecycle()

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

        // Profil başlığı
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val photo = rememberUserPhoto(user.profilePhotoPath)
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(UCardBg).border(2.dp, UOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    Image(photo, "Profil", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, tint = UTextSecondary, modifier = Modifier.size(50.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(user.name, color = UTextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatFollowers(user.followers), color = UOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Takipçi", color = UTextSecondary, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("#${user.rank}", color = UOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Sıra", color = UTextSecondary, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${userVideos.size}", color = UOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Video", color = UTextSecondary, fontSize = 12.sp)
                }
            }
        }

        Divider(color = Color(0xFF333333))

        // Kullanıcının videoları
        if (userVideos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bu kullanıcının videosu yok", color = UTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userVideos) { video ->
                    ProfileVideoRow(video = video, onClick = { onVideoClick(video.video.id) })
                }
            }
        }
    }
}

@Composable
private fun ProfileVideoRow(video: VideoWithTagsAndAssets, onClick: () -> Unit) {
    val thumbPath = video.thumbnails.firstOrNull()?.encryptedPath ?: ""
    val thumb = rememberUserPhoto(thumbPath)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(UCardBg)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(8.dp)).background(UBlack),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(thumb, "Kapak", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Movie, null, tint = UTextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(video.video.title, color = UTextPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

// ============================================================
// 4. KULLANICI SEÇME DİYALOĞU
// (Video eklerken "Kullanıcı Seç" için kullanılır)
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
                // "Kullanıcı yok" seçeneği
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

// Takipçi sayısını formatla (1500 -> 1.5B, 1000000 -> 1M)
fun formatFollowers(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fB", count / 1_000.0)
        else -> count.toString()
    }
}
