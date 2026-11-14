package com.lembar.journal.ui.insight

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lembar.journal.ui.components.PrimaryButton
import com.lembar.journal.ui.components.SecondaryButton
import com.lembar.journal.ui.theme.Lembar

/**
 * Crisis support. Numbers verified against Kemenkes sources (Nov 2026):
 * 119 ext. 8 = Healing119 / SEJIWA suicide-prevention line, free; chat at healing119.id.
 * Opening the dialer or browser is always the user's choice; nothing is sent automatically.
 */
object Crisis {
    const val PHONE = "119"
    const val WEB = "https://healing119.id"
}

@Composable
fun SupportCard(modifier: Modifier = Modifier, compact: Boolean = false, singleEntry: Boolean = false) {
    val c = Lembar.colors
    val t = Lembar.type
    val context = LocalContext.current
    val warm = Color(0xFFFFE9DA)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (c.isDark) Color(0xFF2A1C12) else warm)
            .border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text("Kamu tidak sendirian", style = t.title)
        Spacer(Modifier.height(6.dp))
        Text(
            if (compact) "Kalau sedang terpikir untuk menyakiti diri atau mengakhiri hidup, bicaralah dengan seseorang sekarang."
            else (if (singleEntry) "Tulisan ini" else "Ada tulisanmu yang") + " menyebut keinginan untuk mati atau menyakiti diri. Perasaan seperti ini bisa dibicarakan, dan ada orang terlatih yang siap mendengar, gratis dan tanpa perlu menyebut nama.",
            style = t.bodySmall.copy(color = c.ink),
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            "Telepon 119 (ekstensi 8)",
            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Crisis.PHONE}"))) },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.Call,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            "Chat di healing119.id",
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Crisis.WEB))) } },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Outlined.Chat,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Dalam bahaya saat ini juga? Telepon 119 atau datang ke IGD rumah sakit terdekat. Kamu juga bisa menghubungi orang yang kamu percaya dan minta ditemani.",
            style = t.caption.copy(color = c.ink),
        )
    }
}

/** Shown after saving an entry that mentions suicide or self-harm, before any XP reward. */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Lembar.colors.surface)
                .padding(16.dp),
        ) {
            Text("Terima kasih sudah jujur menuliskannya.", style = Lembar.type.headline, modifier = Modifier.padding(6.dp))
            Spacer(Modifier.height(8.dp))
            SupportCard(compact = true)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Tutup", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
