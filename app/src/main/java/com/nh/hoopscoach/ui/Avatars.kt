package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Post
import java.io.File

/**
 * Profile pictures for the social feed. Real accounts show their own; your posts show the picture you
 * picked (a photo from your phone, your jersey, your team's logo or a cartoon); players show their
 * headshot or jersey, teams their logo, and everyone else a real face picked by their handle, so the
 * same fan always looks the same.
 */
@Composable
fun ProfilePic(lg: League?, p: Post, size: Dp = 36.dp) {
    val ctx = LocalContext.current
    val img = remember(p.avatar) { if (p.avatar.isEmpty()) null else Logos.asset(ctx, "social/${p.avatar}.webp") }
    Box(Modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        when {
            img != null -> Image(img, p.name, Modifier.size(size), contentScale = ContentScale.Crop)
            lg != null && p.kind == "self" -> MyAvatar(lg, size)
            lg != null && p.kind in setOf("teammate", "rival", "player") && player(lg, p.name) != null -> {
                val pl = player(lg, p.name)!!
                Box(Modifier.size(size).background(P.surface2), contentAlignment = Alignment.Center) { PlayerJersey(pl, lg.teams.getOrNull(pl.teamId), size) }
            }
            lg != null && p.kind == "team" && team(lg, p.name) != null -> TeamBadge(team(lg, p.name)!!, size)
            p.kind == "news" || p.kind == "league" -> Initials(p.name, size, P.ink)
            else -> RealFace(p.handle.ifEmpty { p.name }, p.name, size)
        }
    }
}

private val WOMEN = setOf("Dana", "Rae", "Priya", "Jules", "Jen", "Jenny", "Jess", "Jessica", "Sarah", "Ashley", "Emily", "Maria", "Megan", "Lauren", "Rachel",
    "Nicole", "Amanda", "Kate", "Katie", "Alyssa", "Brianna", "Hannah", "Olivia", "Sophia", "Mia", "Ava", "Chloe", "Grace", "Erin", "Kelly", "Lisa", "Michelle",
    "Stephanie", "Tiffany", "Vanessa", "Monica", "Tasha", "Keisha", "Aaliyah", "Imani", "Jada", "Nia", "Destiny", "Kayla", "Maya", "Zoe", "Ella", "Natalie",
    "Rebecca", "Amber", "Brittany", "Danielle", "Jasmine", "Samantha", "Courtney", "Ciara", "Shay", "Tamika", "Elena", "Rosa", "Ana", "Lena", "Carmen")
private val NOT_NAMES = setOf("Big", "Courtside", "Coach", "Real", "Hot", "Bench", "Overrated", "Die-hard", "The", "NBA", "NCAA", "Hoops")

/**
 * A real face for an account without a picture: a man's or a woman's by the first name (a coin
 * weighted toward men when there isn't one), picked by the handle so the same fan is always the same
 * person. The portraits are randomuser.me's, hand-picked from UI Faces' authorized set.
 */
fun facePath(seed: String, name: String, save: Long = 0L): String {
    // The save's own seed mixes in, so who looks like whom is different in every save and fixed within one.
    val h = (seed.hashCode().toLong() * 1_000_003L + save).let { (it xor (it ushr 29)) * -0x61c8864680b583ebL }.let { ((it ushr 33) and 0x7FFFFFFF).toInt() }
    val first = name.split(" ", ".").map { it.trim() }.firstOrNull { it.length > 1 && it.first().isUpperCase() && it !in NOT_NAMES }
    val woman = if (first != null) first in WOMEN else h % 10 < 3
    return "faces/${if (woman) "w" else "m"}${"%02d".format(h % 100)}.webp"
}

/** This save's profile-picture seed (see [League.pfpSeed]). */
val LocalPfpSeed = androidx.compose.runtime.compositionLocalOf { 0L }

@Composable
fun RealFace(seed: String, name: String, size: Dp) {
    val ctx = LocalContext.current
    val path = facePath(seed, name, LocalPfpSeed.current)
    val img = remember(path) { Logos.asset(ctx, path) }
    if (img != null) Image(img, name, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop) else Cartoon(seed, size)
}

private fun player(lg: League, name: String) = lg.players.firstOrNull { it.name == name } ?: lg.alumni.firstOrNull { it.name == name }
private fun team(lg: League, name: String) = lg.teams.firstOrNull { it.name.equals(name, true) || it.school.equals(name, true) }

/** The user's own picture, as he chose it. */
@Composable
fun MyAvatar(lg: League, size: Dp) {
    val r = lg.road
    val h = lg.hero
    val ctx = LocalContext.current
    when (r?.avatarStyle) {
        "photo" -> {
            val bmp = remember(r.avatarFile) {
                runCatching { android.graphics.BitmapFactory.decodeFile(File(ctx.filesDir, r.avatarFile).path)?.asImageBitmap() }.getOrNull()
            }
            if (bmp != null) Image(bmp, "Your profile picture", Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
            else Cartoon(h?.name ?: "you", size)
        }
        "logo" -> lg.teams.getOrNull(h?.teamId ?: -1)?.let { TeamBadge(it, size) } ?: Cartoon(h?.name ?: "you", size)
        "cartoon" -> Cartoon(h?.name ?: "you", size)
        else -> if (h != null) Box(Modifier.size(size).clip(CircleShape).background(P.surface2), contentAlignment = Alignment.Center) { PlayerJersey(h, lg.teams.getOrNull(h.teamId), size) }
        else Cartoon("you", size)
    }
}

@Composable
private fun Initials(name: String, size: Dp, color: Color) {
    val initials = name.split(" ").filter { it.isNotEmpty() && it.first().isLetter() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Text(initials, style = T.label.copy(fontSize = (size.value * 0.33f).sp), color = color)
    }
}

private val BACKS = listOf(0xFF3B7DD8, 0xFFD64545, 0xFF2E9E5B, 0xFFD89A1E, 0xFF7A4FD8, 0xFF1F9BA8, 0xFFE0607E, 0xFF5C6B7A, 0xFF8C6D3F, 0xFF2D3A8C)
private val SKIN = listOf(0xFFF5D0B5, 0xFFE8B98F, 0xFFC68A5E, 0xFF9A6440, 0xFF6B4228)
private val HAIR = listOf(0xFF1B1B1B, 0xFF3D2817, 0xFF6B4A2B, 0xFFC9A66B, 0xFF8A8A8A)
private val SHIRT = listOf(0xFFFFFFFF, 0xFF111111, 0xFFF2C14E, 0xFF2463EB, 0xFFC62828, 0xFF2E7D32, 0xFF6A1B9A, 0xFFEF6C00)

/** A cartoon face from a seed: background, skin, hair (or a cap), and a shirt. The same seed always draws the same person. */
@Composable
fun Cartoon(seed: String, size: Dp) {
    val h = seed.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    fun pick(list: List<Long>, k: Int) = Color(list[(h / k) % list.size])
    val back = pick(BACKS, 1)
    val skin = pick(SKIN, 7)
    val hair = pick(HAIR, 31)
    val shirt = pick(SHIRT, 131)
    val cap = (h / 997) % 4 == 0
    val bald = !cap && (h / 4999) % 6 == 0
    Canvas(Modifier.size(size).clip(CircleShape).background(back)) {
        val w = this.size.width
        // Shoulders and shirt.
        drawOval(shirt, topLeft = Offset(w * 0.12f, w * 0.68f), size = Size(w * 0.76f, w * 0.62f))
        // Neck and head.
        drawRect(skin, topLeft = Offset(w * 0.43f, w * 0.55f), size = Size(w * 0.14f, w * 0.16f))
        drawCircle(skin, radius = w * 0.2f, center = Offset(w * 0.5f, w * 0.43f))
        when {
            cap -> {
                drawArc(shirt, 180f, 180f, true, topLeft = Offset(w * 0.29f, w * 0.2f), size = Size(w * 0.42f, w * 0.34f))
                drawRect(shirt, topLeft = Offset(w * 0.42f, w * 0.33f), size = Size(w * 0.36f, w * 0.05f))
            }
            !bald -> drawArc(hair, 180f, 180f, true, topLeft = Offset(w * 0.3f, w * 0.22f), size = Size(w * 0.4f, w * 0.3f))
        }
        // Eyes.
        drawCircle(Color(0xFF1B1B1B), radius = w * 0.022f, center = Offset(w * 0.43f, w * 0.45f))
        drawCircle(Color(0xFF1B1B1B), radius = w * 0.022f, center = Offset(w * 0.57f, w * 0.45f))
    }
}

/** The top of the social feed: his picture, name, handle and followers, and the way to change the picture. */
@Composable
fun ProfileCard(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.hero ?: return
    var choosing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setProfilePhoto(uri)
    }
    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
    Panel {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(P.surface2), contentAlignment = Alignment.Center) { MyAvatar(lg, 72.dp) }
            androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(h.name, style = T.head, color = P.ink)
                Text("@" + h.name.replace(" ", "").replace(".", "").lowercase(), style = T.small, color = P.faint)
                Text("${com.nh.hoopscoach.game.Road.count(r.followers)} followers", style = T.small, color = P.sub)
            }
            SmallButton("Change picture") { choosing = true }
        }
    }
    if (choosing) androidx.compose.material3.AlertDialog(
        onDismissRequest = { choosing = false }, containerColor = P.surface,
        title = { Text("Profile picture", style = T.head, color = P.ink) },
        text = {
            androidx.compose.foundation.layout.Column {
                for ((style, label) in listOf("photo" to "Choose a photo from your phone", "" to "Your portrait", "logo" to "Your team's logo", "cartoon" to "A cartoon")) {
                    Text(
                        (if (r.avatarStyle == style || (style == "photo" && r.avatarStyle == "photo")) "● " else "○ ") + label,
                        style = T.bodyB, color = P.ink,
                        modifier = Modifier.fillMaxWidth().clickable {
                            choosing = false
                            if (style == "photo") picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else vm.act { it.road?.avatarStyle = style }
                        }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ choosing = false }) { Text("Close", color = P.sub) } },
    )
}
