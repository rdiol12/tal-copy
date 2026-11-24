package com.tal.workhours

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class HomeSyncActivity: AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(systemLanguageContext(newBase)) }
    override fun onResume(){super.onResume();HomeSync.foreground(this)}
    override fun onPause(){HomeSync.background();super.onPause()}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);AppText.bind(this)
        setContent {
            val state by HomeSync.status.collectAsState()
            val diagnostic by HomeSync.diagnostic.collectAsState()
            val scope=rememberCoroutineScope()
            var address by remember { mutableStateOf(HomeSync.url(this)) }
            // Secret is intentionally not saveable and never included in backups.
            var secret by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var problem by remember { mutableStateOf<String?>(null) }
            var enabled by remember { mutableStateOf(HomeSync.enabled(this)) }
            val d by WorkRepository.get(this).state.collectAsState()
            WorkHoursTheme(d?.settings?.appearance ?: "SYSTEM") {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text(tr(R.string.home_sync_title),style=MaterialTheme.typography.headlineSmall)
                        Text(tr(R.string.home_sync_hint))
                        OutlinedTextField(address,{address=it},label={Text(tr(R.string.home_sync_address))},singleLine=true,enabled=!busy&&!enabled,modifier=Modifier.fillMaxWidth())
                        if(!enabled) OutlinedTextField(secret,{secret=it},label={Text(tr(R.string.home_sync_key))},singleLine=true,visualTransformation=PasswordVisualTransformation(),enabled=!busy,modifier=Modifier.fillMaxWidth())
                        Text(tr(when(state){"working"->R.string.home_sync_working;"ok"->R.string.home_sync_ok;"conflict"->R.string.home_sync_conflict;"auth"->R.string.home_sync_auth;"identity"->R.string.home_sync_identity;"error"->R.string.home_sync_error;"pending"->R.string.home_sync_pending;else->R.string.home_sync_off}))
                        if(diagnostic.isNotBlank()) Text(diagnostic,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                        problem?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                        Button(enabled=!busy,onClick={scope.launch {
                            busy=true;problem=null
                            try {
                                if(!enabled) { HomeSync.configure(this@HomeSyncActivity,address,secret);secret="" } else HomeSync.sync(this@HomeSyncActivity)
                                enabled=HomeSync.enabled(this@HomeSyncActivity);HomeSync.foreground(this@HomeSyncActivity)
                            } catch(e:Exception){problem=e.message ?: tr(R.string.home_sync_error)}
                            finally { enabled=HomeSync.enabled(this@HomeSyncActivity);busy=false }
                        }}){Text(tr(if(enabled)R.string.home_sync_now else R.string.home_sync_connect))}
                        if(enabled) OutlinedButton(enabled=!busy,onClick={scope.launch {HomeSync.disconnect(this@HomeSyncActivity);enabled=false}}){Text(tr(R.string.home_sync_disconnect))}
                        Text(tr(R.string.home_sync_backup_hint),style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={finish()}){Text(tr(R.string.home_sync_back))}
                    }
                }
            }
        }
    }
}
