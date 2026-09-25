package com.webdav.player.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.domain.model.WebDavServer

@Composable
fun ServerEditDialog(
    serverToEdit: WebDavServer?,
    testingConnection: Boolean,
    testResult: ConnectionResult?,
    onTestConnection: (WebDavServer) -> Unit,
    onSave: (WebDavServer) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(serverToEdit?.name ?: "") }
    var url by remember { mutableStateOf(serverToEdit?.url ?: "") }
    var port by remember { mutableStateOf(serverToEdit?.port?.toString() ?: "80") }
    var pathPrefix by remember { mutableStateOf(serverToEdit?.pathPrefix ?: "/") }
    var username by remember { mutableStateOf(serverToEdit?.username ?: "") }
    var password by remember { mutableStateOf(serverToEdit?.password ?: "") }
    var allowSelfSigned by remember { mutableStateOf(serverToEdit?.allowSelfSigned ?: false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    fun buildCandidate(): WebDavServer {
        val parsedPort = port.toIntOrNull() ?: if (url.startsWith("https://", ignoreCase = true)) 443 else 80
        return WebDavServer(
            id = serverToEdit?.id ?: 0L,
            name = name.trim(),
            url = url.trim(),
            port = parsedPort,
            pathPrefix = pathPrefix.trim(),
            username = username.trim(),
            password = password,
            allowSelfSigned = allowSelfSigned,
            isDefault = serverToEdit?.isDefault ?: false
        )
    }

    val isInputValid = name.isNotBlank() && url.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (serverToEdit == null) "添加 WebDAV 服务器" else "编辑 WebDAV 服务器",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称 (如: 群晖NAS / AList)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        // Auto adapt default port if scheme changes and user didn't modify port
                        if (it.startsWith("https://", ignoreCase = true) && port == "80") {
                            port = "443"
                        } else if (it.startsWith("http://", ignoreCase = true) && port == "443") {
                            port = "80"
                        }
                    },
                    label = { Text("服务器地址 (如: http://192.168.1.100)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("端口") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = pathPrefix,
                        onValueChange = { pathPrefix = it },
                        label = { Text("路径前缀 (如: /dav)") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码 (可选)") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("信任自签名 SSL 证书", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "适用于内网自签证书或 IP HTTPS 连接 (忽略 SSL 校验)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowSelfSigned,
                        onCheckedChange = { allowSelfSigned = it }
                    )
                }

                // Connection Test Feedback Banner
                if (testingConnection) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在测试 WebDAV 连接...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (testResult != null) {
                    when (testResult) {
                        is ConnectionResult.Success -> {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "成功",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "连接成功！WebDAV PROPFIND 响应正常",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        is ConnectionResult.Failure -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Error,
                                        contentDescription = "失败",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "连接失败: ${testResult.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onTestConnection(buildCandidate()) },
                    enabled = isInputValid && !testingConnection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("测试连接")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(buildCandidate()) },
                enabled = isInputValid
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
