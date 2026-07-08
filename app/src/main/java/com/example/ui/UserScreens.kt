package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.UserEntity
import com.example.data.db.VideoWithTagsAndAssets
import com.example.VideoCard
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
                    if (bmp != null) bitmap = bmp.asImageBitmap()
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    RankingGridCell(user = user, onClick = { onUserClick(user.id) })
                }
            }
        }
    }
}

@Composable
private fun RankingGridCell(user: UserEntity, onClick: () -> Unit) {
    val photo = rememberUserPhoto(user.profilePhotoPath)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Büyük yuvarlak profil fotosu (rank rozetli)
        Box(contentAlignment = Alignment.TopStart) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(UCardBg)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    Image(photo, "Profil", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, tint = UTextSecondary, modifier = Modifier.size(64.dp))
                }
            }
            // Sıra rozeti (sol üst köşe)
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UOrange)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("#${user.rank}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // İsim + tik + kupa
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                user.name,
                color = UTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Default.Verified, "Doğrulanmış", tint = Color(0xFF1DA1F2), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.EmojiEvents, "Ödül", tint = UOrange, modifier = Modifier.size(15.dp))
        }

        Spacer(Modifier.height(2.dp))

        // Video + Takipçi
        Text(
            text = "${formatFollowers(user.followers)} takipçi",
            color = UTextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(8.dp))

        // Profili Görüntüle butonu
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = UOrange),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Profili Görüntüle", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
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
    onVideoOptions: (String) -> Unit,
    onBack: () -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val user = allUsers.find { it.id == userId }
    // BUG FIX: flow'u remember ile sabitle, yoksa her recomposition'da 0/1 titrer
    val userVideosFlow = remember(userId) { viewModel.getVideosByUser(userId) }
    val userVideos by userVideosFlow.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(UBlack)) {
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

        // Videolar aşağı kaydırıldıkça devam etmesi için tüm içerik LazyColumn'da
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // --- PROFİL BAŞLIĞI (Pornhub tarzı) ---
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    // Foto solda + istatistikler sağda
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
                            // İsim + tik + kupa
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.name, color = UTextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, "Doğrulanmış", tint = Color(0xFF1DA1F2), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Default.EmojiEvents, "Ödül", tint = UOrange, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            // Yatay istatistikler: Sıra / Takipçi / Video
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

                    // "Profili Görüntüle" geniş turuncu buton
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

                    // Subscribe / Message / Links butonları (dekoratif)
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

            // --- VİDEOLAR (ana ekrandaki gerçek VideoCard) ---
            if (userVideos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Bu kullanıcının videosu yok", color = UTextSecondary)
                    }
                }
            } else {
                items(userVideos, key = { it.video.id }) { video ->
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        VideoCard(
                            item = video,
                            isSelected = false,
                            isInSelectionMode = false,
                            viewModel = viewModel,
                            isVisible = true,
                            onClick = { onVideoClick(video.video.id) },
                            onOptionsClick = { onVideoOptions(video.video.id) }
                        )
                    }
                }
            }
        }
        } // Column sonu

        // NOT: Preview artık VideoCard'ın İÇİNDE render ediliyor.
        // Profildeki global overlay kaldırıldı (kart içi preview kart ile kayar).
    } // Box sonu
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
