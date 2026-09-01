package com.feldman.clock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.feldman.motion.MotionButton
import com.feldman.motion.MotionButtonState

@Composable
fun NotificationPermissionRequiredCard(
    message: String,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 24.dp,
                top = 16.dp,
                end = 24.dp,
                bottom = 240.dp
            )
    ) {
        Text(
            text = "Notification permission is off",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = colors.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.error
        )

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(160.dp)
                .background(colors.errorContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(112.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        MotionButton(
            text = "Grant permission",
            onClick = onGrantClick,
            modifier = Modifier.fillMaxWidth(),
            height = 64.dp,
            fontSize = 20.sp,
            defaultState = MotionButtonState(
                backgroundColor = colors.tertiary,
                contentColor = colors.onTertiary
            )
        )
    }
}
