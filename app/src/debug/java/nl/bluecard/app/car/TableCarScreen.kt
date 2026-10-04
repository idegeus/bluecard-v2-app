package nl.bluecard.app.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import nl.bluecard.app.AppContainer
import nl.bluecard.app.R
import nl.bluecard.app.res.Resources
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.app.session.ViewSummary
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.multiplayer.session.LobbySnapshot
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase

/**
 * The table on the car screen, for the host: who sits at it, whose turn it is and how many cards everyone has
 * left; in the lobby "add bot" and "start", after a game "play again". Built from Android Auto templates, so it
 * stays readable (and allowed) while driving: at most six rows, no cards.
 */
class TableCarScreen(carContext: CarContext, private val container: AppContainer) : Screen(carContext) {

    private var table: CarTable? = null

    init {
        lifecycleScope.launch {
            tables().collect { latest ->
                if (latest == table) return@collect
                table = latest
                invalidate()
                // Android Auto limits how often a template may change; a calm pace is plenty for a card table.
                delay(REFRESH_MS)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun tables(): Flow<CarTable?> = container.sessions.active.flatMapLatest { active ->
        if (active == null || active is ActiveSession.Local) {
            flowOf(null)
        } else {
            combine(active.port.lobby, active.port.view) { lobby, view ->
                CarTable(active, lobby, view?.let { active.game.binding.summarizeAny(it) })
            }
        }
    }

    override fun onGetTemplate(): Template {
        val current = table ?: return noTable()
        val lobby = current.lobby ?: return noTable()
        val gameName = GameTexts.gameName(Resources, current.session.game)
        val hosting = current.session is ActiveSession.Hosting
        val summary = current.summary
        val rows = mutableListOf<Row>()
        val title: String
        when {
            lobby.phase == SessionPhase.LOBBY || summary == null -> {
                title = Resources.getString(R.string.car_lobby, gameName)
                if (hosting) {
                    if (lobby.seats.size < lobby.maxPlayers) rows += actionRow(Resources.getString(R.string.host_add_bot)) { addBot() }
                    if (lobby.seats.size >= lobby.minPlayers) rows += actionRow(Resources.getString(R.string.host_start)) { start() }
                }
                rows += lobby.seats.take(MAX_ROWS - rows.size).map { seat ->
                    Row.Builder().setTitle(seat.name).addText(seatKind(seat.kind)).build()
                }
            }
            summary.result != null -> {
                title = Resources.getString(R.string.car_finished, gameName)
                if (hosting) rows += actionRow(Resources.getString(R.string.result_play_again)) { playAgain() }
                rows += summary.result!!.ranking.take(MAX_ROWS - rows.size).map { entry ->
                    Row.Builder().setTitle(Resources.getString(R.string.car_place, entry.position, entry.name)).build()
                }
            }
            else -> {
                title = Resources.getString(R.string.car_playing, gameName)
                rows += lobby.seats.take(MAX_ROWS).map { seat -> playerRow(seat.id, seat.name, summary) }
            }
        }
        if (!hosting) {
            rows.add(0, Row.Builder().setTitle(Resources.getString(R.string.car_guest, current.session.hostLabel())).build())
        }
        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(ItemList.Builder().apply { rows.take(MAX_ROWS).forEach(::addItem) }.build())
            .build()
    }

    private fun playerRow(id: String, name: String, summary: ViewSummary): Row {
        val cards = summary.cardsLeft[id] ?: 0
        val details = buildList {
            if (summary.currentPlayerId == id) add(Resources.getString(R.string.car_turn))
            add(Resources.getQuantityString(R.plurals.car_cards, cards, cards))
        }.joinToString(" · ")
        val turn = summary.currentPlayerId == id
        return Row.Builder().setTitle(if (turn) "▶ $name" else name).addText(details).build()
    }

    private fun seatKind(kind: SeatKind): String = when (kind) {
        SeatKind.HOST -> Resources.getString(R.string.car_host)
        SeatKind.BOT -> Resources.getString(R.string.car_bot)
        SeatKind.REMOTE -> Resources.getString(R.string.car_player)
    }

    private fun actionRow(text: String, onClick: () -> Unit): Row =
        Row.Builder().setTitle("➤ $text").setBrowsable(false).setOnClickListener(onClick).build()

    private fun noTable(): Template = MessageTemplate.Builder(Resources.getString(R.string.car_no_table_text))
        .setTitle(Resources.getString(R.string.car_no_table_title))
        .setHeaderAction(Action.APP_ICON)
        .addAction(
            Action.Builder()
                .setTitle(Resources.getString(R.string.car_open_table))
                .setOnClickListener { openTable() }
                .build(),
        )
        .build()

    private fun openTable() {
        container.appScope.launch { container.sessions.startHosting(container.settings.value.game) }
    }

    private fun addBot() {
        val hosting = container.sessions.active.value as? ActiveSession.Hosting ?: return
        container.appScope.launch { hosting.host.addBot(container.settings.value.defaultDifficulty) }
    }

    private fun start() {
        val hosting = container.sessions.active.value as? ActiveSession.Hosting ?: return
        container.appScope.launch { hosting.host.startGame() }
    }

    private fun playAgain() {
        container.appScope.launch { container.sessions.playAgain() }
    }

    private data class CarTable(val session: ActiveSession, val lobby: LobbySnapshot?, val summary: ViewSummary?)

    private companion object {
        /** Android Auto shows at most six list rows while driving. */
        const val MAX_ROWS = 6
        const val REFRESH_MS = 1_000L
    }
}

private fun ActiveSession.hostLabel(): String = (this as? ActiveSession.Joined)?.hostLabel ?: ""
