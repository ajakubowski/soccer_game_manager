package com.example.soccergamemanager.domain

import com.example.soccergamemanager.data.AssignmentEntity
import com.example.soccergamemanager.data.GameEntity
import com.example.soccergamemanager.data.GoalEventEntity
import com.example.soccergamemanager.data.PlayerEntity
import com.example.soccergamemanager.data.template
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportFormatter {
    fun format(
        game: GameEntity,
        players: List<PlayerEntity>,
        assignments: List<AssignmentEntity>,
        goals: List<GoalEventEntity>,
    ): PrintableReport {
        val playerNames = players.associateBy({ it.playerId }, { it.name })
        val template = game.template()
        val dateLabel = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(game.scheduledAt))
        val title = "${game.opponent.ifBlank { "Opponent TBD" }} - $dateLabel"

        val plainText = buildString {
            appendLine(title)
            appendLine("Status: ${game.status.name}")
            appendLine("Location: ${game.location.ifBlank { "Not set" }}")
            appendLine()

            for (half in 1..template.halfCount) {
                appendLine("Half $half")
                for (round in 1..template.roundsPerHalf) {
                    appendLine("  Round $round")
                    template.positions.forEach { position ->
                        val assignment = assignments.firstOrNull {
                            it.halfNumber == half && it.roundIndex == round && it.position == position
                        }
                        appendLine("    ${position.label}: ${playerNames[assignment?.playerId].orEmpty()}")
                    }
                }
                val halfGoals = goals.filter { it.halfNumber == half }
                appendLine(
                    "  Score: ${halfGoals.count { it.scoredBy == GoalSide.TEAM }}-" +
                        halfGoals.count { it.scoredBy == GoalSide.OPPONENT },
                )
                val scorers = halfGoals
                    .filter { it.scoredBy == GoalSide.TEAM }
                    .mapNotNull { goal -> goal.scorerPlayerId?.let(playerNames::get) }
                if (scorers.isNotEmpty()) {
                    appendLine("  Scorers: ${scorers.joinToString()}")
                }
                appendLine()
            }
        }.trim()

        val htmlRows = buildString {
            for (half in 1..template.halfCount) {
                append("<h2>Half $half</h2>")
                append("<table><thead><tr><th>Round</th>")
                template.positions.forEach { append("<th>${it.label}</th>") }
                append("</tr></thead><tbody>")
                for (round in 1..template.roundsPerHalf) {
                    append("<tr><td>$round</td>")
                    template.positions.forEach { position ->
                        val assignment = assignments.firstOrNull {
                            it.halfNumber == half && it.roundIndex == round && it.position == position
                        }
                        append("<td>${playerNames[assignment?.playerId].orEmpty()}</td>")
                    }
                    append("</tr>")
                }
                append("</tbody></table>")
                val halfGoals = goals.filter { it.halfNumber == half }
                append(
                    "<p><strong>Score:</strong> ${halfGoals.count { it.scoredBy == GoalSide.TEAM }}-" +
                        "${halfGoals.count { it.scoredBy == GoalSide.OPPONENT }}</p>",
                )
                val scorers = halfGoals
                    .filter { it.scoredBy == GoalSide.TEAM }
                    .mapNotNull { goal -> goal.scorerPlayerId?.let(playerNames::get) }
                if (scorers.isNotEmpty()) {
                    append("<p><strong>Scorers:</strong> ${scorers.joinToString()}</p>")
                }
            }
        }

        val html = """
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 24px; color: #122117; }
                    h1, h2 { color: #17633C; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
                    th, td { border: 1px solid #CAD8C7; padding: 8px; font-size: 12px; text-align: left; }
                    th { background: #EEF6EF; }
                </style>
            </head>
            <body>
                <h1>$title</h1>
                <p><strong>Location:</strong> ${game.location.ifBlank { "Not set" }}</p>
                <p><strong>Status:</strong> ${game.status.name}</p>
                $htmlRows
            </body>
            </html>
        """.trimIndent()

        return PrintableReport(title = title, plainText = plainText, html = html)
    }
}
