package io.legado.app.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import io.legado.app.help.drive.DriveLinkResolver
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun AddDriveSourceSheet(
    onDismiss: () -> Unit,
    onAddPublicLink: (url: String, name: String) -> Unit,
    onRequestGoogleAuth: () -> Unit,
    onAddGoogleAccount: (email: String, folderId: String, name: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Public Link
    var publicUrl by remember { mutableStateOf("") }
    var publicName by remember { mutableStateOf("") }
    var detectedProvider by remember { mutableStateOf<String?>(null) }

    var googleEmail by remember { mutableStateOf("") }
    var googleFolderId by remember { mutableStateOf("") }
    var googleSourceName by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Thêm thư viện đám mây",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Google Drive") },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Public Link") },
                    icon = { Icon(Icons.Default.Link, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Google Account
                Text(
                    text = "Đăng nhập tài khoản Google Drive của bạn (Quyền đọc thư mục chọn lọc drive.file)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = onRequestGoogleAuth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đăng nhập bằng Google")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = googleEmail,
                    onValueChange = { googleEmail = it },
                    label = { Text("Email tài khoản") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = googleFolderId,
                    onValueChange = { googleFolderId = it },
                    label = { Text("Mã thư mục (Folder ID - để trống = toàn bộ)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = googleSourceName,
                    onValueChange = { googleSourceName = it },
                    label = { Text("Tên nguồn hiển thị") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (googleEmail.isNotBlank()) {
                            onAddGoogleAccount(googleEmail, googleFolderId, googleSourceName)
                        }
                    },
                    enabled = googleEmail.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lưu nguồn")
                }
            } else {
                // Public Link
                Text(
                    text = "Hỗ trợ link công khai từ Google Drive, OneDrive, Dropbox, hoặc HTTP autoindex.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = publicUrl,
                    onValueChange = {
                        publicUrl = it
                        val res = DriveLinkResolver.resolve(it)
                        detectedProvider = res.getOrNull()?.suggestedName
                    },
                    label = { Text("Dán link chia sẻ công khai") },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.getText()?.let { clip ->
                                publicUrl = clip.text
                                val res = DriveLinkResolver.resolve(clip.text)
                                detectedProvider = res.getOrNull()?.suggestedName
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Dán")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (detectedProvider != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nhận diện: $detectedProvider",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = publicName,
                    onValueChange = { publicName = it },
                    label = { Text("Tên nguồn (tùy chọn)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (publicUrl.isNotBlank()) {
                            onAddPublicLink(publicUrl, publicName)
                        }
                    },
                    enabled = publicUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thêm thư viện")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
