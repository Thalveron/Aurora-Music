package com.craftworks.music.ui.screens.tv

import com.craftworks.music.ui.elements.tv.TvScreenTitle
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import com.craftworks.music.R
import com.craftworks.music.data.model.Screen


@Preview(
    showSystemUi = false, showBackground = true, device = "id:tv_1080p"
)
@Composable
fun TvSettingScreen(
    navHostController: NavHostController = rememberNavController()
) {
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
            .focusRestorer(focusRequester)
            .focusRequester(focusRequester)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TvScreenTitle(
            title = stringResource(R.string.settings),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        SettingsButton(
            Screen.S_Appearance.route,
            R.drawable.s_a_palette,
            R.string.Settings_Header_Appearance,
            R.string.Tv_Settings_Appearance_Desc,
            navHostController,
            Modifier.onFocusChanged {
                focusRequester.saveFocusedChild()
            }
        )

        SettingsButton(
            Screen.S_Providers.route,
            R.drawable.s_m_media_providers,
            R.string.Settings_Header_Media,
            R.string.Tv_Settings_Media_Desc,
            navHostController,
            Modifier.onFocusChanged {
                focusRequester.saveFocusedChild()
            }
        )

        SettingsButton(
            Screen.S_Playback.route,
            R.drawable.s_m_playback,
            R.string.Settings_Header_Playback,
            R.string.Tv_Settings_Playback_Desc,
            navHostController,
            Modifier.onFocusChanged {
                focusRequester.saveFocusedChild()
            }
        )
        WideButton(
            onClick = { com.craftworks.music.updates.MusicUpdateActivity.open(context) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            icon = {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.rounded_download_24),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
            },
            title = { Text(stringResource(R.string.music_update_title)) },
            subtitle = {
                Text(
                    stringResource(R.string.Tv_Settings_Update_Desc) + " · " +
                            com.craftworks.music.updates.MusicUpdates.version(context)
                )
            }
        )
    }
}

@Composable
private fun SettingsButton(
    route: String,
    icon: Int,
    text: Int,
    description: Int,
    navHostController: NavHostController,
    modifier: Modifier = Modifier
) {
    WideButton(
        modifier = modifier,
        onClick = {
            navHostController.navigate(route) {
                launchSingleTop = true
            }
        },
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(icon),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        },
        title = {
            Text(
                text = stringResource(text), modifier = Modifier
            )
        },
        subtitle = { Text(stringResource(description)) }
    )
}
