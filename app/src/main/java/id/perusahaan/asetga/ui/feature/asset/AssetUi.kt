package id.perusahaan.asetga.ui.feature.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import id.perusahaan.asetga.domain.calc.WarrantyStatus
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.ui.components.StatusChip
import id.perusahaan.asetga.ui.theme.AppTheme
import java.io.File

@Composable
fun ConditionChip(condition: AssetCondition, modifier: Modifier = Modifier) {
    val s = AppTheme.status
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (condition) {
        AssetCondition.BAIK -> s.successContainer to s.onSuccessContainer
        AssetCondition.RUSAK -> s.dangerContainer to s.onDangerContainer
    }
    StatusChip(condition.label, bg, fg, modifier)
}

@Composable
fun AssetStatusChip(status: AssetStatus, modifier: Modifier = Modifier) {
    val s = AppTheme.status
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        AssetStatus.DIGUNAKAN -> cs.primaryContainer to cs.onPrimaryContainer
        AssetStatus.TERSEDIA -> cs.secondaryContainer to cs.onSecondaryContainer
        AssetStatus.DALAM_PERBAIKAN -> s.warningContainer to s.onWarningContainer
        AssetStatus.PROPOSE_WRITE_OFF -> cs.tertiaryContainer to cs.onTertiaryContainer
        AssetStatus.WRITE_OFF -> s.dangerContainer to s.onDangerContainer
        // Status tambahan dari master.
        else -> cs.surfaceContainerHigh to cs.onSurfaceVariant
    }
    StatusChip(status.label, bg, fg, modifier)
}

@Composable
fun WarrantyChip(status: WarrantyStatus, modifier: Modifier = Modifier) {
    val s = AppTheme.status
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        WarrantyStatus.NONE -> cs.surfaceContainerHigh to cs.onSurfaceVariant
        WarrantyStatus.ACTIVE -> s.successContainer to s.onSuccessContainer
        WarrantyStatus.EXPIRING -> s.warningContainer to s.onWarningContainer
        WarrantyStatus.EXPIRED -> s.dangerContainer to s.onDangerContainer
    }
    StatusChip("Garansi: ${status.label}", bg, fg, modifier)
}

@Composable
fun AssetThumb(file: File?, size: Dp = 52.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
