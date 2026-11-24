package com.craftworks.music.ui.elements.dialogs.tv

import android.content.res.Configuration
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import com.craftworks.music.data.datasource.navidrome.NavidromeDataSource
import com.craftworks.music.providers.navidrome.NetworkFailure
import com.craftworks.music.providers.navidrome.ServerCheck
import com.craftworks.music.providers.navidrome.normalizeServerUrl
import com.craftworks.music.ui.elements.tv.AuroraBackdrop
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.craftworks.music.ui.elements.tv.auroraBackground
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component3
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.craftworks.music.R
import com.craftworks.music.data.NavidromeProvider
import com.craftworks.music.managers.LocalProviderManager
import com.craftworks.music.managers.NavidromeManager
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import com.craftworks.music.managers.settings.MediaProviderSettingsManager
import com.craftworks.music.providers.navidrome.navidromeStatus
import com.craftworks.music.ui.screens.tv.settings.SettingsSwitchItem
import kotlinx.coroutines.launch
import java.io.File

/**
 * One screen, one action: the address and account are tested together and the server is saved as
 * soon as it answers, so there is no hidden second step to get stuck on.
 */
@Composable
fun CreateNavidromeProviderDialog(
    setShowDialog: (Boolean) -> Unit = { }
) {
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var allowCerts by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ServerCheck?>(null) }
    var missingUsername by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val dataSource = remember { NavidromeDataSource() }
    val urlFocus = remember { FocusRequester() }

    fun submit() {
        if (checking) return
        missingUsername = false
        val address = normalizeServerUrl(url)
        if (address == null) {
            result = ServerCheck.InvalidUrl
            return
        }
        val account = username.trim()
        if (account.isEmpty()) {
            result = null
            missingUsername = true
            return
        }

        checking = true
        result = null
        coroutineScope.launch {
            val server = NavidromeProvider(
                id = "$address|$account",
                url = address,
                username = account,
                password = password,
                enabled = true,
                allowSelfSignedCert = allowCerts
            )
            val check = dataSource.checkServer(server)
            if (check == ServerCheck.Ok) {
                // Finish saving even if the dialog is closed while the library list loads.
                withContext(NonCancellable) {
                    NavidromeManager.addServer(server)
                    AppearanceSettingsManager(context).setUsername(account)
                }
                navidromeStatus.value = ""
                setShowDialog(false)
            } else {
                result = check
            }
            checking = false
        }
    }

    val message = when {
        checking -> stringResource(R.string.Tv_Server_Connecting)
        missingUsername -> stringResource(R.string.Tv_Server_Error_Username)
        else -> result?.let { serverCheckMessage(it) }
    }
    val addressProblem = result != null && result != ServerCheck.WrongCredentials
    val accountProblem = result == ServerCheck.WrongCredentials

    Dialog(
        onDismissRequest = { setShowDialog(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AuroraBackdrop {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 56.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.s_m_media_providers),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.Tv_Server_Title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.Tv_Server_Intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        R.string.Tv_Server_Tip_Address,
                        R.string.Tv_Server_Tip_Keyboard,
                        R.string.Tv_Server_Tip_Network
                    ).forEach { tip ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Text(
                                text = stringResource(tip),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServerField(
                        value = url,
                        onValueChange = { url = it },
                        label = stringResource(R.string.Tv_Server_Address),
                        placeholder = "192.168.1.10:4533",
                        isError = addressProblem,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        modifier = Modifier.focusRequester(urlFocus)
                    )
                    ServerField(
                        value = username,
                        onValueChange = { username = it },
                        label = stringResource(R.string.Tv_Server_Account),
                        isError = accountProblem || missingUsername,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                    ServerField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.Tv_Server_Password),
                        isError = accountProblem,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = { submit() },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
                    )

                    SettingsSwitchItem(
                        title = stringResource(R.string.Tv_Server_Show_Password),
                        checked = passwordVisible,
                        onCheckedChange = { passwordVisible = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.Label_Allow_Self_Signed_Certs),
                        checked = allowCerts,
                        onCheckedChange = { allowCerts = it }
                    )

                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (checking) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        OutlinedButton(onClick = { setShowDialog(false) }) {
                            Text(stringResource(R.string.Action_Cancel))
                        }
                        Button(
                            onClick = { submit() },
                            enabled = !checking
                        ) {
                            Text(stringResource(R.string.Tv_Server_Connect))
                        }
                    }
                }
            }

            // Inside the dialog's own composition, so the field exists when focus is requested.
            LaunchedEffect(Unit) {
                runCatching { urlFocus.requestFocus() }
            }
        }
    }

}

@Composable
private fun ServerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { androidx.compose.material3.Text(label) },
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } },
        singleLine = true,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun serverCheckMessage(check: ServerCheck): String? = when (check) {
    ServerCheck.Ok -> null
    ServerCheck.InvalidUrl -> stringResource(R.string.Tv_Server_Error_Url)
    ServerCheck.WrongCredentials -> stringResource(R.string.Tv_Server_Error_Credentials)
    ServerCheck.NotSubsonic -> stringResource(R.string.Tv_Server_Error_NotSubsonic)
    is ServerCheck.Http -> stringResource(R.string.Tv_Server_Error_Http, check.status)
    is ServerCheck.ServerError -> stringResource(R.string.Tv_Server_Error_Server, check.message, check.code)
    is ServerCheck.Network -> when (check.kind) {
        NetworkFailure.UNKNOWN_HOST -> stringResource(R.string.Tv_Server_Error_UnknownHost)
        NetworkFailure.UNREACHABLE -> stringResource(R.string.Tv_Server_Error_Unreachable)
        NetworkFailure.TIMEOUT -> stringResource(R.string.Tv_Server_Error_Timeout)
        NetworkFailure.TLS -> stringResource(R.string.Tv_Server_Error_Tls)
        NetworkFailure.CLEARTEXT_BLOCKED -> stringResource(R.string.Tv_Server_Error_Cleartext)
        NetworkFailure.OTHER -> stringResource(R.string.Tv_Server_Error_Other, check.detail)
    }
}

@Preview(
    showBackground = false, showSystemUi = true, device = "id:tv_1080p",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_TELEVISION,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE
)
@Composable
fun CreateLocalProviderDialog(
    setShowDialog: (Boolean) -> Unit = {}
) {
    val rootOptions = remember {
        buildList {
            Environment.getExternalStorageDirectory()?.let { add(it) }
            System.getenv("SECONDARY_STORAGE")
                ?.split(":")
                ?.map { File(it) }
                ?.filter { it.exists() }
                ?.let { addAll(it) }
        }
    }

    var currentDir by remember { mutableStateOf(rootOptions.firstOrNull() ?: File("/")) }

    val entries = remember(currentDir) {
        currentDir.listFiles()
            ?.filter { it.isDirectory && !it.isHidden && it.canRead() }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    val backgroundColor = MaterialTheme.colorScheme.surface
    val (content, action) = remember { FocusRequester.createRefs() }

    Dialog(
        onDismissRequest = { setShowDialog(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {

        BackHandler {
            val parentDir = currentDir.parentFile?.takeIf { it.canRead() }
            if (parentDir != null) {
                currentDir = parentDir
                println("parentDir: $parentDir")
            }
            else {
                setShowDialog(false)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .auroraBackground()
                .padding(horizontal = 48.dp, vertical = 24.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val parentDir = currentDir.parentFile?.takeIf { it.canRead() }
            if (parentDir != null) {
                ListItem(
                    selected = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(content),
                    headlineContent = {
                        Text(
                            text = "..",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回上一级"
                        )
                    },
                    onClick = {
                        currentDir = parentDir
                        content.requestFocus()
                    }
                )
            }

            LazyColumn (
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(content)
                    .focusRestorer(content)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.absolutePath }) { dir ->
                    ListItem(
                        selected = false,
                        modifier = Modifier.fillMaxWidth(),
                        headlineContent = {
                            Text(
                                text = dir.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.s_m_local_filled),
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            currentDir = dir
                            content.requestFocus()
                        }
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListItem(
                    selected = false,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .focusRequester(action),
                    headlineContent = {
                        Text(stringResource(R.string.Action_Add))
                    },
                    supportingContent = {
                        Text(
                            text = currentDir.canonicalPath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        LocalProviderManager.addFolder(currentDir.canonicalPath)
                        setShowDialog(false)
                    }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        content.requestFocus()
    }
    LaunchedEffect(entries) {
        if (entries.isEmpty())
            action.requestFocus()
        else
            content.requestFocus()
    }
}


@Preview(
    showBackground = false, showSystemUi = true, device = "id:tv_1080p",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_TELEVISION,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE
)
@Composable
fun ModifyLrcLibProviderDialog(
    initialUrl: String = "",
    setShowDialog: (Boolean) -> Unit = { }
) {
    val context = LocalContext.current
    val settingsManager = remember { MediaProviderSettingsManager(context) }
    var url: String by remember { mutableStateOf(initialUrl) }
    val coroutineScope = rememberCoroutineScope()

    val backgroundColor = MaterialTheme.colorScheme.surface

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorBorderColor = MaterialTheme.colorScheme.error,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorTextColor = MaterialTheme.colorScheme.error
    )

    Dialog(
        onDismissRequest = { setShowDialog(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .auroraBackground()
                .padding(horizontal = 48.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = {
                        Text(
                            text = "歌词服务地址",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    placeholder = {
                        Text(
                            text = "https://lrclib.net",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    isError = url.isEmpty(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            coroutineScope.launch {
                                settingsManager.setLrcLibEndpoint(url)
                                setShowDialog(false)
                            }
                        }
                    ),
                    colors = textFieldColors
                )

                ListItem(
                    selected = false,
                    headlineContent = { Text(stringResource(R.string.Action_Done)) },
                    onClick = {
                        coroutineScope.launch {
                            settingsManager.setLrcLibEndpoint(url)
                            setShowDialog(false)
                        }
                    }
                )
            }
        }
    }
}
