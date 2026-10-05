package nl.bluecard.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import nl.bluecard.app.res.pluralStringResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.ui.text.RequirementTexts
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import nl.bluecard.app.ui.components.feltTable
import nl.bluecard.app.ui.components.CardBack
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.components.PlayingCard
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.store.Products
import nl.bluecard.app.ui.theme.CardBackSkin
import nl.bluecard.app.ui.theme.Skins
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.app.ui.theme.TableSkin
import nl.bluecard.engine.model.Card

/** Card backs and table cloths, unlocked by playing or bought in the shop (with the shop itself on top). */
@Composable
fun SkinsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val progress by container.myProgress.collectAsStateWithLifecycle()
    val entitlements by container.entitlements.collectAsStateWithLifecycle()
    val storeReady by container.store.ready.collectAsStateWithLifecycle()
    val storeProducts by container.store.products.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    StoreMessages(snackbar)
    val wins = progress.wins
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    // Tapping a skin shows it on a table you can tilt; choosing happens there.
    var previewBack by remember { mutableStateOf<CardBackSkin?>(null) }
    var previewTable by remember { mutableStateOf<TableSkin?>(null) }
    previewBack?.let { back ->
        CardBackPreviewDialog(
            back = back,
            canChoose = Skins.isUnlocked(back, progress, entitlements),
            onChoose = {
                scope.launch { container.settingsRepository.update { it.copy(cardBackSkin = back.id) } }
                previewBack = null
            },
            onDismiss = { previewBack = null },
        )
    }
    previewTable?.let { table ->
        SkinPreviewDialog(
            back = Skins.cardBack,
            table = table,
            canChoose = Skins.isUnlocked(table, progress, entitlements),
            onChoose = {
                scope.launch { container.settingsRepository.update { it.copy(tableSkin = table.id) } }
                previewTable = null
            },
            onDismiss = { previewTable = null },
        )
    }

    FeltScreen(title = stringResource(R.string.skins_title), onBack = onBack, snackbarHostState = snackbar) {
        FeltHeader(
            title = stringResource(R.string.skins_header),
            subtitle = pluralStringResource(R.plurals.skins_wins, wins, wins),
            cards = listOf(Card.of("JH"), Card.of("AS"), Card.of("KD")),
        )
        ShopPanel()
        val settings by container.settings.collectAsStateWithLifecycle()
        EmojiPanel(progress, settings.reactionEmojis) { chosen ->
            scope.launch { container.settingsRepository.update { it.copy(reactionEmojis = chosen) } }
        }
        FeltPanel(title = stringResource(R.string.skins_backs)) {
            SkinGrid(CardBackSkin.entries) { skin, modifier ->
                SkinTile(
                    modifier = modifier,
                    name = stringResource(backName(skin)),
                    chosen = Skins.cardBack == skin,
                    unlocked = Skins.isUnlocked(skin, progress, entitlements),
                    requirement = RequirementTexts.progress(res, skin.requirement, progress),
                    premiumPrice = skin.premiumPriceCents?.let { cents ->
                        storeProducts[Products.skin(skin.id)]?.price ?: stringResource(R.string.skins_price, euros(cents))
                    },
                    onBuy = if (storeReady && Products.skin(skin.id) in storeProducts) ({ container.store.buy(Products.skin(skin.id)) }) else null,
                    onChoose = { previewBack = skin },
                ) { CardBack(58.dp, skin = skin) }
            }
        }
        FeltPanel(title = stringResource(R.string.skins_tables)) {
            SkinGrid(TableSkin.entries) { skin, modifier ->
                SkinTile(
                    modifier = modifier,
                    name = stringResource(tableName(skin)),
                    chosen = Skins.table == skin,
                    unlocked = Skins.isUnlocked(skin, progress, entitlements),
                    requirement = RequirementTexts.progress(res, skin.requirement, progress),
                    premiumPrice = if (skin.isPremium) storeProducts[Products.skin(skin.id)]?.price else null,
                    onBuy = if (skin.isPremium && storeReady && Products.skin(skin.id) in storeProducts) ({ container.store.buy(Products.skin(skin.id)) }) else null,
                    onChoose = { previewTable = skin },
                ) { TablePreview(skin) }
            }
        }
    }
}

/**
 * Tiles in equal columns that use the full panel width (as many as fit, at least two); every tile in a row gets the
 * height of the tallest one, so names and prices line up.
 */
@Composable
private fun <T> SkinGrid(items: List<T>, tile: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val columns = ((maxWidth + gap) / (MIN_TILE_WIDTH + gap)).toInt().coerceIn(2, 4)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in items.chunked(columns)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (item in row) tile(item, Modifier.weight(1f).fillMaxHeight())
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val MIN_TILE_WIDTH = 92.dp

@Composable
private fun SkinTile(
    modifier: Modifier,
    name: String,
    chosen: Boolean,
    unlocked: Boolean,
    requirement: String,
    /** The price of a premium skin (null for skins you unlock by playing). */
    premiumPrice: String?,
    /** Buys the premium skin; null while the store cannot sell it. */
    onBuy: (() -> Unit)?,
    onChoose: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (chosen) TableColors.FeltLight.copy(alpha = 0.7f) else TableColors.Ink.copy(alpha = 0.06f))
            .border(if (chosen) BorderStroke(2.dp, TableColors.TurnGlow) else BorderStroke(1.dp, TableColors.Ink.copy(alpha = 0.15f)), shape)
            .clickable(onClick = onChoose)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            preview()
            if (!unlocked) {
                Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.padding(6.dp).size(18.dp))
                }
            }
        }
        Text(
            name,
            color = TableColors.OnFelt,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Status lines sit at the bottom, level across the row.
        Spacer(Modifier.weight(1f))
        when {
            premiumPrice != null && !unlocked -> {
                Text(stringResource(R.string.skins_premium), color = TableColors.Highlight, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                Text(
                    premiumPrice,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFE082), Color(0xFFFF80AB), Color(0xFF80DEEA))))
                        .then(if (onBuy != null) Modifier.clickable(onClick = onBuy) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
                if (onBuy == null) {
                    Text(stringResource(R.string.skins_coming_soon), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
            chosen -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = TableColors.TurnGlow, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.skins_chosen), color = TableColors.TurnGlow, style = MaterialTheme.typography.labelMedium)
            }
            unlocked -> Text(stringResource(R.string.skins_choose), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.labelMedium)
            else -> Text(
                requirement,
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A small piece of table cloth with two cards on it. */
@Composable
private fun TablePreview(skin: TableSkin) {
    Box(
        Modifier
            .size(width = 80.dp, height = 82.dp)
            .clip(RoundedCornerShape(10.dp))
            .feltTable(skin),
        contentAlignment = Alignment.Center,
    ) {
        PlayingCard(Card.of("AH"), 30.dp, Modifier.offset(x = (-9).dp).rotate(-10f))
        PlayingCard(Card.of("KS"), 30.dp, Modifier.offset(x = 9.dp, y = 2.dp).rotate(8f))
    }
}

private fun backName(skin: CardBackSkin): Int = when (skin) {
    CardBackSkin.CLASSIC -> R.string.skin_back_classic
    CardBackSkin.CHERRY -> R.string.skin_back_cherry
    CardBackSkin.EMERALD -> R.string.skin_back_emerald
    CardBackSkin.MIDNIGHT_GOLD -> R.string.skin_back_midnight_gold
    CardBackSkin.ORANGE_LION -> R.string.skin_back_orange_lion
    CardBackSkin.DIAMOND_HOLO -> R.string.skin_back_diamond_holo
    CardBackSkin.RAINBOW_ROAD -> R.string.skin_back_rainbow_road
}

private fun tableName(skin: TableSkin): Int = when (skin) {
    TableSkin.GREEN -> R.string.skin_table_green
    TableSkin.BLUE -> R.string.skin_table_blue
    TableSkin.BORDEAUX -> R.string.skin_table_bordeaux
    TableSkin.SLATE -> R.string.skin_table_slate
    TableSkin.NIGHT -> R.string.skin_table_night
    TableSkin.OLED -> R.string.skin_table_oled
    TableSkin.RAINBOW_ROAD -> R.string.skin_table_rainbow_road
}

/** "99,00": the price with a decimal comma (as in the Netherlands), whatever the language. */
private fun euros(cents: Int): String = "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
