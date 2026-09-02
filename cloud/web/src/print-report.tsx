import React from "react";
import type { CloudEntity, PublishedLineupSnapshot, TeamSnapshot } from "../../shared/contracts";
import { FORMATIONS, type WebFormationType } from "../../shared/lineup-generator";

interface PrintLineupRow {
  round: number;
  playersByPosition: Record<string, string>;
}

interface PrintHalf {
  halfNumber: number;
  positions: string[];
  groupSummaries: Array<{ label: string; players: string }>;
  rows: PrintLineupRow[];
  teamGoals: number;
  opponentGoals: number;
}

interface PrintGoal {
  halfNumber: number;
  time: string;
  description: string;
  notes: string;
}

export interface PrintReportModel {
  gameId: string;
  teamName: string;
  opponent: string;
  location: string;
  scheduledLabel: string;
  status: string;
  formationLabel: string;
  rotationLabel: string;
  scoreRecorded: boolean;
  teamGoals: number;
  opponentGoals: number;
  halves: PrintHalf[];
  goals: PrintGoal[];
  notes: Array<{ label: string; value: string }>;
  publication: PublishedLineupSnapshot | null;
}

export function buildPrintReportModel(snapshot: TeamSnapshot, teamName: string, gameId: string): PrintReportModel | null {
  const game = activeEntities(snapshot, "game").find(entity => entity.entityId === gameId);
  if (!game) return null;
  const players = activeEntities(snapshot, "player");
  const playerNames = new Map(players.map(player => [player.entityId, stringValue(player.payload?.name)]));
  const assignments = activeEntities(snapshot, "assignment").filter(entity => stringValue(entity.payload?.gameId) === gameId);
  const goals = activeEntities(snapshot, "goal")
    .filter(entity => stringValue(entity.payload?.gameId) === gameId)
    .sort((left, right) => numberValue(left.payload?.halfNumber) - numberValue(right.payload?.halfNumber) ||
      numberValue(left.payload?.elapsedSecondsInHalf) - numberValue(right.payload?.elapsedSecondsInHalf));
  const template = parseObject(stringValue(game.payload?.templateJson));
  const formationType = isFormationType(template.formationType) ? template.formationType : "CLASSIC_U9";
  const formation = FORMATIONS[formationType];
  const plannedRounds = Math.max(
    positiveNumber(template.plannedRoundsPerHalf, positiveNumber(template.substitutionEventsPerHalf, 0) + 1),
    ...assignments.map(assignment => numberValue(assignment.payload?.roundIndex)),
    1,
  );
  const extraSlots = parseArray(stringValue(game.payload?.extraLineupSlotsJson));
  const halves = [1, 2].map(halfNumber => {
    const halfAssignments = assignments.filter(assignment => numberValue(assignment.payload?.halfNumber) === halfNumber);
    const configuredExtraPositions = extraSlots
      .filter(slot => numberValue(slot.halfNumber) === halfNumber)
      .map(slot => extraPosition(stringValue(slot.type)));
    const assignedExtraPositions = halfAssignments
      .map(assignment => stringValue(assignment.payload?.position))
      .filter(position => position.startsWith("EXTRA_"));
    const positions = unique([...formation.positions, ...configuredExtraPositions, ...assignedExtraPositions]);
    const groups = unique([...formation.groups.map(group => group.key as string), "GOALIE"]);
    const groupSummaries = groups.map(group => ({
      label: groupLabel(group),
      players: unique(halfAssignments
        .filter(assignment => stringValue(assignment.payload?.positionGroup) === group)
        .map(assignment => playerNames.get(stringValue(assignment.payload?.playerId)) ?? "")
        .filter(Boolean)).join(", ") || "Not assigned",
    }));
    const rows = Array.from({ length: plannedRounds }, (_, index) => index + 1).map(round => ({
      round,
      playersByPosition: Object.fromEntries(positions.map(position => {
        const assignment = halfAssignments.find(item => numberValue(item.payload?.roundIndex) === round && stringValue(item.payload?.position) === position);
        return [position, assignment ? playerNames.get(stringValue(assignment.payload?.playerId)) ?? "" : ""];
      })),
    }));
    const halfGoals = goals.filter(goal => numberValue(goal.payload?.halfNumber) === halfNumber);
    return {
      halfNumber,
      positions,
      groupSummaries,
      rows,
      teamGoals: halfGoals.filter(goal => stringValue(goal.payload?.scoredBy) === "TEAM").length,
      opponentGoals: halfGoals.filter(goal => stringValue(goal.payload?.scoredBy) === "OPPONENT").length,
    };
  });
  const publication = snapshot.publishedLineups
    .filter(item => item.gameId === gameId)
    .sort((left, right) => left.publishedVersion - right.publishedVersion)
    .at(-1) ?? null;
  const reportGoals = goals.map(goal => {
    const scorer = playerNames.get(stringValue(goal.payload?.scorerPlayerId));
    const assister = playerNames.get(stringValue(goal.payload?.assisterPlayerId));
    const teamGoal = stringValue(goal.payload?.scoredBy) === "TEAM";
    return {
      halfNumber: numberValue(goal.payload?.halfNumber),
      time: formatClock(numberValue(goal.payload?.elapsedSecondsInHalf)),
      description: teamGoal
        ? `${scorer || `${teamName} goal`}${assister ? ` (assist: ${assister})` : ""}`
        : `${stringValue(game.payload?.opponent) || "Opponent"} goal`,
      notes: stringValue(goal.payload?.notes),
    };
  });
  const notes = [
    { label: "Planner notes", value: stringValue(game.payload?.plannerNotes) },
    { label: "Live notes", value: stringValue(game.payload?.liveNotes) },
    { label: "Post-game notes", value: stringValue(game.payload?.postGameNotes) },
  ].filter(note => note.value.trim());
  const teamGoals = halves.reduce((total, half) => total + half.teamGoals, 0);
  const opponentGoals = halves.reduce((total, half) => total + half.opponentGoals, 0);
  const status = stringValue(game.payload?.status) || "PLANNED";

  return {
    gameId,
    teamName,
    opponent: stringValue(game.payload?.opponent) || "Opponent TBD",
    location: stringValue(game.payload?.location) || "Location TBD",
    scheduledLabel: formatDateTime(numberValue(game.payload?.scheduledAt)),
    status,
    formationLabel: formation.label,
    rotationLabel: `${plannedRounds} rotations per half · ${positiveNumber(template.substitutionWindowMinutes, 4)} minute target`,
    scoreRecorded: goals.length > 0 || status === "FINAL" || status === "LIVE",
    teamGoals,
    opponentGoals,
    halves,
    goals: reportGoals,
    notes,
    publication,
  };
}

export function PrintableGameReport({ model }: { model: PrintReportModel }) {
  return <article className="print-report-sheet" aria-label={`Printable lineup for ${model.teamName} versus ${model.opponent}`}>
    <header className="print-report-hero">
      <div>
        <span className="print-kicker">Soccer Game Manager · Match Plan</span>
        <h1>{model.teamName} <span>vs</span> {model.opponent}</h1>
        <p>{model.scheduledLabel} · {model.location}</p>
        <p>{model.formationLabel} · {model.rotationLabel}</p>
      </div>
      <div className="print-score-block">
        <span>{model.status === "FINAL" ? "Final score" : "Score"}</span>
        <strong>{model.scoreRecorded ? `${model.teamGoals} – ${model.opponentGoals}` : "___ – ___"}</strong>
      </div>
    </header>

    <section className="print-half-score-strip">
      {model.halves.map(half => <div key={half.halfNumber}><span>Half {half.halfNumber}</span><strong>{model.scoreRecorded ? `${half.teamGoals} – ${half.opponentGoals}` : "___ – ___"}</strong></div>)}
    </section>

    <div className="print-halves">
      {model.halves.map(half => <section className="print-half" key={half.halfNumber}>
        <div className="print-section-heading"><h2>Half {half.halfNumber}</h2><span>{half.rows.length} rotations</span></div>
        <div className="print-groups">{half.groupSummaries.map(group => <div key={group.label}><strong>{group.label}</strong><span>{group.players}</span></div>)}</div>
        <table className="print-lineup-table">
          <thead><tr><th>Rot</th>{half.positions.map(position => <th key={position}>{positionShort(position)}</th>)}</tr></thead>
          <tbody>{half.rows.map(row => <tr key={row.round}><th>R{row.round}</th>{half.positions.map(position => <td key={position}>{row.playersByPosition[position] || (position.startsWith("EXTRA_") ? "N/A" : "")}</td>)}</tr>)}</tbody>
        </table>
      </section>)}
    </div>

    {(model.goals.length > 0 || model.notes.length > 0) && <div className="print-details">
      {model.goals.length > 0 && <section><h2>Goal timeline</h2>{model.goals.map((goal, index) => <p key={`${goal.halfNumber}:${goal.time}:${index}`}><strong>H{goal.halfNumber} · {goal.time}</strong> · {goal.description}{goal.notes ? ` · ${goal.notes}` : ""}</p>)}</section>}
      {model.notes.length > 0 && <section><h2>Coach notes</h2>{model.notes.map(note => <p key={note.label}><strong>{note.label}:</strong> {note.value}</p>)}</section>}
    </div>}

    <footer className="print-report-footer">
      <span>{model.publication ? `${model.publication.lineupName || `Lineup v${model.publication.publishedVersion}`} · v${model.publication.publishedVersion} · ${model.publication.publishedByUser} · ${new Date(model.publication.publishedAt).toLocaleString()}` : "Draft lineup · Not yet published"}</span>
      <span>Printed {new Date().toLocaleString()}</span>
    </footer>
  </article>;
}

function activeEntities(snapshot: TeamSnapshot, type: string): CloudEntity[] {
  return snapshot.entities.filter(entity => entity.entityType === type && !entity.deletedAt);
}

function stringValue(value: unknown): string { return typeof value === "string" ? value : ""; }
function numberValue(value: unknown): number { return typeof value === "number" && Number.isFinite(value) ? value : 0; }
function positiveNumber(value: unknown, fallback: number): number { const parsed = Number(value); return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback; }
function unique<T>(values: T[]): T[] { return [...new Set(values)]; }
function parseObject(value: string): Record<string, unknown> { try { const parsed = JSON.parse(value); return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {}; } catch { return {}; } }
function parseArray(value: string): Array<Record<string, unknown>> { try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed.filter(item => item && typeof item === "object") : []; } catch { return []; } }
function isFormationType(value: unknown): value is WebFormationType { return value === "CLASSIC_U9" || value === "ATTACK_BACK_THREE"; }
function extraPosition(type: string): string { return ({ EXTRA_ATTACK: "EXTRA_ATTACK", EXTRA_MIDFIELD: "EXTRA_MIDFIELD", EXTRA_DEFENSE: "EXTRA_DEFENSE" } as Record<string, string>)[type] ?? type; }
function positionShort(position: string): string { return ({ LEFT_DEFENSE: "LD", CENTER_DEFENSE: "CD", RIGHT_DEFENSE: "RD", LEFT_MIDFIELDER: "LM", CENTER_MIDFIELDER: "CM", RIGHT_MIDFIELDER: "RM", STRIKER: "ST", GOALIE: "GK", EXTRA_ATTACK: "+A", EXTRA_MIDFIELD: "+M", EXTRA_DEFENSE: "+D" } as Record<string, string>)[position] ?? position; }
function groupLabel(group: string): string { return ({ ATTACK: "Attack", DEFENSE: "Defense", LR_MID: "L/R Mid", CM_STRIKER: "CM/Striker", GOALIE: "Goalie" } as Record<string, string>)[group] ?? group; }
function formatClock(seconds: number): string { return `${Math.floor(seconds / 60).toString().padStart(2, "0")}:${Math.max(0, seconds % 60).toString().padStart(2, "0")}`; }
function formatDateTime(timestamp: number): string { return timestamp > 0 ? new Date(timestamp).toLocaleString([], { dateStyle: "medium", timeStyle: "short" }) : "Date and time TBD"; }
