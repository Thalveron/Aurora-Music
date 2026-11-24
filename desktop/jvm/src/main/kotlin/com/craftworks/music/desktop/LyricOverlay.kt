package com.craftworks.music.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import javax.swing.SwingUtilities

@Composable
internal fun LyricOverlay(text: String, color: Color, onError: (String) -> Unit, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = "Aurora Music Lyrics",
        state = rememberWindowState(position = WindowPosition.Aligned(Alignment.BottomCenter), width = 900.dp, height = 110.dp),
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false,
        resizable = false,
    ) {
        LaunchedEffect(window) {
            if (Platform.isWindows()) {
                SwingUtilities.invokeLater {
                    try {
                        val handle = HWND(Native.getComponentPointer(window))
                        val current = User32.INSTANCE.GetWindowLong(handle, -20)
                        Native.setLastError(0)
                        val previous = User32.INSTANCE.SetWindowLong(handle, -20, current or 0x20 or 0x80)
                        if (previous == 0 && Native.getLastError() != 0) {
                            onError("Unable to enable click-through for desktop lyrics")
                        }
                    } catch (_: RuntimeException) {
                        onError("Unable to enable click-through for desktop lyrics")
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = TextStyle(
                    color = color,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black, Offset(2f, 2f), 5f),
                ),
            )
        }
    }
}
