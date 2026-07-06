import sys

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

target = """        // Global Overlay Player for Previews
        if (activePreviewId != null && activePreviewRect != null && activePreviewRect != androidx.compose.ui.geometry.Rect.Zero) {
            val rect = activePreviewRect!!
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { rect.left.toDp() },
                        y = with(density) { rect.top.toDp() }
                    )
                    .size(
                        width = with(density) { rect.width.toDp() },
                        height = with(density) { rect.height.toDp() }
                    )
            ) {
                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            useController = false
                            player = viewModel.previewPlayer
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            isClickable = false
                            isFocusable = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (view.player != viewModel.previewPlayer) {
                            view.player = viewModel.previewPlayer
                        }
                    }
                )
            }
        }"""

replacement = """        // Global Overlay Player for Previews
        val isPreviewVisible = activePreviewId != null && activePreviewRect != null && activePreviewRect != androidx.compose.ui.geometry.Rect.Zero
        val rect = activePreviewRect ?: androidx.compose.ui.geometry.Rect.Zero
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { rect.left.toDp() },
                    y = with(density) { rect.top.toDp() }
                )
                .size(
                    width = with(density) { rect.width.toDp() },
                    height = with(density) { rect.height.toDp() }
                )
                .alpha(if (isPreviewVisible) 1f else 0f)
        ) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = false
                        player = viewModel.previewPlayer
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        isClickable = false
                        isFocusable = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view.player != viewModel.previewPlayer) {
                        view.player = viewModel.previewPlayer
                    }
                }
            )
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
        f.write(content)
    print("Success")
else:
    print("Target not found")
