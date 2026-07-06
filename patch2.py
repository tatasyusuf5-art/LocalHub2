import sys

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

import re

target_regex = r"    val libVlc = remember \{ org\.videolan\.libvlc\.LibVLC\(context\) \}.*?modifier = Modifier\.fillMaxSize\(\)\n            \)"

replacement = """    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
    }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(encryptedVideoPath) {
        try {
            isLoading = true
            val encFile = File(encryptedVideoPath)
            if (encFile.exists()) {
                exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(encFile)))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                
                exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_READY) {
                            isLoading = false
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        isLoading = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Video oynatma hatası: ${error.message}")
                        }
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = exoPlayer
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )"""

new_content = re.sub(target_regex, replacement, content, flags=re.DOTALL)
if new_content != content:
    with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
        f.write(new_content)
    print("Success")
else:
    print("Target not found")

