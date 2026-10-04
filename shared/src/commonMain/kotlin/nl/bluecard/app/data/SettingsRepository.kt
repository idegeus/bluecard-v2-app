package nl.bluecard.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import nl.bluecard.app.platform.Platform
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.settings.AppSettings
import nl.bluecard.app.settings.BotSpeed
import nl.bluecard.engine.core.BotDifficulty
import kotlinx.serialization.KSerializer
import nl.bluecard.engine.hartenjagen.HjHouseRules
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.engine.presidenten.PrHouseRules
import nl.bluecard.engine.presidenten.PrRules
import nl.bluecard.engine.pesten.PsRules
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.engine.zweedspesten.ZpRules
import nl.bluecard.multiplayer.protocol.ProtocolJson
import kotlin.uuid.Uuid

/** Reads and writes [AppSettings] with Jetpack DataStore (local file, works offline). */
class SettingsRepository(private val store: DataStore<Preferences>, private val platform: Platform) {
    private val json = ProtocolJson.json

    val settings: Flow<AppSettings> = store.data
        .catch { e ->
            if (e is CancellationException) throw e
            platform.log(TAG, "settings unreadable, using defaults", e)
            emit(emptyPreferences())
        }
        .map { prefs -> prefs.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    /** Makes sure a player token and a device id exist; returns the (possibly new) settings. */
    suspend fun ensureToken(): AppSettings {
        val now = current()
        if (now.playerToken.isNotBlank() && now.deviceId.isNotBlank()) return now
        val token = now.playerToken.ifBlank { Uuid.random().toString() }
        val deviceId = now.deviceId.ifBlank { Uuid.random().toString() }
        store.edit {
            it[KEY_TOKEN] = token
            it[KEY_DEVICE_ID] = deviceId
        }
        return now.copy(playerToken = token, deviceId = deviceId)
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[KEY_NAME] = updated.playerName.trim().take(AppSettings.MAX_NAME_LENGTH)
            prefs[KEY_TOKEN] = updated.playerToken
            prefs[KEY_BOT_SPEED] = updated.botSpeed.name
            prefs[KEY_DIFFICULTY] = updated.defaultDifficulty.name
            prefs[KEY_BOT_COUNT] = updated.botCount
            prefs[KEY_HINTS] = updated.showHints
            prefs[KEY_SCREEN_ON] = updated.keepScreenOn
            prefs[KEY_SOUND] = updated.soundEffects
            prefs[KEY_SHUFFLE] = updated.shuffleRitual
            prefs[KEY_NEARBY_ALERTS] = updated.nearbyAlerts
            prefs[KEY_RULES] = json.encodeToString(ZpHouseRules.serializer(), updated.houseRules)
            prefs[KEY_PESTEN_RULES] = json.encodeToString(PsHouseRules.serializer(), updated.pestenRules)
            prefs[KEY_PRESIDENT_RULES] = json.encodeToString(PrHouseRules.serializer(), updated.presidentRules)
            prefs[KEY_HEARTS_RULES] = json.encodeToString(HjHouseRules.serializer(), updated.heartsRules)
            prefs[KEY_GAME] = updated.game.id
            prefs[KEY_DEVICE_ID] = updated.deviceId
            prefs[KEY_CARD_BACK] = updated.cardBackSkin
            prefs[KEY_TABLE] = updated.tableSkin
            prefs[KEY_AVATAR] = updated.avatar
            prefs[KEY_REACTIONS] = updated.reactionEmojis.joinToString(REACTION_SEPARATOR)
        }
    }

    suspend fun resetPreferences() {
        update { old ->
            AppSettings(
                playerName = old.playerName,
                playerToken = old.playerToken,
                game = old.game,
                deviceId = old.deviceId,
                cardBackSkin = old.cardBackSkin,
                tableSkin = old.tableSkin,
                avatar = old.avatar,
            )
        }
    }

    private fun <T> Preferences.decodeOrNull(key: Preferences.Key<String>, serializer: KSerializer<T>): T? {
        val text = this[key] ?: return null
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException as well.
            platform.log(TAG, "stored $key invalid, using defaults", e)
            null
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val rules = this[KEY_RULES]?.let { text ->
            try {
                json.decodeFromString(ZpHouseRules.serializer(), text)
            } catch (e: SerializationException) {
                platform.log(TAG, "stored house rules invalid, using defaults", e)
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }?.takeIf { ZpRules.validateSetup(2, it) == null } ?: ZpHouseRules()
        val pestenRules = this[KEY_PESTEN_RULES]?.let { text ->
            try {
                json.decodeFromString(PsHouseRules.serializer(), text)
            } catch (e: SerializationException) {
                platform.log(TAG, "stored pesten rules invalid, using defaults", e)
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }?.takeIf { PsRules.validateSetup(2, it) == null } ?: PsHouseRules()
        return AppSettings(
            playerName = this[KEY_NAME].orEmpty(),
            playerToken = this[KEY_TOKEN].orEmpty(),
            botSpeed = this[KEY_BOT_SPEED]?.let { runCatching { BotSpeed.valueOf(it) }.getOrNull() } ?: BotSpeed.NORMAL,
            defaultDifficulty = this[KEY_DIFFICULTY]?.let { runCatching { BotDifficulty.valueOf(it) }.getOrNull() }
                ?: BotDifficulty.NORMAL,
            botCount = (this[KEY_BOT_COUNT] ?: 2).coerceIn(1, maxOf(ZpRules.maxPlayersFor(rules), PsRules.maxPlayersFor(pestenRules), PrRules.MAX_PLAYERS) - 1),
            showHints = this[KEY_HINTS] ?: true,
            keepScreenOn = this[KEY_SCREEN_ON] ?: true,
            soundEffects = this[KEY_SOUND] ?: true,
            shuffleRitual = this[KEY_SHUFFLE] ?: true,
            nearbyAlerts = this[KEY_NEARBY_ALERTS] ?: true,
            houseRules = rules,
            pestenRules = pestenRules,
            presidentRules = decodeOrNull(KEY_PRESIDENT_RULES, PrHouseRules.serializer())?.takeIf { PrRules.validateSetup(4, it) == null } ?: PrHouseRules(),
            heartsRules = decodeOrNull(KEY_HEARTS_RULES, HjHouseRules.serializer()) ?: HjHouseRules(),
            game = GameKind.fromId(this[KEY_GAME]) ?: GameKind.ZWEEDS_PESTEN,
            deviceId = this[KEY_DEVICE_ID].orEmpty(),
            cardBackSkin = this[KEY_CARD_BACK].orEmpty(),
            tableSkin = this[KEY_TABLE].orEmpty(),
            avatar = this[KEY_AVATAR].orEmpty(),
            reactionEmojis = this[KEY_REACTIONS]?.split(REACTION_SEPARATOR)?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: AppSettings.DEFAULT_REACTIONS,
        )
    }

    private companion object {
        const val TAG = "SettingsRepository"
        val KEY_NAME = stringPreferencesKey("player_name")
        val KEY_TOKEN = stringPreferencesKey("player_token")
        val KEY_BOT_SPEED = stringPreferencesKey("bot_speed")
        val KEY_DIFFICULTY = stringPreferencesKey("default_difficulty")
        val KEY_BOT_COUNT = intPreferencesKey("bot_count")
        val KEY_HINTS = booleanPreferencesKey("show_hints")
        val KEY_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_SOUND = booleanPreferencesKey("sound_effects")
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle_ritual")
        val KEY_NEARBY_ALERTS = booleanPreferencesKey("nearby_alerts")
        val KEY_RULES = stringPreferencesKey("house_rules_json")
        val KEY_PESTEN_RULES = stringPreferencesKey("pesten_rules_json")
        val KEY_PRESIDENT_RULES = stringPreferencesKey("president_rules_json")
        val KEY_HEARTS_RULES = stringPreferencesKey("hearts_rules_json")
        val KEY_GAME = stringPreferencesKey("game")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_CARD_BACK = stringPreferencesKey("skin_card_back")
        val KEY_TABLE = stringPreferencesKey("skin_table")
        val KEY_AVATAR = stringPreferencesKey("avatar")
        val KEY_REACTIONS = stringPreferencesKey("reaction_emojis")
        const val REACTION_SEPARATOR = "|"
    }
}
