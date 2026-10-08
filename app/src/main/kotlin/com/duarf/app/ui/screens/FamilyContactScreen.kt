package com.duarf.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.duarf.app.R
import com.duarf.data.repo.FamilyContact

/** Name and number of someone to call before acting on a warning. Used in setup and in Settings. */
@Composable
fun FamilyContactForm(
    contact: FamilyContact?,
    onSave: (name: String, number: String) -> Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable(contact) { mutableStateOf(contact?.name ?: "") }
    var number by rememberSaveable(contact) { mutableStateOf(contact?.number ?: "") }
    var invalid by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; saved = false },
            label = { Text(stringResource(R.string.family_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = number,
            onValueChange = { number = it; invalid = false; saved = false },
            label = { Text(stringResource(R.string.family_number_label)) },
            singleLine = true,
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.family_number_invalid)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val ok = onSave(name.ifBlank { number }, number)
                    invalid = !ok
                    saved = ok
                },
                enabled = number.isNotBlank(),
                modifier = Modifier.heightIn(min = 52.dp)
            ) {
                Text(stringResource(if (saved) R.string.family_saved else R.string.btn_save))
            }
            if (contact != null) {
                OutlinedButton(
                    onClick = {
                        onRemove()
                        name = ""
                        number = ""
                        saved = false
                    },
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.btn_remove))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyContactScreen(
    contact: FamilyContact?,
    onSave: (name: String, number: String) -> Boolean,
    onRemove: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.family_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.family_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FamilyContactForm(contact = contact, onSave = onSave, onRemove = onRemove)
        }
    }
}
