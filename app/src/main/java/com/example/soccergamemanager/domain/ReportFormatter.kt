package com.example.soccergamemanager.domain

import com.example.soccergamemanager.data.AssignmentEntity
import com.example.soccergamemanager.data.GameEntity
import com.example.soccergamemanager.data.GoalEventEntity
import com.example.soccergamemanager.data.PlayerEntity
import com.example.soccergamemanager.data.template

class ReportFormatter {
    fun format(
        game: GameEntity,
        players: List<PlayerEntity>,
        assignments: List<AssignmentEntity>,
        goals: List<GoalEventEntity>,
        analytics: MatchReportAnalytics,
    ): PrintableReport {
        val playerNames = players.associateBy({ it.playerId }, { it.name })
        val template = game.template()
        val title = "${game.opponent.ifBlank { "Opponent TBD" }} - ${analytics.dateLabel}"

        val plainText = buildString {
            appendLine(title)
            appendLine("Status: ${analytics.status.name}")
            appendLine("Location: ${analytics.location}")
            appendLine("Final score: ${analytics.teamGoals}-${analytics.opponentGoals}")
            appendLine("Record by half: ${analytics.halfScores.joinToString { "H${it.halfNumber} ${it.teamGoals}-${it.opponentGoals}" }}")
            appendLine()
            appendLine("Goal timeline")
            analytics.timelineEvents
                .filter { it.kind == MatchTimelineKind.TEAM_GOAL || it.kind == MatchTimelineKind.OPPONENT_GOAL }
                .forEach { event ->
                    appendLine("  Half ${event.halfNumber} • ${formatClock(event.elapsedSecondsInHalf)} • ${event.label}")
                }
            appendLine()
            appendLine("Player usage")
            analytics.playerUsage.forEach { usage ->
                appendLine(
                    "  ${usage.playerName}: ${"%.1f".format(usage.minutes)} min, ${usage.goals}G, ${usage.assists}A, " +
                        "${usage.positions.joinToString { it.label }}",
                )
            }
            appendLine()
            appendLine("Lineup rotation")
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
            }
        }.trim()

        val halfScoreHtml = analytics.halfScores.joinToString("") { half ->
            "<div class='half-score'><span>Half ${half.halfNumber}</span><strong>${half.teamGoals}-${half.opponentGoals}</strong></div>"
        }
        val timelineHtml = analytics.timelineEvents
            .filter { it.kind == MatchTimelineKind.TEAM_GOAL || it.kind == MatchTimelineKind.OPPONENT_GOAL }
            .joinToString("") { event ->
                "<div class='timeline-event'><span>H${event.halfNumber}</span><span>${formatClock(event.elapsedSecondsInHalf)}</span><span>${event.label}</span></div>"
            }
        val playerUsageHtml = analytics.playerUsage.joinToString("") { usage ->
            "<tr><td>${usage.playerName}</td><td>${"%.1f".format(usage.minutes)}</td><td>${usage.goals}</td><td>${usage.assists}</td><td>${usage.positions.joinToString { it.label }}</td></tr>"
        }
        val rotationHtml = buildString {
            for (half in 1..template.halfCount) {
                append("<h3>Half $half</h3>")
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
            }
        }

        val html = """
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 24px; color: #13243b; }
                    h1, h2, h3 { color: #1c5aa8; margin-bottom: 8px; }
                    .hero { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
                    .score { font-size: 30px; font-weight: bold; color: #0c1f38; }
                    .half-scores { display: flex; gap: 12px; margin: 12px 0 18px; }
                    .half-score { background: #e7f0ff; padding: 10px 14px; border-radius: 12px; min-width: 96px; }
                    .half-score span { display:block; font-size:12px; color:#32598c; }
                    .timeline-event { display:flex; gap:12px; padding:8px 0; border-bottom:1px solid #dde5f2; font-size:13px; }
                    .takeaway { background:#f7faff; border-left:4px solid #1c5aa8; padding:10px 12px; margin:8px 0; border-radius:10px; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
                    th, td { border: 1px solid #d7e1ef; padding: 8px; font-size: 12px; text-align: left; }
                    th { background: #edf4ff; }
                </style>
            </head>
            <body>
                <div class="hero">
                    <div>
                        <h1>$title</h1>
                        <p><strong>Location:</strong> ${analytics.location}</p>
                        <p><strong>Status:</strong> ${analytics.status.name}</p>
                    </div>
                    <div class="score">${analytics.teamGoals} - ${analytics.opponentGoals}</div>
                </div>
                <div class="half-scores">$halfScoreHtml</div>
                <h2>Goal timeline</h2>
                $timelineHtml
                <h2>Coach takeaways</h2>
                ${analytics.takeaways.joinToString("") { "<div class='takeaway'><strong>${it.title}</strong><br/>${it.body}</div>" }}
                <h2>Player usage</h2>
                <table>
                    <thead>
                        <tr><th>Player</th><th>Minutes</th><th>Goals</th><th>Assists</th><th>Positions</th></tr>
                    </thead>
                    <tbody>
                        $playerUsageHtml
                    </tbody>
                </table>
                <h2>Lineup rotation</h2>
                $rotationHtml
            </body>
            </html>
        """.trimIndent()

        return PrintableReport(
            title = title,
            plainText = plainText,
            html = html,
            analytics = analytics,
        )
    }

    private fun formatClock(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
