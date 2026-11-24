package com.tal.workhours

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val dayPalette = lightColorScheme(
    primary=Color(0xFF255DD2),onPrimary=Color.White,primaryContainer=Color(0xFFE3ECFF),onPrimaryContainer=Color(0xFF15356C),
    secondary=Color(0xFF087F82),onSecondary=Color.White,secondaryContainer=Color(0xFFDDF3EF),onSecondaryContainer=Color(0xFF124D4B),
    background=Color(0xFFF4F7FB),onBackground=Color(0xFF182333),surface=Color.White,onSurface=Color(0xFF182333),
    surfaceVariant=Color(0xFFEBF0F5),onSurfaceVariant=Color(0xFF4E5D70),outline=Color(0xFF718093),outlineVariant=Color(0xFFDBE3ED),
    error=Color(0xFFB3263D),onError=Color.White,errorContainer=Color(0xFFFFE0E5),onErrorContainer=Color(0xFF74162B))
private val nightPalette = darkColorScheme(
    primary=Color(0xFFADC7FF),onPrimary=Color(0xFF002D73),primaryContainer=Color(0xFF203C67),onPrimaryContainer=Color(0xFFDFE9FF),
    secondary=Color(0xFF80D6CB),onSecondary=Color(0xFF003733),secondaryContainer=Color(0xFF174C49),onSecondaryContainer=Color(0xFFBDEFE7),
    background=Color(0xFF101720),onBackground=Color(0xFFE7EDF4),surface=Color(0xFF19232E),onSurface=Color(0xFFE7EDF4),
    surfaceVariant=Color(0xFF263441),onSurfaceVariant=Color(0xFFB9C6D5),outline=Color(0xFF8D9BAB),outlineVariant=Color(0xFF374757),
    error=Color(0xFFFFB1C0),onError=Color(0xFF650D25),errorContainer=Color(0xFF74263A),onErrorContainer=Color(0xFFFFDFE6))

@Composable
fun WorkHoursTheme(appearance: String, content: @Composable () -> Unit) {
    val dark = when(appearance) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    LaunchedEffect(appearance) {
        val mode=when(appearance) { "DARK" -> AppCompatDelegate.MODE_NIGHT_YES; "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }
        if(AppCompatDelegate.getDefaultNightMode()!=mode) AppCompatDelegate.setDefaultNightMode(mode)
    }
    SideEffect {
        (context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme=if(dark)nightPalette else dayPalette,
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp)),content=content)
}

@Composable
fun AppIcon(drawable: Int, description: String?=null, modifier: Modifier=Modifier) {
    Icon(painterResource(drawable),description,modifier.size(24.dp))
}

@Composable
fun DetailDisclosure(title: String=tr(R.string.ui_more_info), content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state=tr(if(expanded)R.string.ui_collapse else R.string.ui_expand)
    TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics { stateDescription=state }) {
        Text(title,modifier=Modifier.weight(1f))
        AppIcon(if(expanded)R.drawable.ic_expand_less else R.drawable.ic_expand_more)
    }
    AnimatedVisibility(expanded,enter=fadeIn(),exit=fadeOut()) {
        Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable
fun ExpandableCard(title: String, summary: @Composable ColumnScope.() -> Unit={}, details: @Composable ColumnScope.() -> Unit) {
    InfoCard(title) {
        summary()
        DetailDisclosure(tr(R.string.ui_details),content=details)
    }
}

@Composable
fun ThemeSelector(value: String, enabled: Boolean, onChange: (String)->Unit) {
    InfoCard(tr(R.string.ui_appearance)) {
        Column(Modifier.selectableGroup()) {
            listOf("SYSTEM" to R.string.ui_theme_system,"LIGHT" to R.string.ui_theme_light,"DARK" to R.string.ui_theme_dark).forEach { (key,label) ->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=value==key,enabled=enabled,
                    role=Role.RadioButton,onClick={onChange(key)}),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=value==key,onClick=null,enabled=enabled)
                    Text(tr(label),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Text(tr(R.string.ui_theme_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SnapshotHero(summary: PeriodSummary, active: Boolean, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(
        containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_snapshot)
                Spacer(Modifier.width(10.dp))
                Text(tr(R.string.ui_gross_estimate),style=MaterialTheme.typography.titleMedium)
            }
            Text(money(summary.gross,taxCurrency(summary.settings.taxCountry)),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Metric(tr(R.string.text_3eb92e199a),hoursText(summary.totalMs))
                    Metric(tr(R.string.text_dda62e1cc9),hoursText(summary.overtimeMs))
                }
            }
            if(active) Text(tr(R.string.text_d7fec4855e),style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun RecordActions(shift: Shift, enabled: Boolean, onEdit: (Shift)->Unit, onDelete: (Shift)->Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        OutlinedButton(enabled=enabled,onClick={onEdit(shift)}) {
            AppIcon(R.drawable.ic_edit);Spacer(Modifier.width(8.dp));Text(tr(R.string.text_39fe25939b))
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(enabled=enabled,onClick={menu=true}) { AppIcon(R.drawable.ic_more,tr(R.string.ui_record_actions)) }
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                DropdownMenuItem(text={Text(tr(R.string.text_7c8173fafa),color=MaterialTheme.colorScheme.error)},
                    enabled=enabled,onClick={menu=false;onDelete(shift)})
            }
        }
    }
}
