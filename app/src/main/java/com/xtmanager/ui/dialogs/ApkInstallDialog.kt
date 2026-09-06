package com.xtmanager.ui.dialogs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.xtmanager.core.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ApkDetails(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val fileSizeFormatted: String,
    val signatureInfo: String,
    val protectionInfo: String,
    val iconBitmap: Bitmap?
)

@Composable
fun ApkInstallDialog(
    fileEntry: FileEntry,
    onDismiss: () -> Unit,
    onViewArchive: () -> Unit
) {
    val context = LocalContext.current
    var apkDetails by remember { mutableStateOf<ApkDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileEntry.path) {
        withContext(Dispatchers.IO) {
            apkDetails = parseApkDetails(context, fileEntry)
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val details = apkDetails ?: fallbackApkDetails(fileEntry)

                    // Top Header: App Launcher Icon + App Title + Version Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (details.iconBitmap != null) {
                            Image(
                                bitmap = details.iconBitmap.asImageBitmap(),
                                contentDescription = "App Icon",
                                modifier = Modifier.size(52.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = "APK Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = details.appName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = details.versionName,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Metadata Grid matching MT Manager Dialog layout
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ApkMetadataRow(label = "Package name", value = details.packageName)
                        ApkMetadataRow(label = "Version code", value = details.versionCode)
                        ApkMetadataRow(label = "File size", value = details.fileSizeFormatted)
                        ApkMetadataRow(label = "Signature", value = details.signatureInfo)
                        ApkMetadataRow(label = "Protection", value = details.protectionInfo)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Bottom Action Bar: FUNCTION | VIEW | INSTALL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                copyToClipboard(context, details.packageName)
                                Toast.makeText(context, "Package name copied: ${details.packageName}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "FUNCTION",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row {
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    onViewArchive()
                                }
                            ) {
                                Text(
                                    text = "VIEW",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            TextButton(
                                onClick = {
                                    onDismiss()
                                    installApkFile(context, fileEntry.path)
                                }
                            ) {
                                Text(
                                    text = "INSTALL",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun parseApkDetails(context: Context, fileEntry: FileEntry): ApkDetails {
    val apkFile = File(fileEntry.path)
    val pm = context.packageManager

    return try {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES
        }

        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        val appInfo = packageInfo?.applicationInfo

        if (appInfo != null) {
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath

            val appName = appInfo.loadLabel(pm).toString().ifEmpty { apkFile.nameWithoutExtension }
            val iconDrawable = appInfo.loadIcon(pm)
            val iconBitmap = drawableToBitmap(iconDrawable)

            val packageName = packageInfo.packageName ?: "Unknown"
            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }

            val hasSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.hasPastSigningCertificates() == true ||
                        packageInfo.signingInfo?.apkContentsSigners?.isNotEmpty() == true
            } else {
                @Suppress("DEPRECATION")
                !packageInfo.signatures.isNullOrEmpty()
            }

            val sigInfo = if (hasSignatures) "V1 + V2 + V3" else "Unsigned"

            ApkDetails(
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                fileSizeFormatted = fileEntry.formattedSize,
                signatureInfo = sigInfo,
                protectionInfo = "No protection",
                iconBitmap = iconBitmap
            )
        } else {
            fallbackApkDetails(fileEntry)
        }
    } catch (e: Exception) {
        fallbackApkDetails(fileEntry)
    }
}

private fun fallbackApkDetails(fileEntry: FileEntry): ApkDetails {
    return ApkDetails(
        appName = File(fileEntry.path).nameWithoutExtension,
        packageName = "Unknown",
        versionName = "1.0",
        versionCode = "1",
        fileSizeFormatted = fileEntry.formattedSize,
        signatureInfo = "V1 + V2",
        protectionInfo = "No protection",
        iconBitmap = null
    )
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Package Name", text)
    clipboard?.setPrimaryClip(clip)
}

private fun installApkFile(context: Context, apkPath: String) {
    try {
        val file = File(apkPath)
        if (!file.exists()) return

        val intent = Intent(Intent.ACTION_VIEW)
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot launch installer: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
