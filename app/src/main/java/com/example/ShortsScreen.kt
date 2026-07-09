package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.db.VideoWithTagsAndAssets
import com.example.ui.theme.PrimaryOrange
import com.example.ui.viewmodel.AppViewModel
import java.io.File

private val SBlack = Color(0xFF000000)
private val SWhite = Color(0xFFFFFFFF)
private val SOrange = Color(0xFFFF6D00)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ShortsScreen(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onOpenFullVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val shorts by viewModel.shortsList.collectAsStateWithLifecycle()

    // Ekran açıldığında karıştır
    LaunchedEffect(Unit) { viewModel.reshuffleShorts() }

    if (shorts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(SBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideoLibrary, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Henüz shorts için video yok", color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = SOrange)
                ) { Text("Geri Dön", color = SBlack) }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { shorts.size })

    // Tek ExoPlayer - shorts için, SESLİ
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f  // SHORTS'ta ses AÇIK
            playWhenReady = true
        }
    }

    // Aktif sayfa değişince o videonun preview'ini yükle
    LaunchedEffect(pagerState.currentPage, shorts) {
        val item = shorts.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val previewPath = item.previews.firstOrNull()?.encryptedPath
        if (previewPath != null && File(previewPath).exists()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(previewPath))))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    var isPaused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(SBlack)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = shorts[page]
            val isActive = page == pagerState.currentPage

            Box(modifier = Modifier.fillMaxSize()) {
                // Sadece aktif sayfada video oynatıcı göster
                if (isActive) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                // Tıklama: durdur/oynat (tam videoyu AÇMA)
                                isPaused = !isPaused
                                exoPlayer.playWhenReady = !isPaused
                            },
                        update = { view -> view.player = exoPlayer }
                    )
                } else {
                    // Aktif olmayan sayfalar: sadece thumbnail (siyah arka plan)
                    Box(modifier = Modifier.fillMaxSize().background(SBlack))
                }

                // Duraklat ikonu (ortada, duraklatıldıysa)
                if (isActive && isPaused) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Oynat",
                        tint = SWhite.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.Center).size(70.dp)
                    )
                }

                // === ALT BİLGİ KATMANI (Instagram tarzı) ===
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 24.dp)
                ) {
                    // "Tam Video" butonu (profilin ÜSTÜNDE)
                    Button(
                        onClick = { onOpenFullVideo(item.video.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = SOrange),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayCircle, null, tint = SBlack, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tam Video", color = SBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Paylaşan kişi (profil fotosu + adı)
                    item.user?.let { user ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val userPhoto = rememberEncryptedImage(user.profilePhotoPath)
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                if (userPhoto != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = userPhoto,
                                        contentDescription = user.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Icon(Icons.Default.Person, null, tint = SWhite, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                user.name,
                                color = SWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Video açıklaması (başlık)
                    Text(
                        item.video.title,
                        color = SWhite,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Üst bar: kapat butonu + "Shorts" başlığı
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = SWhite)
            }
            Spacer(Modifier.width(12.dp))
            Text("Shorts", color = SWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}
