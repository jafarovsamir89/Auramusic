package az.simplesoft.aura.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.simplesoft.aura.assistant.chatscript.BrainResult
import az.simplesoft.aura.assistant.chatscript.ChatScriptBrain
import az.simplesoft.aura.assistant.chatscript.ChatScriptVariant
import kotlinx.coroutines.launch

private data class LabMessage(val fromUser: Boolean, val text: String, val latencyMs: Long = 0L, val action: String? = null)

@Composable
fun ChatScriptLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brain = remember(context) { ChatScriptBrain(context) }
    var variant by remember { mutableStateOf(ChatScriptVariant.ENGLISH) }
    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(emptyList<LabMessage>()) }
    var lastResult by remember { mutableStateOf<BrainResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun accept(result: BrainResult) {
        lastResult = result
        if (result.error == null && result.text.isNotBlank()) {
            messages = messages + LabMessage(false, result.text, result.latencyMs, result.action)
        }
    }

    DisposableEffect(brain) { onDispose { brain.close() } }
    LaunchedEffect(variant) {
        loading = true
        messages = emptyList()
        accept(brain.startSession(variant, "lab_user"))
        loading = false
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            Column(Modifier.weight(1f)) {
                Text("ChatScript Lab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Изолированный эксперимент · DEBUG", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                scope.launch { loading = true; accept(brain.reset()); messages = emptyList(); loading = false }
            }) { Icon(Icons.Rounded.DeleteSweep, "Сбросить") }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Brain variant", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChatScriptVariant.entries.forEach { option ->
                                FilterChip(
                                    selected = variant == option,
                                    onClick = { variant = option },
                                    label = { Text(option.label, fontSize = 11.sp) }
                                )
                            }
                        }
                        Text("Официальный ChatScript upstream · arm64 JNI · offline", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        lastResult?.let { result ->
                            Text("Последний ответ: ${result.latencyMs} мс${result.action?.let { " · action $it" } ?: ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            result.error?.let { Text("Ошибка: $it", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        }
                    }
                }
            }
            items(messages) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (message.fromUser) "YOU" else "AURA", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(message.text)
                        if (!message.fromUser) {
                            Text("${message.latencyMs} ms${message.action?.let { " · __ACTION__:$it" } ?: ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Спроси AURA…") },
                singleLine = true,
                enabled = !loading
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        draft = ""
                        messages = messages + LabMessage(true, text)
                        scope.launch { loading = true; accept(brain.send(text)); loading = false }
                    }
                }) { Icon(Icons.Rounded.Send, "Отправить") }
            }
        }
    }
}
