package com.cabin.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cabin.app.data.ServiceLocator
import com.cabin.app.ui.common.CircleButton
import com.cabin.app.ui.common.MessageDialog
import com.cabin.app.ui.common.OTag
import com.cabin.app.ui.common.SoftCard
import com.cabin.app.ui.common.SoftError
import com.cabin.app.ui.common.SoftField
import com.cabin.app.ui.common.SoftHeader
import com.cabin.app.ui.common.SoftLink
import com.cabin.app.ui.common.SoftPhoto
import com.cabin.app.ui.common.SoftPrimaryButton
import com.cabin.app.ui.common.SoftRadius
import com.cabin.app.ui.common.SoftRow
import com.cabin.app.ui.common.SoftSecondaryButton
import com.cabin.app.ui.common.SoftSegment
import com.cabin.app.ui.common.VTag
import com.cabin.app.ui.common.Wordmark
import com.cabin.app.ui.common.softClick
import com.cabin.app.ui.common.softShadowRow
import com.cabin.app.ui.theme.SoftClay
import com.cabin.app.ui.theme.SoftDivider
import com.cabin.app.ui.theme.SoftInk
import com.cabin.app.ui.theme.SoftPale
import com.cabin.app.ui.theme.SoftSecondary
import com.cabin.app.ui.theme.SoftText
import com.cabin.app.ui.theme.SoftTextSoft
import com.cabin.app.ui.theme.SoftTile
import com.cabin.app.ui.theme.SoftType
import com.cabin.app.ui.theme.soft
import com.cabin.app.util.Format

@Composable
fun AuthFlowScreen(viewModel: AuthViewModel = viewModel()) {
    val user by ServiceLocator.repository.user.collectAsState()
    LaunchedEffect(user == null) { if (user == null) viewModel.reset() }

    // Form fields live here so rotation keeps them; the step lives in the view model.
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var licence by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isAgent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }

    when (viewModel.step) {
        AuthStep.WELCOME -> WelcomeScreen(viewModel)
        AuthStep.LOGIN -> LoginScreen(viewModel, email, { email = it }, password, { password = it })
        AuthStep.CREATE -> CreateScreen(
            viewModel,
            name, { name = it }, phone, { phone = it }, licence, { licence = it },
            email, { email = it }, password, { password = it }, isAgent, { isAgent = it },
        )
        AuthStep.CODE -> CodeScreen(viewModel, code, { code = it })
        AuthStep.DONE -> DoneScreen(viewModel)
    }

    viewModel.verificationMessage?.let { message ->
        MessageDialog(title = "Verification", message = message, onDismiss = viewModel::acknowledgeVerification)
    }
}

// MARK: Welcome

@Composable
private fun WelcomeScreen(vm: AuthViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadHero() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = 24.dp, bottom = 12.dp),
    ) {
        Wordmark()

        Text(
            "A place you can trust,\nbefore you visit.",
            style = soft(42, letterSpacing = (-0.9).sp),
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            "Every listing is screened before it earns a badge. Browse verified homes for sale or rent across South Metro Manila, Laguna and Cavite.",
            style = SoftType.bodyLight,
            color = SoftSecondary,
            modifier = Modifier.padding(top = 14.dp),
        )

        // The photo gives way first on shorter screens so the copy and buttons never do.
        Box(
            modifier = Modifier
                .padding(top = 22.dp)
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(min = 100.dp, max = 180.dp),
        ) {
            SoftPhoto(url = vm.heroUrl, radius = 26.dp, modifier = Modifier.fillMaxSize())
            VTag(
                text = vm.verifiedCount?.takeIf { it > 0 }?.let { "$it verified nearby" } ?: "Verified homes nearby",
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        SoftError(vm.error, modifier = Modifier.padding(bottom = 8.dp))

        SoftPrimaryButton("Create account", onClick = { vm.go(AuthStep.CREATE) }, large = true, modifier = Modifier.fillMaxWidth())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(SoftDivider))
            Text("or continue with", style = SoftType.footnote, color = SoftSecondary)
            Box(Modifier.weight(1f).height(1.dp).background(SoftDivider))
        }
        ProviderButtons(vm, context)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            SoftLink("Log in", onClick = { vm.go(AuthStep.LOGIN) })
            Text("·", style = SoftType.caption, color = SoftSecondary)
            SoftLink("Continue as demo@cabin.app", onClick = vm::loginAsDemo, muted = true)
        }
    }
}

/** Google (white, four-colour G) and Apple (black) side by side, as in the design. */
@Composable
private fun ProviderButtons(vm: AuthViewModel, context: android.content.Context) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ProviderButton(
            title = "Google",
            loading = vm.loading,
            onClick = { vm.signInWithGoogle(context) },
            modifier = Modifier.weight(1f),
        ) { GoogleMark(Modifier.size(20.dp)) }
        ProviderButton(
            title = "Apple",
            dark = true,
            onClick = vm::signInWithApple,
            modifier = Modifier.weight(1f),
        ) { AppleMark(Modifier.size(20.dp)) }
    }
}

@Composable
private fun ProviderButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    loading: Boolean = false,
    mark: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .clip(CircleShape)
            .background(if (dark) SoftInk else Color.White)
            .softClick(!loading, onClick)
            .padding(vertical = 16.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = if (dark) Color.White else SoftInk, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            mark()
            Text(title, style = soft(16, FontWeight.Normal), color = if (dark) Color.White else SoftText)
        }
    }
}

/** Google's four-colour "G", drawn as ring segments plus the bar so no image asset is needed. */
@Composable
fun GoogleMark(modifier: Modifier = Modifier) {
    val red = Color(0xFFEA4335)
    val yellow = Color(0xFFFBBC05)
    val green = Color(0xFF34A853)
    val blue = Color(0xFF4285F4)
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = s * 9.5f / 48f
        val inset = stroke / 2
        val arcSize = Size(s - stroke, s - stroke)
        val topLeft = Offset(inset, inset)
        fun seg(color: Color, start: Float, sweep: Float) =
            drawArc(color, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        seg(red, -153f, 105f)      // top
        seg(yellow, 153f, 54f)     // left
        seg(green, 49.5f, 103.5f)  // bottom
        seg(blue, 0f, 49.5f)       // right
        drawRect(blue, topLeft = Offset(s / 2, s * 20f / 48f), size = Size(s / 2, s * 9f / 48f))
    }
}

/** Apple logo approximation: no vector asset in the platform icon set, so draw the silhouette. */
@Composable
fun AppleMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val p = androidx.compose.ui.graphics.Path().apply {
            // Body
            moveTo(s * 0.68f, s * 0.53f)
            cubicTo(s * 0.68f, s * 0.43f, s * 0.76f, s * 0.38f, s * 0.77f, s * 0.37f)
            cubicTo(s * 0.72f, s * 0.30f, s * 0.64f, s * 0.29f, s * 0.62f, s * 0.29f)
            cubicTo(s * 0.55f, s * 0.28f, s * 0.49f, s * 0.33f, s * 0.46f, s * 0.33f)
            cubicTo(s * 0.43f, s * 0.33f, s * 0.38f, s * 0.29f, s * 0.32f, s * 0.29f)
            cubicTo(s * 0.25f, s * 0.29f, s * 0.19f, s * 0.34f, s * 0.15f, s * 0.40f)
            cubicTo(s * 0.08f, s * 0.53f, s * 0.13f, s * 0.72f, s * 0.20f, s * 0.82f)
            cubicTo(s * 0.24f, s * 0.87f, s * 0.28f, s * 0.93f, s * 0.34f, s * 0.92f)
            cubicTo(s * 0.39f, s * 0.92f, s * 0.41f, s * 0.89f, s * 0.47f, s * 0.89f)
            cubicTo(s * 0.53f, s * 0.89f, s * 0.55f, s * 0.92f, s * 0.61f, s * 0.92f)
            cubicTo(s * 0.67f, s * 0.92f, s * 0.71f, s * 0.87f, s * 0.74f, s * 0.82f)
            cubicTo(s * 0.77f, s * 0.78f, s * 0.79f, s * 0.74f, s * 0.80f, s * 0.70f)
            cubicTo(s * 0.69f, s * 0.66f, s * 0.68f, s * 0.56f, s * 0.68f, s * 0.53f)
            close()
            // Leaf
            moveTo(s * 0.58f, s * 0.22f)
            cubicTo(s * 0.61f, s * 0.19f, s * 0.63f, s * 0.14f, s * 0.62f, s * 0.08f)
            cubicTo(s * 0.58f, s * 0.09f, s * 0.53f, s * 0.11f, s * 0.50f, s * 0.15f)
            cubicTo(s * 0.47f, s * 0.18f, s * 0.45f, s * 0.23f, s * 0.46f, s * 0.28f)
            cubicTo(s * 0.51f, s * 0.28f, s * 0.55f, s * 0.25f, s * 0.58f, s * 0.22f)
            close()
        }
        drawPath(p, Color.White)
    }
}

// MARK: Log in

@Composable
private fun LoginScreen(
    vm: AuthViewModel,
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
) {
    val context = LocalContext.current
    StepScaffold(label = "", onBack = { vm.go(AuthStep.WELCOME) }) {
        Text("Welcome back", style = SoftType.title)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftField(email, onEmail, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            SoftField(password, onPassword, "Password", secure = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
        }
        SoftError(vm.error)
        SoftPrimaryButton(
            "Log in", onClick = { vm.login(email, password) }, large = true, loading = vm.loading,
            enabled = email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth(),
        )
        ProviderButtons(vm, context)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("New here?", style = SoftType.caption, color = SoftSecondary)
            SoftLink("Create an account", onClick = { vm.go(AuthStep.CREATE) })
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SoftLink("Continue as demo@cabin.app", onClick = vm::loginAsDemo, muted = true)
        }
    }
}

// MARK: Create account (step 1 of 2)

@Composable
private fun CreateScreen(
    vm: AuthViewModel,
    name: String, onName: (String) -> Unit,
    phone: String, onPhone: (String) -> Unit,
    licence: String, onLicence: (String) -> Unit,
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    isAgent: Boolean, onAgent: (Boolean) -> Unit,
) {
    val canRegister = name.isNotBlank() && email.isNotBlank() && password.length >= 6 && !vm.loading

    StepScaffold(label = "Step 1 of 2", onBack = { vm.go(AuthStep.WELCOME) }) {
        Text("Create your account", style = SoftType.title)

        Column {
            Text("I am a", style = SoftType.footnote, color = SoftSecondary, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            SoftSegment(
                options = listOf(false to "Private individual", true to "Real estate agent"),
                selected = isAgent,
                onSelect = onAgent,
            )
            // Anyone can post — 36% of surveyed users are owners/sellers, and nobody wanted an agents-only marketplace.
            Text(
                if (isAgent) "Agents add a PRC licence number to be verified. \"Agent\" is an occupation label, not a trust signal."
                else "Owners, buyers and renters can all post and browse listings.",
                style = SoftType.footnote, color = SoftSecondary,
                modifier = Modifier.padding(horizontal = 6.dp).padding(top = 10.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftField(name, onName, "Full name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            SoftField(phone, onPhone, "Mobile number", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
            if (isAgent) {
                SoftField(licence, onLicence, "PRC licence number", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next))
            }
            SoftField(email, onEmail, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            SoftField(password, onPassword, "Password (6+ characters)", secure = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
        }

        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(SoftInk),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Text(
                "Text me a one-time code to confirm this number. Confirming is what earns the account badge.",
                style = SoftType.footnote, color = SoftSecondary,
            )
        }

        SoftError(vm.error)

        SoftPrimaryButton(
            "Continue",
            onClick = { vm.register(name, email, password, phone, licence, isAgent) },
            large = true, loading = vm.loading, enabled = canRegister,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Already have an account?", style = SoftType.caption, color = SoftSecondary)
            SoftLink("Log in", onClick = { vm.go(AuthStep.LOGIN) })
        }
    }
}

// MARK: Confirm number (step 2 of 2)

@Composable
private fun CodeScreen(vm: AuthViewModel, code: String, onCode: (String) -> Unit) {
    StepScaffold(label = "Step 2 of 2", onBack = { vm.go(AuthStep.CREATE) }) {
        Text("Confirm your number", style = SoftType.title)
        Text(
            "We texted a 6-digit code to ${vm.sentTo ?: vm.phone}. It expires in 10 minutes.",
            style = SoftType.bodyLight, color = SoftSecondary,
        )

        CodeBoxes(code)
        Keypad(code, onCode)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            Text("Didn't get it?", style = SoftType.caption, color = SoftSecondary, modifier = Modifier.weight(1f))
            SoftLink("Send a new code", onClick = vm::resendCode)
        }

        vm.devCode?.let {
            Text("Development server: your code is $it.", style = SoftType.footnote, color = SoftClay, modifier = Modifier.padding(horizontal = 6.dp))
        }
        SoftError(vm.error)

        SoftPrimaryButton(
            "Confirm", onClick = { vm.verifyCode(code) }, large = true, loading = vm.loading,
            enabled = code.length == 6, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Codes are hashed, expire in 10 minutes and allow 5 attempts.",
            style = SoftType.footnote, color = SoftSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SoftLink("Skip for now", onClick = vm::skipCode, muted = true)
        }
    }
}

@Composable
private fun CodeBoxes(code: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(6) { i ->
            val shape = RoundedCornerShape(SoftRadius.codeBox)
            val active = i == code.length
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .then(if (active) Modifier else Modifier.softShadowRow(shape))
                    .clip(shape)
                    .background(Color.White)
                    .then(if (active) Modifier.border(2.dp, SoftInk, shape) else Modifier),
            ) {
                Text(code.getOrNull(i)?.toString() ?: "", style = soft(26))
            }
        }
    }
}

@Composable
private fun Keypad(code: String, onCode: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth().height(232.dp), // 4 rows of 52dp + 3 gaps of 8dp
    ) {
        items(keys) { key ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(if (key.isEmpty()) Color.Transparent else Color.White.copy(alpha = 0.7f))
                    .softClick(key.isNotEmpty()) {
                        when {
                            key == "⌫" -> onCode(code.dropLast(1))
                            code.length < 6 -> onCode(code + key)
                        }
                    },
            ) {
                Text(key, style = soft(20))
            }
        }
    }
}

// MARK: You're in

@Composable
private fun DoneScreen(vm: AuthViewModel) {
    val confirmed = vm.phoneConfirmed
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 12.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp).clip(CircleShape).background(SoftInk)) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text("You're in, ${vm.userFirstName}.", style = soft(40, letterSpacing = (-0.8).sp), modifier = Modifier.padding(top = 28.dp))
        Text(
            if (confirmed) "Your number is confirmed. One more step earns the badge that shows on every listing you post."
            else "Confirm your number from your profile whenever you're ready — that's what earns the account badge.",
            style = SoftType.bodyLight, color = SoftSecondary, modifier = Modifier.padding(top = 12.dp),
        )

        SoftCard(padding = 12.dp, modifier = Modifier.padding(top = 24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftRow(fill = if (confirmed) SoftTile else SoftPale, shadow = false) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).clip(CircleShape).background(if (confirmed) SoftInk else Color.White)) {
                        Icon(if (confirmed) Icons.Outlined.Check else Icons.Outlined.Phone, contentDescription = null, tint = if (confirmed) Color.White else SoftTextSoft, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (confirmed) "Mobile number confirmed" else "Mobile number not confirmed", style = SoftType.body)
                        Text(
                            if (confirmed) "${Format.maskedPhone(vm.phone)} · just now" else "You can do this later from your profile",
                            style = SoftType.footnote, color = SoftSecondary,
                        )
                    }
                }
                SoftRow(fill = SoftPale, shadow = false) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White)) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = SoftTextSoft, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Identity verification", style = SoftType.body)
                        Text(
                            if (vm.isAgent) "Needs your PRC licence number and a short review." else "A short automated review of your account.",
                            style = SoftType.footnote, color = SoftSecondary,
                        )
                    }
                    OTag("Optional")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPrimaryButton("Start browsing", onClick = vm::startBrowsing, large = true, modifier = Modifier.fillMaxWidth())
            SoftSecondaryButton(
                "Request verification", onClick = vm::requestVerification, large = true, loading = vm.loading,
                enabled = confirmed, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "86% of people we surveyed said verification decides whether they trust a listing.",
                style = SoftType.footnote, color = SoftSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** Back circle + step label header, then a scrolling column of content. */
@Composable
private fun StepScaffold(label: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        SoftHeader(
            leading = { CircleButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Back", onClick = onBack) },
            title = { Text(label, style = SoftType.caption, color = SoftSecondary) },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 40.dp),
        ) {
            content()
        }
    }
}
