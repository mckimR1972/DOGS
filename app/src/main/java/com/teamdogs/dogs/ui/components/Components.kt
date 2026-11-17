package com.teamdogs.dogs.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdogs.dogs.ui.theme.DogsTheme

// ---------- Buttons ----------

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                  loading: Boolean = false, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape, contentPadding = PaddingValues(horizontal = 20.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        else {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                    icon: ImageVector? = null, height: Dp = 52.dp, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = height), shape = CircleShape,
        border = BorderStroke(1.dp, DogsTheme.colors.controlOutline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 18.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun InverseButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 40.dp), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface),
        contentPadding = PaddingValues(horizontal = 16.dp)) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)) }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
             color: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: ImageVector? = null) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (trailing != null) Icon(trailing, null, Modifier.size(16.dp))
    }
}

// ---------- Text field ----------

/** Label above the field (always visible), 56dp field, radius 12, inline error with icon, password toggle. */
@Composable
fun DogsTextField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
                  fieldModifier: Modifier = Modifier, enabled: Boolean = true, error: String? = null, isError: Boolean = error != null,
                  helper: String? = null, placeholder: String? = null, email: Boolean = false, password: Boolean = false,
                  singleLine: Boolean = true, minLines: Int = 1, trailing: (@Composable () -> Unit)? = null) {
    var visible by remember { mutableStateOf(false) }
    val colors = DogsTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The visible label is decorative for accessibility; the same label is attached to the editable node below so
        // TalkBack announces it on focus and text-based test selectors (onNodeWithText(label)) hit the input.
        Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = value, onValueChange = onValueChange, enabled = enabled, singleLine = singleLine,
            minLines = minLines, isError = isError,
            modifier = fieldModifier.fillMaxWidth().heightIn(min = 56.dp).semantics { text = AnnotatedString(label) },
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            placeholder = placeholder?.let { { Text(it, color = colors.textTertiary) } },
            keyboardOptions = KeyboardOptions(keyboardType = when {
                password -> KeyboardType.Password; email -> KeyboardType.Email; else -> KeyboardType.Text
            }),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = when {
                password -> ({
                    IconButton(onClick = { visible = !visible }, enabled = enabled) {
                        Icon(if (visible) DogsIcons.EyeOff else DogsIcons.Eye, if (visible) "비밀번호 숨기기" else "비밀번호 보기")
                    }
                })
                trailing != null -> trailing
                else -> null
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                disabledContainerColor = colors.divider,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ))
        when {
            error != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(DogsIcons.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            helper != null -> Text(helper, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
    }
}

// ---------- Avatars & chips ----------

@Composable
fun dogTone(id: String): Pair<Color, Color> {
    val tones = DogsTheme.colors.dogTones
    return tones[Math.floorMod(id.hashCode(), tones.size)]
}

@Composable
fun DogAvatar(name: String, id: String, size: Dp = 32.dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val (bg, fg) = dogTone(id.ifBlank { name })
    Initial(name, bg, fg, size, modifier, ring)
}

@Composable
fun PersonAvatar(name: String, size: Dp = 32.dp, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val c = DogsTheme.colors
    if (highlighted) Initial(name, c.doneContainer, c.onDoneContainer, size, modifier)
    else Initial(name, MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.onSurface, size,
        modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@Composable
private fun Initial(name: String, bg: Color, fg: Color, size: Dp, modifier: Modifier, ring: Color? = null) {
    Box(modifier.size(size).then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
        .clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        // The initial is a graphic sized to its circle, so it must not grow with the user's font scale.
        val fontSize = with(LocalDensity.current) { (size * 0.4f).toSp() }
        Text(name.trim().take(1).ifBlank { "?" }, color = fg, fontWeight = FontWeight.Bold,
            fontSize = fontSize, lineHeight = fontSize, maxLines = 1)
    }
}

/** Dog filter chip: 40dp, avatar 32dp, selected = inverse surface. */
@Composable
fun DogChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dogId: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.inverseSurface else scheme.surfaceContainerLowest
    val fg = if (selected) scheme.inverseOnSurface else scheme.onSurfaceVariant
    Row(modifier.heightIn(min = 40.dp).clip(CircleShape).background(bg)
        .then(if (selected) Modifier else Modifier.border(1.dp, scheme.outlineVariant, CircleShape))
        .clickable(role = Role.Tab, onClick = onClick)
        .semantics { stateDescription = if (selected) "선택됨" else "선택 안 됨" }
        .padding(start = if (dogId != null) 4.dp else 16.dp, end = if (dogId != null) 14.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dogId != null) DogAvatar(label, dogId, 32.dp)
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

// ---------- Layout pieces ----------

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, action: String? = null,
                  onAction: (() -> Unit)? = null, actionEnabled: Boolean = true) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (count != null) Text(" $count", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = DogsTheme.colors.textTertiary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) TextLink(action, onAction, enabled = actionEnabled, trailing = DogsIcons.ChevronRight)
    }
}

/** Top-level tab bar: title + trailing actions. */
@Composable
fun TabTopBar(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            if (subtitle != null) Text("  $subtitle", style = MaterialTheme.typography.labelMedium, color = DogsTheme.colors.textTertiary, maxLines = 1)
        }
        actions()
    }
}

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, enabled: Boolean = true, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = enabled) { Icon(DogsIcons.Back, "뒤로") }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
fun BadgeIconButton(icon: ImageVector, description: String, onClick: () -> Unit, dot: Boolean = false, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Box {
            Icon(icon, description)
            if (dot) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.background).padding(2.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.error))
        }
    }
}

/** Tinted message block: attention (amber) for requests/warnings, done (green) for confirmations. */
@Composable
fun Banner(modifier: Modifier = Modifier, attention: Boolean = true, icon: ImageVector? = null,
           content: @Composable ColumnScope.() -> Unit) {
    val c = DogsTheme.colors
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(if (attention) c.attentionContainer else c.doneContainer).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = if (attention) c.attention else c.onDoneContainer)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Surface block for the single most important item on a screen. Not used for every list. */
@Composable
fun FeatureCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .border(1.dp, DogsTheme.colors.divider, MaterialTheme.shapes.large).padding(20.dp), content = content)
}

@Composable
fun ListDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, 1.dp, DogsTheme.colors.divider)

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RailMotif(Modifier.width(200.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(8.dp)); action() }
    }
}

// ---------- Care task pieces ----------

/** Check target: 26dp ring inside a 48dp touch area. */
@Composable
fun CheckRing(done: Boolean, late: Boolean, onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier,
              enabled: Boolean = true) {
    val c = DogsTheme.colors
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Box(Modifier.size(26.dp).clip(CircleShape).then(
            when {
                done -> Modifier.background(c.done)
                late -> Modifier.background(c.attentionContainer).border(2.dp, c.attention, CircleShape)
                else -> Modifier.border(1.75.dp, c.controlOutline, CircleShape)
            }).semantics { this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
            if (done) Icon(DogsIcons.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun DoneBadge(text: String, modifier: Modifier = Modifier) {
    val c = DogsTheme.colors
    Row(modifier.heightIn(min = 28.dp).clip(CircleShape).background(c.doneContainer).padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center) { Icon(DogsIcons.Check, null, Modifier.size(12.dp), tint = c.onDoneContainer) }
        Text(text, style = MaterialTheme.typography.labelSmall, color = c.onDoneContainer, maxLines = 1)
    }
}

/**
 * A care task row: time · title + meta · trailing check. 68dp min height, grows with font scale.
 * [doneText] is shown as a badge instead of the ring when the task is completed by someone.
 */
@Composable
fun TaskRowItem(time: String, title: String, meta: String, done: Boolean, late: Boolean, onToggle: () -> Unit,
                modifier: Modifier = Modifier, dogId: String? = null, dogName: String? = null, doneText: String? = null,
                toggleTag: String? = null, enabled: Boolean = true) {
    val c = DogsTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(time.ifBlank { "미정" }, Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
            fontWeight = FontWeight.SemiBold, color = if (late && !done) c.attention else c.textTertiary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (done) c.textTertiary else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null)
            if (meta.isNotBlank() || dogName != null) Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dogId != null && dogName != null) DogAvatar(dogName, dogId, 20.dp, ring = MaterialTheme.colorScheme.background)
                Text(meta, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (late && !done) c.attention else c.textTertiary)
            }
        }
        val tagged = if (toggleTag != null) Modifier.testTag(toggleTag) else Modifier
        if (done && doneText != null) Box(tagged.heightIn(min = 48.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClickLabel = "완료 취소", onClick = onToggle)
            .semantics { stateDescription = "완료됨" }, contentAlignment = Alignment.Center) { DoneBadge(doneText) }
        else CheckRing(done, late, onToggle, if (done) "$title 완료 취소" else "$title 완료", tagged, enabled)
    }
}
