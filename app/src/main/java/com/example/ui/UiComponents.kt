package com.example.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.*

@Composable
fun ArtButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = PrimaryYellow,
        contentColor = DeepBlack,
        disabledContainerColor = MutedGray,
        disabledContentColor = DeepBlack
    ),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = colors,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun ArtOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    borderColor: Color = PrimaryYellow,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PrimaryYellow
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun ArtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MutedGray) },
        singleLine = singleLine,
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryYellow,
            unfocusedBorderColor = BorderGray,
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            cursorColor = PrimaryYellow,
            focusedLabelColor = PrimaryYellow
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ArtCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = BorderStroke(1.dp, BorderGray),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        border = border,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        content = content
    )
}

@Composable
fun WarningBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

fun showDatePicker(context: Context, initialDateStr: String, onDateSelected: (String) -> Unit) {
    val calendar = Calendar.getInstance()
    if (initialDateStr.isNotBlank()) {
        try {
            val parts = initialDateStr.split("-")
            if (parts.size == 3) {
                calendar.set(Calendar.YEAR, parts[0].toInt())
                calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                calendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
            }
        } catch (_: Exception) {}
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, monthOfYear, dayOfMonth ->
            val formattedDate = String.format(Locale.US, "%d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
            onDateSelected(formattedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    datePickerDialog.show()
}

@Composable
fun TabIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(3.dp)
            .fillMaxWidth()
            .background(if (selected) PrimaryYellow else Color.Transparent)
    )
}
