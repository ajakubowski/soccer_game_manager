import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import type { AuthSessionResponse, AuthUser, CloudEntity, MemberRole, MutationCommand, MutationResult, PresenceMember, SyncConflict, TeamSnapshot, TeamSummary } from "../../shared/contracts";
import { FORMATIONS, generateWebLineup, type WebFormationType, type WebLineupPlayer, type WebManualLock, type WebPlayerHistory } from "../../shared/lineup-generator";
import { api, cloudApi, collaborationSocket } from "./api";
import { buildManualInviteMessage } from "./invite-message";
import { buildPrintReportModel, PrintableGameReport } from "./print-report";
import "./styles.css";

type Tab = "Overview" | "Roster & Schedule" | "Planner" | "Live" | "History & Stats" | "Report" | "Access";
type Json = Record<string, unknown>;

function App() {
  const [auth, setAuth] = useState<AuthSessionResponse | null>(null);
  const [authError, setAuthError] = useState("");

  useEffect(() => {
    void cloudApi.authSession()
      .then(setAuth)
      .catch(error => setAuthError(readableError(error)));
  }, []);

  if (!auth) return <LoadingScreen message={authError || "Checking your session..."} />;
  if (!auth.user) {
    return <AuthScreen
      registrationOpen={auth.registrationOpen}
      onAuthenticated={user => setAuth({ user, registrationOpen: false })}
    />;
  }
  return <TeamApp user={auth.user} onLogout={() => setAuth({ user: null, registrationOpen: false })} />;
}

function TeamApp({ user, onLogout }: { user: AuthUser; onLogout: () => void }) {
  const [teams, setTeams] = useState<TeamSummary[]>([]);
  const [teamId, setTeamId] = useState<string | null>(localStorage.getItem("soccer-team-id"));
  const [snapshot, setSnapshot] = useState<TeamSnapshot | null>(null);
  const [presence, setPresence] = useState<PresenceMember[]>([]);
  const [tab, setTab] = useState<Tab>("Overview");
  const [message, setMessage] = useState("");
  const [conflict, setConflict] = useState<{ conflict: SyncConflict; command: MutationCommand } | null>(null);
  const [creatingTeam, setCreatingTeam] = useState(false);
  const [reportGameId, setReportGameId] = useState("");

  const refreshTeams = async () => {
    const result = await cloudApi.teams();
    setTeams(result.teams);
    setTeamId(current => current && result.teams.some(team => team.teamId === current)
      ? current
      : result.teams[0]?.teamId ?? null);
  };
  const refresh = async () => {
    if (teamId) setSnapshot(await cloudApi.snapshot(teamId));
  };

  useEffect(() => { void refreshTeams().catch(error => setMessage(String(error))); }, []);
  useEffect(() => {
    if (!teamId) return;
    localStorage.setItem("soccer-team-id", teamId);
    void refresh().catch(error => setMessage(String(error)));
    const socket = collaborationSocket(teamId, event => {
      if (event.presence) setPresence(event.presence);
      if (event.type !== "presence") void refresh();
    });
    return () => socket.close();
  }, [teamId]);

  const mutate = async (commands: MutationCommand[]): Promise<MutationResult | undefined> => {
    if (!teamId) return undefined;
    const result = await cloudApi.mutate(teamId, commands);
    if (result.conflicts.length) {
      const first = result.conflicts[0];
      const command = commands.find(item => item.mutationId === first.mutationId);
      if (command) setConflict({ conflict: first, command });
    }
    await refresh();
    return result;
  };

  const activeTeam = teams.find(team => team.teamId === teamId);
  if (!teams.length) return <CreateTeam user={user} onLogout={onLogout} onCreated={async id => { await refreshTeams(); setTeamId(id); }} message={message} />;

  return (
    <div className="app-shell">
      <header className="brand-header">
        <div>
          <span className="eyebrow">Soccer Game Manager Cloud</span>
          <h1>{activeTeam?.name ?? "Team"} <small>{activeTeam?.year}</small></h1>
        </div>
        <div className="header-actions">
          <span className="identity-pill">{user.username}</span>
          <span className="sync-pill">Cloud revision {snapshot?.teamRevision ?? "..."}</span>
          <select value={teamId ?? ""} onChange={event => setTeamId(event.target.value)}>
            {teams.map(team => <option key={team.teamId} value={team.teamId}>{team.name} {team.year}</option>)}
          </select>
          <button className="header-button" onClick={() => setCreatingTeam(true)}>New team</button>
          <button className="header-button" onClick={async () => { await cloudApi.logout(); onLogout(); }}>Sign out</button>
        </div>
      </header>

      <section className="collaboration-bar">
        <strong>{presence.length ? `${presence.length} connected` : "Connecting..."}</strong>
        {presence.map(member => <span className="presence" key={member.connectionId}>{member.displayName} · {member.location}</span>)}
        {message && <button className="message" onClick={() => setMessage("")}>{message}</button>}
      </section>

      <nav className="tabs" aria-label="Team sections">
        {(["Overview", "Roster & Schedule", "Planner", "Live", "History & Stats", "Report", "Access"] as Tab[]).map(item => (
          <button key={item} className={tab === item ? "active" : ""} onClick={() => setTab(item)}>{item}</button>
        ))}
      </nav>

      <main>
        {snapshot && tab === "Overview" && <Overview snapshot={snapshot} onNavigate={setTab} />}
        {snapshot && tab === "Roster & Schedule" && <TeamRoster teamId={teamId!} snapshot={snapshot} onMutate={mutate} onRefresh={refresh} onMessage={setMessage} />}
        {snapshot && tab === "Planner" && <Planner teamId={teamId!} snapshot={snapshot} onMutate={mutate} onRefresh={refresh} onMessage={setMessage} onOpenPrint={gameId => { setReportGameId(gameId); setTab("Report"); }} />}
        {snapshot && tab === "Live" && <LiveObserver snapshot={snapshot} />}
        {snapshot && tab === "History & Stats" && <History snapshot={snapshot} />}
        {snapshot && tab === "Report" && <Report snapshot={snapshot} teamName={activeTeam?.name ?? "Team"} initialGameId={reportGameId} />}
        {teamId && tab === "Access" && <AccessPanel teamId={teamId} teamName={activeTeam?.name ?? "Team"} user={user} onMessage={setMessage} />}
      </main>
      {conflict && <ConflictDialog
        conflict={conflict.conflict}
        subject={conflictSubject(conflict.conflict, snapshot)}
        onUseCloud={async () => {
          const subject = conflictSubject(conflict.conflict, snapshot);
          setConflict(null);
          await refresh();
          setMessage(`Cloud version kept for ${subject}.`);
        }}
        onKeepMine={async () => {
          const activeConflict = conflict;
          const retry = { ...conflict.command, mutationId: crypto.randomUUID(), expectedVersion: conflict.conflict.actualVersion };
          const result = await mutate([retry]);
          if (!result?.conflicts.length) {
            setConflict(current => current?.conflict.mutationId === activeConflict.conflict.mutationId ? null : current);
            setMessage(`Web change saved for ${conflictSubject(activeConflict.conflict, snapshot)}.`);
          }
        }}
      />}
      {creatingTeam && <CreateTeamDialog
        onCancel={() => setCreatingTeam(false)}
        onCreated={async id => {
          await refreshTeams();
          setTeamId(id);
          setSnapshot(await cloudApi.snapshot(id));
          setTab("Roster & Schedule");
          setCreatingTeam(false);
        }}
      />}
    </div>
  );
}

function TeamRoster({ teamId, snapshot, onMutate, onRefresh, onMessage }: {
  teamId: string;
  snapshot: TeamSnapshot;
  onMutate: (commands: MutationCommand[]) => Promise<MutationResult | undefined>;
  onRefresh: () => Promise<void>;
  onMessage: (message: string) => void;
}) {
  const season = entities(snapshot, "season")[0];
  const players = entities(snapshot, "player").sort((a, b) => stringValue(a.payload?.name).localeCompare(stringValue(b.payload?.name)));
  const games = entities(snapshot, "game").sort((a, b) => numberValue(b.payload?.scheduledAt) - numberValue(a.payload?.scheduledAt));
  const [playerName, setPlayerName] = useState("");
  const [jersey, setJersey] = useState("");
  const [playerNotes, setPlayerNotes] = useState("");
  const [keeperEligible, setKeeperEligible] = useState(true);
  const [editingPlayer, setEditingPlayer] = useState<CloudEntity | null>(null);
  const [editingGame, setEditingGame] = useState<CloudEntity | null>(null);
  const [opponent, setOpponent] = useState("");
  const [locationName, setLocationName] = useState("");
  const [scheduledAt, setScheduledAt] = useState(new Date().toISOString().slice(0, 16));

  const mutation = (entityType: string, entityId: string, expectedVersion: number, payload: Json | null, operation: MutationCommand["operation"] = "UPSERT_ENTITY"): MutationCommand => ({
    mutationId: crypto.randomUUID(), deviceId: "web", teamId, entityType, entityId,
    operation, expectedVersion, payload, createdAt: Date.now(),
  });
  const addPlayer = async () => {
    if (!season || !playerName.trim()) return;
    const playerId = crypto.randomUUID();
    await onMutate([mutation("player", playerId, 0, {
      playerId,
      seasonId: season.entityId,
      name: playerName.trim(),
      jerseyNumber: jersey.trim(),
      notes: playerNotes.trim(),
      preferredKeeper: keeperEligible,
      active: true,
    })]);
    setPlayerName(""); setJersey(""); setPlayerNotes(""); setKeeperEligible(true);
  };
  const deletePlayer = async (player: CloudEntity) => {
    const name = stringValue(player.payload?.name);
    if (!window.confirm(`Delete ${name}? This removes the player from the shared roster on every synced device.`)) return;
    await onMutate([mutation("player", player.entityId, player.version, null, "DELETE_ENTITY")]);
  };
  const addGame = async () => {
    if (!season || !opponent.trim()) return;
    const gameId = crypto.randomUUID();
    await onMutate([mutation("game", gameId, 0, {
      gameId, seasonId: season.entityId, opponent: opponent.trim(), location: locationName.trim(), scheduledAt: new Date(scheduledAt).getTime(),
      status: "PLANNED", templateJson: season.payload?.defaultTemplateJson ?? "{}", manualGroupLocksJson: "[]", extraLineupSlotsJson: "[]",
      plannerNotes: "", liveNotes: "", postGameNotes: "", currentHalf: 1, currentRound: 1, elapsedSecondsInHalf: 0,
      elapsedSecondsInRound: 0, lockedAt: null, finalizedAt: null,
    })]);
    setOpponent(""); setLocationName("");
  };
  const deleteGame = async (game: CloudEntity) => {
    const opponentName = stringValue(game.payload?.opponent) || "this game";
    const relatedCount = snapshot.entities.filter(entity => !entity.deletedAt &&
      ["availability", "assignment", "goal"].includes(entity.entityType) && stringValue(entity.payload?.gameId) === game.entityId).length;
    const publishedCount = snapshot.publishedLineups.filter(lineup => lineup.gameId === game.entityId).length;
    const details = [relatedCount ? `${relatedCount} related lineup/event records` : "its associated records", publishedCount ? `${publishedCount} published lineup version${publishedCount === 1 ? "" : "s"}` : ""].filter(Boolean).join(" and ");
    if (!window.confirm(`Delete the game against ${opponentName}? This permanently removes the game, ${details}, and updates shared analytics on every synced device. This cannot be undone.`)) return;
    try {
      const result = await cloudApi.deleteGame(teamId, game.entityId, game.version, crypto.randomUUID());
      if (result.conflicts.length) {
        await onRefresh();
        onMessage("The game changed on another device before it could be deleted. Review the refreshed schedule and try again.");
        return;
      }
      await onRefresh();
      onMessage(`Game against ${opponentName} deleted.`);
    } catch (error) {
      onMessage(`Game was not deleted: ${readableError(error)}`);
    }
  };

  if (!season) return <Empty text="Preparing this cloud team for web roster management. Refresh once if this message remains visible." />;
  return <div className="two-column roster-layout">
    <section className="panel"><span className="eyebrow">Shared roster</span><h2>Players</h2>
      <p className="muted">Build and maintain the shared roster here. Pair the Android tablet only when you are ready to download it for match day.</p>
      <div className="roster-entry">
        <input placeholder="Player name" aria-label="Player name" value={playerName} onChange={event => setPlayerName(event.target.value)} />
        <input className="short-input" placeholder="#" aria-label="Jersey number" value={jersey} onChange={event => setJersey(event.target.value)} />
        <input placeholder="Notes (optional)" aria-label="Player notes" value={playerNotes} onChange={event => setPlayerNotes(event.target.value)} />
        <label className="check-control"><input type="checkbox" checked={keeperEligible} onChange={event => setKeeperEligible(event.target.checked)} /> Keeper eligible</label>
        <button className="primary" disabled={!playerName.trim()} onClick={() => void addPlayer()}>Add player</button>
      </div>
      <div className="roster-list">
        {players.map(player => <article className={`roster-player ${player.payload?.active === false ? "inactive" : ""}`} key={player.entityId}>
          <div className="jersey-badge">{stringValue(player.payload?.jerseyNumber) || "–"}</div>
          <div className="roster-player-copy"><strong>{stringValue(player.payload?.name)}</strong><span className="subline">{player.payload?.preferredKeeper === false ? "Not keeper eligible" : "Keeper eligible"}{stringValue(player.payload?.notes) ? ` · ${stringValue(player.payload?.notes)}` : ""}</span></div>
          <span className={`status-chip ${player.payload?.active === false ? "off" : "on"}`}>{player.payload?.active === false ? "Inactive" : "Active"}</span>
          <div className="row-actions">
            <button onClick={() => setEditingPlayer(player)}>Edit</button>
            <button onClick={() => void onMutate([mutation("player", player.entityId, player.version, { ...(player.payload ?? {}), active: player.payload?.active === false })])}>{player.payload?.active === false ? "Activate" : "Deactivate"}</button>
            <button className="danger-text" onClick={() => void deletePlayer(player)}>Delete</button>
          </div>
        </article>)}
        {!players.length && <Empty text="Add your first player above. New players are keeper eligible by default." />}
      </div>
    </section>
    <section className="panel"><span className="eyebrow">Shared schedule</span><h2>Games</h2>
      <div className="stacked-form"><input placeholder="Opponent" value={opponent} onChange={event => setOpponent(event.target.value)} /><input placeholder="Location" value={locationName} onChange={event => setLocationName(event.target.value)} /><input type="datetime-local" value={scheduledAt} onChange={event => setScheduledAt(event.target.value)} /><button className="primary" onClick={() => void addGame()}>Create game</button></div>
      <div className="schedule-list">{games.map(game => <article className="schedule-game" key={game.entityId}>
        <div className="schedule-game-copy"><strong>vs {stringValue(game.payload?.opponent)}</strong><span>{new Date(numberValue(game.payload?.scheduledAt)).toLocaleString()}</span><span>{stringValue(game.payload?.location) || "Location TBD"}</span></div>
        <span className={`game-status ${stringValue(game.payload?.status).toLowerCase()}`}>{label(stringValue(game.payload?.status) || "PLANNED")}</span>
        <div className="row-actions"><button onClick={() => setEditingGame(game)}>Edit</button><button className="danger-text" onClick={() => void deleteGame(game)}>Delete</button></div>
      </article>)}</div>
      {!games.length && <Empty text="Create the first game above. You can edit or delete it later." />}
    </section>
    {editingPlayer && <PlayerEditor
      player={editingPlayer}
      onCancel={() => setEditingPlayer(null)}
      onSave={async payload => {
        await onMutate([mutation("player", editingPlayer.entityId, editingPlayer.version, payload)]);
        setEditingPlayer(null);
      }}
    />}
    {editingGame && <GameEditor
      game={editingGame}
      onCancel={() => setEditingGame(null)}
      onSave={async payload => {
        await onMutate([mutation("game", editingGame.entityId, editingGame.version, payload)]);
        setEditingGame(null);
      }}
    />}
  </div>;
}

function CreateTeam({ user, onLogout, onCreated, message }: { user: AuthUser; onLogout: () => void; onCreated: (id: string) => Promise<void>; message: string }) {
  const [name, setName] = useState("Youth Team");
  const [year, setYear] = useState(new Date().getFullYear());
  return <main className="welcome">
    <div className="welcome-card">
      <span className="eyebrow">Soccer Game Manager Cloud</span>
      <h1>Create your shared team</h1>
      <p className="muted">Signed in as {user.username} ({user.email})</p>
      <p>Create the team and roster entirely on the web. Connect the Android tablet later when you are ready to download the match-day data.</p>
      <label>Team name<input value={name} onChange={event => setName(event.target.value)} /></label>
      <label>Year<input type="number" value={year} onChange={event => setYear(Number(event.target.value))} /></label>
      <button className="primary" onClick={async () => onCreated((await cloudApi.createTeam(name, year)).teamId)}>Create team</button>
      <button onClick={async () => { await cloudApi.logout(); onLogout(); }}>Sign out</button>
      {message && <p>{message}</p>}
    </div>
  </main>;
}

function CreateTeamDialog({ onCancel, onCreated }: { onCancel: () => void; onCreated: (id: string) => Promise<void> }) {
  const [name, setName] = useState("");
  const [year, setYear] = useState(new Date().getFullYear());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  return <div className="dialog-backdrop" role="dialog" aria-modal="true" aria-labelledby="new-team-title">
    <section className="dialog-card"><span className="eyebrow">Team workspace</span><h2 id="new-team-title">Create a new team</h2>
      <p className="muted">This creates an independent cloud roster and schedule. No Android tablet is required.</p>
      <div className="stacked-form">
        <label>Team name<input autoFocus value={name} onChange={event => setName(event.target.value)} placeholder="U9 Thunder" /></label>
        <label>Year<input type="number" min={2000} max={2100} value={year} onChange={event => setYear(Number(event.target.value))} /></label>
      </div>
      {error && <p className="auth-error">{error}</p>}
      <div className="button-row"><button onClick={onCancel}>Cancel</button><button className="primary" disabled={saving || !name.trim()} onClick={async () => {
        setSaving(true); setError("");
        try { await onCreated((await cloudApi.createTeam(name, year)).teamId); }
        catch (caught) { setError(readableError(caught)); setSaving(false); }
      }}>{saving ? "Creating..." : "Create team"}</button></div>
    </section>
  </div>;
}

function PlayerEditor({ player, onCancel, onSave }: { player: CloudEntity; onCancel: () => void; onSave: (payload: Json) => Promise<void> }) {
  const [name, setName] = useState(stringValue(player.payload?.name));
  const [jersey, setJersey] = useState(stringValue(player.payload?.jerseyNumber));
  const [notes, setNotes] = useState(stringValue(player.payload?.notes));
  const [keeperEligible, setKeeperEligible] = useState(player.payload?.preferredKeeper !== false);
  const [saving, setSaving] = useState(false);
  return <div className="dialog-backdrop" role="dialog" aria-modal="true" aria-labelledby="edit-player-title">
    <section className="dialog-card"><span className="eyebrow">Roster details</span><h2 id="edit-player-title">Edit player</h2>
      <div className="stacked-form">
        <label>Player name<input autoFocus value={name} onChange={event => setName(event.target.value)} /></label>
        <label>Jersey number<input value={jersey} onChange={event => setJersey(event.target.value)} /></label>
        <label>Notes<textarea value={notes} onChange={event => setNotes(event.target.value)} rows={3} /></label>
        <label className="check-control"><input type="checkbox" checked={keeperEligible} onChange={event => setKeeperEligible(event.target.checked)} /> Keeper eligible</label>
      </div>
      <div className="button-row"><button onClick={onCancel}>Cancel</button><button className="primary" disabled={saving || !name.trim()} onClick={async () => {
        setSaving(true);
        await onSave({ ...(player.payload ?? {}), name: name.trim(), jerseyNumber: jersey.trim(), notes: notes.trim(), preferredKeeper: keeperEligible });
      }}>{saving ? "Saving..." : "Save player"}</button></div>
    </section>
  </div>;
}

function GameEditor({ game, onCancel, onSave }: { game: CloudEntity; onCancel: () => void; onSave: (payload: Json) => Promise<void> }) {
  const [opponent, setOpponent] = useState(stringValue(game.payload?.opponent));
  const [locationName, setLocationName] = useState(stringValue(game.payload?.location));
  const [scheduledAt, setScheduledAt] = useState(formatDateTimeLocal(numberValue(game.payload?.scheduledAt)));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  return <div className="dialog-backdrop" role="dialog" aria-modal="true" aria-labelledby="edit-game-title">
    <section className="dialog-card"><span className="eyebrow">Schedule details</span><h2 id="edit-game-title">Edit game</h2>
      <p className="muted">Updates are shared with the web planner and paired Android devices.</p>
      <div className="stacked-form">
        <label>Opponent<input autoFocus value={opponent} onChange={event => setOpponent(event.target.value)} /></label>
        <label>Location<input value={locationName} onChange={event => setLocationName(event.target.value)} placeholder="Location TBD" /></label>
        <label>Date and time<input type="datetime-local" value={scheduledAt} onChange={event => setScheduledAt(event.target.value)} /></label>
      </div>
      {error && <p className="auth-error">{error}</p>}
      <div className="button-row"><button disabled={saving} onClick={onCancel}>Cancel</button><button className="primary" disabled={saving || !opponent.trim() || !scheduledAt} onClick={async () => {
        setSaving(true); setError("");
        try {
          await onSave({ ...(game.payload ?? {}), opponent: opponent.trim(), location: locationName.trim(), scheduledAt: new Date(scheduledAt).getTime() });
        } catch (caught) {
          setError(readableError(caught)); setSaving(false);
        }
      }}>{saving ? "Saving..." : "Save game"}</button></div>
    </section>
  </div>;
}

function Overview({ snapshot, onNavigate }: { snapshot: TeamSnapshot; onNavigate: (tab: Tab) => void }) {
  const players = entities(snapshot, "player");
  const games = entities(snapshot, "game").sort((a, b) => numberValue(b.payload?.scheduledAt) - numberValue(a.payload?.scheduledAt));
  const next = games.find(game => stringValue(game.payload?.status) !== "FINAL") ?? games[0];
  return <div className="dashboard-grid">
    <section className="hero-card">
      <span className="eyebrow">Next match</span>
      <h2>{next ? `vs ${stringValue(next.payload?.opponent)}` : "No game scheduled"}</h2>
      <p>{next ? new Date(numberValue(next.payload?.scheduledAt)).toLocaleString() : "Add players and create a game from Roster & Schedule."}</p>
      <div className="button-row"><button className="primary" onClick={() => onNavigate("Planner")}>Open planner</button><button onClick={() => onNavigate("Live")}>Live observer</button></div>
    </section>
    <Stat label="Players" value={players.length} />
    <Stat label="Games" value={games.length} />
    <Stat label="Published lineups" value={snapshot.publishedLineups.length} />
    <Stat label="Cloud changes" value={snapshot.teamRevision} />
  </div>;
}

function Planner({ teamId, snapshot, onMutate, onRefresh, onMessage, onOpenPrint }: {
  teamId: string;
  snapshot: TeamSnapshot;
  onMutate: (commands: MutationCommand[]) => Promise<MutationResult | undefined>;
  onRefresh: () => Promise<void>;
  onMessage: (message: string) => void;
  onOpenPrint: (gameId: string) => void;
}) {
  const players = entities(snapshot, "player").filter(player => player.payload?.active !== false)
    .sort((left, right) => stringValue(left.payload?.name).localeCompare(stringValue(right.payload?.name)));
  const games = entities(snapshot, "game").filter(game => stringValue(game.payload?.status) !== "FINAL");
  const [gameId, setGameId] = useState(games[0]?.entityId ?? "");
  const assignments = entities(snapshot, "assignment").filter(entity => stringValue(entity.payload?.gameId) === gameId);
  const game = games.find(entity => entity.entityId === gameId);
  const availabilityEntities = entities(snapshot, "availability").filter(entity => stringValue(entity.payload?.gameId) === gameId);
  const [formationType, setFormationType] = useState<WebFormationType>("CLASSIC_U9");
  const [halfDuration, setHalfDuration] = useState(25);
  const [rotationMinutes, setRotationMinutes] = useState(4);
  const [plannedRounds, setPlannedRounds] = useState(8);
  const [draftAvailability, setDraftAvailability] = useState<Record<string, { first: boolean; second: boolean }>>({});
  const [manualLocks, setManualLocks] = useState<WebManualLock[]>([]);
  const [activeHalf, setActiveHalf] = useState<1 | 2>(1);
  const [selectedRounds, setSelectedRounds] = useState<Record<number, number>>({ 1: 1, 2: 1 });
  const [picker, setPicker] = useState<{ half: number; round: number; position: string } | null>(null);
  const [armedPlayerId, setArmedPlayerId] = useState("");
  const [publishNeedsReview, setPublishNeedsReview] = useState(false);
  const [lineupName, setLineupName] = useState("");
  const latestPublished = snapshot.publishedLineups.filter(item => item.gameId === gameId).at(-1);
  const gameStatus = stringValue(game?.payload?.status);
  const editable = gameStatus === "PLANNED" || gameStatus === "PREGAME";
  const suggestedRounds = Math.ceil(Math.max(halfDuration, 1) / Math.max(rotationMinutes, 1)) + 1;
  const extraSlots = parseJsonArray(stringValue(game?.payload?.extraLineupSlotsJson)) as Array<Record<string, unknown>>;
  const availableSignature = availabilityEntities.map(item => `${item.entityId}:${item.version}`).join("|");

  useEffect(() => {
    const selected = games.find(item => item.entityId === gameId);
    const selectedTemplate = parseJsonObject(stringValue(selected?.payload?.templateJson));
    setFormationType(isFormationType(selectedTemplate.formationType) ? selectedTemplate.formationType : "CLASSIC_U9");
    const nextDuration = positiveNumber(selectedTemplate.halfDurationMinutes, 25);
    const nextRotation = positiveNumber(selectedTemplate.substitutionWindowMinutes, 4);
    setHalfDuration(nextDuration);
    setRotationMinutes(nextRotation);
    setPlannedRounds(positiveNumber(selectedTemplate.plannedRoundsPerHalf, Math.ceil(nextDuration / nextRotation) + 1));
    setManualLocks(parseJsonArray(stringValue(selected?.payload?.manualGroupLocksJson)) as unknown as WebManualLock[]);
    const storedAvailability = new Map(
      entities(snapshot, "availability")
        .filter(entity => stringValue(entity.payload?.gameId) === gameId)
        .map(entity => [stringValue(entity.payload?.playerId), entity]),
    );
    setDraftAvailability(Object.fromEntries(players.map(player => {
      const stored = storedAvailability.get(player.entityId);
      return [player.entityId, {
        first: stored?.payload?.availableFirstHalf !== false,
        second: stored?.payload?.availableSecondHalf !== false,
      }];
    })));
    setActiveHalf(1);
    setArmedPlayerId("");
  }, [gameId, game?.version, availableSignature]);

  useEffect(() => setPublishNeedsReview(false), [gameId]);

  useEffect(() => {
    if (!games.some(item => item.entityId === gameId) && games[0]) setGameId(games[0].entityId);
  }, [games.length, gameId]);

  const command = (entityType: string, entityId: string, expectedVersion: number, payload: Json | null, operation: MutationCommand["operation"] = "UPSERT_ENTITY", cell?: MutationCommand["cell"]): MutationCommand => ({
    mutationId: crypto.randomUUID(), deviceId: "web", teamId, entityType, entityId, operation, expectedVersion, payload, createdAt: Date.now(), cell,
  });

  const updateGame = async (patch: Json) => {
    if (!game) return;
    await onMutate([command("game", game.entityId, game.version, { ...(game.payload ?? {}), ...patch })]);
  };

  const saveAvailability = async (playerId: string, half: 1 | 2, available: boolean) => {
    const current = draftAvailability[playerId] ?? { first: true, second: true };
    const updated = { ...current, [half === 1 ? "first" : "second"]: available };
    setDraftAvailability(value => ({ ...value, [playerId]: updated }));
    const existing = availabilityEntities.find(entity => stringValue(entity.payload?.playerId) === playerId);
    await onMutate([command("availability", `${gameId}:${playerId}`, existing?.version ?? 0, {
      ...(existing?.payload ?? {}), gameId, playerId,
      isAvailable: updated.first || updated.second,
      isInjured: existing?.payload?.isInjured === true,
      availableFirstHalf: updated.first,
      availableSecondHalf: updated.second,
    })]);
  };

  const saveLock = async (halfNumber: number, positionGroup: string, playerId: string, lockedPosition?: string) => {
    const keyMatches = (lock: WebManualLock) => lock.halfNumber === halfNumber &&
      (lock.lockedPosition ?? null) === (lockedPosition ?? null) && lock.positionGroup === positionGroup;
    const existing = manualLocks.find(keyMatches);
    const alreadySelected = existing?.playerIds.includes(playerId) === true;
    let next = manualLocks.map(lock => ({ ...lock, playerIds: [...lock.playerIds] }));
    if (!alreadySelected) {
      next = next.map(lock => lock.halfNumber === halfNumber
        ? { ...lock, playerIds: lock.playerIds.filter(id => id !== playerId) }
        : lock);
    }
    next = next.filter(lock => lock.playerIds.length > 0 && !keyMatches(lock));
    const selectedIds = alreadySelected
      ? existing!.playerIds.filter(id => id !== playerId)
      : positionGroup === "GOALIE" ? [playerId] : [...(existing?.playerIds ?? []), playerId];
    if (selectedIds.length) next.push({ halfNumber, positionGroup, playerIds: selectedIds, lockedPosition: lockedPosition ?? null });
    setManualLocks(next);
    await updateGame({ manualGroupLocksJson: JSON.stringify(next) });
    if (assignments.length) onMessage("Manual lock saved. Choose Regenerate fresh lineup to apply it to every rotation.");
  };

  const setCellPlayer = async (halfNumber: number, roundIndex: number, position: string, playerId: string) => {
    const current = assignments.find(item => numberValue(item.payload?.halfNumber) === halfNumber &&
      numberValue(item.payload?.roundIndex) === roundIndex && stringValue(item.payload?.position) === position);
    if (current && stringValue(current.payload?.playerId) === playerId) return;
    const rowAssignment = assignments.find(item => numberValue(item.payload?.halfNumber) === halfNumber &&
      numberValue(item.payload?.roundIndex) === roundIndex && stringValue(item.payload?.playerId) === playerId);
    const cell = { gameId, halfNumber, roundIndex, slotKey: position };
    if (!current) {
      if (rowAssignment) return;
      const assignmentId = crypto.randomUUID();
      await onMutate([command("assignment", assignmentId, 0, {
        assignmentId, gameId, playerId, halfNumber, roundIndex, position, positionGroup: groupForPosition(formationType, position),
      }, "SET_LINEUP_CELL", cell)]);
      return;
    }
    const changes = [command("assignment", current.entityId, current.version, { ...(current.payload ?? {}), playerId }, "SET_LINEUP_CELL", cell)];
    if (rowAssignment) {
      changes.push(command("assignment", rowAssignment.entityId, rowAssignment.version, {
        ...(rowAssignment.payload ?? {}), playerId: stringValue(current.payload?.playerId),
      }, "SET_LINEUP_CELL", {
        gameId, halfNumber, roundIndex, slotKey: stringValue(rowAssignment.payload?.position),
      }));
    }
    await onMutate(changes);
  };

  const clearExtraCell = async (halfNumber: number, roundIndex: number, position: string) => {
    const current = assignments.find(item => numberValue(item.payload?.halfNumber) === halfNumber &&
      numberValue(item.payload?.roundIndex) === roundIndex && stringValue(item.payload?.position) === position);
    if (current) await onMutate([command("assignment", current.entityId, current.version, null, "DELETE_ENTITY")]);
  };

  const generate = async (regenerate = false, selectedFormation = formationType, confirmed = false) => {
    if (!game || !editable) return;
    if (assignments.length && !regenerate) return;
    if (assignments.length && !confirmed && !window.confirm("Generate a fresh lineup? This replaces the current draft but does not change published versions.")) return;
    const latest = await cloudApi.snapshot(teamId);
    const latestGame = entities(latest, "game").find(item => item.entityId === gameId);
    if (!latestGame || !["PLANNED", "PREGAME"].includes(stringValue(latestGame.payload?.status))) {
      await onRefresh();
      onMessage("This game changed and can no longer be regenerated. Review the refreshed game status.");
      return;
    }
    const webPlayers: WebLineupPlayer[] = players.map(player => ({
      playerId: player.entityId,
      name: stringValue(player.payload?.name),
      keeperEligible: player.payload?.preferredKeeper !== false,
      availableFirstHalf: draftAvailability[player.entityId]?.first !== false,
      availableSecondHalf: draftAvailability[player.entityId]?.second !== false,
    }));
    const result = generateWebLineup({
      players: webPlayers,
      formationType: selectedFormation,
      halfDurationMinutes: halfDuration,
      substitutionWindowMinutes: rotationMinutes,
      plannedRoundsPerHalf: plannedRounds,
      manualLocks,
      historyByPlayer: lineupHistory(latest, gameId),
      variationSeed: latest.teamRevision + (regenerate ? 1 : 0),
    });
    if (!result.assignments.length) {
      onMessage(result.warnings.join(" ") || "Not enough available players to generate a lineup.");
      return;
    }
    const generatedAssignments = result.assignments.map(generated => {
      const assignmentId = crypto.randomUUID();
      return { assignmentId, gameId, ...generated };
    });
    const latestTemplate = parseJsonObject(stringValue(latestGame.payload?.templateJson));
    const updatedTemplate = {
      ...latestTemplate,
      halfDurationMinutes: halfDuration,
      substitutionWindowMinutes: rotationMinutes,
      plannedRoundsPerHalf: plannedRounds,
      substitutionEventsPerHalf: Math.max(0, plannedRounds - 1),
      formationType: selectedFormation,
      positions: [...FORMATIONS[selectedFormation].positions],
    };
    const updatedGame = {
      ...(latestGame.payload ?? {}),
      status: "PREGAME",
      templateJson: JSON.stringify(updatedTemplate),
      manualGroupLocksJson: JSON.stringify(manualLocks),
      plannerNotes: result.warnings.join("\n"),
    };
    const replacement = await cloudApi.replaceLineup(
      teamId,
      gameId,
      latestGame.version,
      updatedGame,
      generatedAssignments,
      crypto.randomUUID(),
    );
    await onRefresh();
    if (replacement.conflicts.length) {
      onMessage("The game settings changed while the lineup was being regenerated. The existing draft was left unchanged; review the latest changes and try again.");
    } else {
      onMessage(`Lineup draft created with ${result.roundsPerHalf} rounds per half.${result.warnings.length ? ` ${result.warnings.join(" ")}` : ""}`);
    }
  };

  const addExtraSlot = async (halfNumber: 1 | 2, type: string) => {
    if (!game || !editable) return;
    const position = extraPosition(type);
    if (extraSlots.some(slot => numberValue(slot.halfNumber) === halfNumber && stringValue(slot.type) === type)) return;
    const additions: Array<Record<string, unknown>> = [{ slotId: crypto.randomUUID(), type, halfNumber, startRound: 1, endRound: plannedRounds }];
    if (halfNumber === 1 && !extraSlots.some(slot => numberValue(slot.halfNumber) === 2 && stringValue(slot.type) === type)) {
      additions.push({ slotId: crypto.randomUUID(), type, halfNumber: 2, startRound: 1, endRound: plannedRounds });
    }
    await updateGame({ extraLineupSlotsJson: JSON.stringify([...extraSlots, ...additions]), plannerNotes: `${stringValue(game.payload?.plannerNotes)}\n${label(position)} slot added.`.trim() });
  };

  const removeExtraSlot = async (slot: Record<string, unknown>) => {
    if (!game || !editable) return;
    const halfNumber = numberValue(slot.halfNumber);
    const position = extraPosition(stringValue(slot.type));
    const deletions = assignments
      .filter(item => numberValue(item.payload?.halfNumber) === halfNumber && stringValue(item.payload?.position) === position)
      .map(item => command("assignment", item.entityId, item.version, null, "DELETE_ENTITY"));
    const remaining = extraSlots.filter(item => stringValue(item.slotId) !== stringValue(slot.slotId));
    deletions.push(command("game", game.entityId, game.version, { ...(game.payload ?? {}), extraLineupSlotsJson: JSON.stringify(remaining) }));
    await onMutate(deletions);
  };

  const publish = async () => {
    try {
      const published = await cloudApi.publish(teamId, gameId, snapshot.teamRevision, {
        game: game?.payload,
        assignments: assignments.map(item => item.payload),
        publishedFromRevision: snapshot.teamRevision,
      }, lineupName);
      setPublishNeedsReview(false);
      setLineupName("");
      onMessage(`Lineup version ${published.publishedVersion} published.`);
    } catch (error) {
      if (String(error).includes("STALE_DRAFT")) {
        setPublishNeedsReview(true);
        await onRefresh();
        onMessage("New cloud changes arrived. Review the refreshed draft, then publish the latest cloud version.");
      } else {
        onMessage(String(error));
      }
    }
  };

  const publishLatestCloudDraft = async () => {
    try {
      const latest = await cloudApi.snapshot(teamId);
      const latestGame = entities(latest, "game").find(item => item.entityId === gameId);
      const latestAssignments = entities(latest, "assignment")
        .filter(item => stringValue(item.payload?.gameId) === gameId);
      if (!latestGame || !latestAssignments.length) {
        onMessage("The latest cloud draft is incomplete. Refresh the planner before publishing.");
        return;
      }
      const published = await cloudApi.publish(teamId, gameId, latest.teamRevision, {
        game: latestGame.payload,
        assignments: latestAssignments.map(item => item.payload),
        publishedFromRevision: latest.teamRevision,
      }, lineupName);
      setPublishNeedsReview(false);
      setLineupName("");
      await onRefresh();
      onMessage(`Latest cloud lineup published as version ${published.publishedVersion}.`);
    } catch (error) {
      if (String(error).includes("STALE_DRAFT")) {
        await onRefresh();
        onMessage("Another change arrived before publishing. Review the refreshed draft and try again.");
      } else {
        onMessage(String(error));
      }
    }
  };

  if (!games.length) return <Empty text="Create a game in Roster & Schedule before starting a lineup." />;
  const firstHalfAvailable = players.filter(player => draftAvailability[player.entityId]?.first !== false).length;
  const secondHalfAvailable = players.filter(player => draftAvailability[player.entityId]?.second !== false).length;
  const keeperInsight = keeperPlanningInsight(snapshot, gameId, players);
  const currentHalfSlots = extraSlots.filter(slot => numberValue(slot.halfNumber) === activeHalf);

  return <section className="panel">
    <div className="panel-heading">
      <div><span className="eyebrow">Shared lineup draft</span><h2>Planner</h2></div>
      <div className="button-row">
        <select value={gameId} onChange={event => setGameId(event.target.value)}>{games.map(item => <option key={item.entityId} value={item.entityId}>{stringValue(item.payload?.opponent)}</option>)}</select>
        <input aria-label="Lineup name" placeholder="Lineup name (optional)" value={lineupName} onChange={event => setLineupName(event.target.value)} />
        {publishNeedsReview && <button onClick={() => void publishLatestCloudDraft()}>Publish latest cloud draft</button>}
        <button disabled={!assignments.length} onClick={() => onOpenPrint(gameId)}>Print / PDF</button>
        <button className="primary" disabled={!assignments.length || !editable} onClick={() => void publish()}>Publish lineup</button>
      </div>
    </div>
    <p className="muted">{latestPublished ? `${latestPublished.lineupName || `Lineup v${latestPublished.publishedVersion}`} · Published by ${latestPublished.publishedByUser} from ${latestPublished.publishedFromDeviceName} · ${new Date(latestPublished.publishedAt).toLocaleString()}` : "Not published yet"} · Changes are checked cell-by-cell before saving.</p>
    <div className="planner-command-deck">
      <section className="lineup-setup-block"><div className="panel-heading"><div><span className="eyebrow">Match shape</span><h3>Formation</h3></div></div>
        <div className="formation-options">{(Object.keys(FORMATIONS) as WebFormationType[]).map(type => <button key={type} className={`formation-option ${formationType === type ? "selected" : ""}`} onClick={() => {
          if (type === formationType) return;
          if (assignments.length) {
            if (!window.confirm(`Change to ${FORMATIONS[type].label} and generate a fresh lineup?`)) return;
            setFormationType(type);
            void generate(true, type, true);
          } else setFormationType(type);
        }}>
          <strong>{FORMATIONS[type].label}</strong><span>{FORMATIONS[type].description}</span>
        </button>)}</div>
      </section>
      <section className="lineup-setup-block rotation-setup"><div><span className="eyebrow">Match timing</span><h3>Plan rotations</h3><p className="muted">Set the rotation length and exactly how many rows you want prepared for each half.</p></div>
        <div className="rotation-inputs">
          <label>Half minutes<input type="number" min="1" max="90" value={halfDuration} onChange={event => setHalfDuration(positiveNumber(event.target.value, 25))} /></label>
          <label>Minutes per rotation<input type="number" min="1" max="30" value={rotationMinutes} onChange={event => setRotationMinutes(positiveNumber(event.target.value, 4))} /></label>
          <label>Planned rotations<input type="number" min="1" max="20" value={plannedRounds} onChange={event => setPlannedRounds(positiveNumber(event.target.value, suggestedRounds))} /></label>
        </div>
        <button className="quiet-button" onClick={() => setPlannedRounds(suggestedRounds)}>Use suggested {suggestedRounds}</button>
      </section>
    </div>

    <details className="planner-details" open={!assignments.length}>
      <summary><span><strong>Availability and manual locks</strong><small>Half-specific roster, keeper choices, and position-group locks</small></span><span className="availability-counts">H1 {firstHalfAvailable} · H2 {secondHalfAvailable}</span></summary>
      <section className="lineup-setup-block compact-availability"><div className="panel-heading"><div><span className="eyebrow">Availability</span><h3>Who can play each half?</h3></div></div>
        <div className="availability-grid">{players.map(player => <div className="availability-player" key={player.entityId}>
          <strong>{stringValue(player.payload?.name)}</strong>
          <label><input disabled={!editable} type="checkbox" checked={draftAvailability[player.entityId]?.first !== false} onChange={event => void saveAvailability(player.entityId, 1, event.target.checked)} /> H1</label>
          <label><input disabled={!editable} type="checkbox" checked={draftAvailability[player.entityId]?.second !== false} onChange={event => void saveAvailability(player.entityId, 2, event.target.checked)} /> H2</label>
        </div>)}</div>
      </section>
      <div className="manual-lock-halves">{([1, 2] as const).map(half => <ManualLocksEditor key={half} half={half} formationType={formationType} players={players} availability={draftAvailability} locks={manualLocks} disabled={!editable} onToggle={(group, playerId, position) => void saveLock(half, group, playerId, position)} />)}</div>
      <section className="keeper-insight"><div><span className="eyebrow">Keeper rotation</span><h3>Recent and season usage</h3></div><p><strong>Most recent game:</strong> {keeperInsight.recent || "No finalized game yet"}</p><div className="keeper-counts">{keeperInsight.counts.map(item => <span key={item.name}>{item.name} <strong>{item.count}</strong></span>)}</div></section>
    </details>

    {(firstHalfAvailable < 7 || secondHalfAvailable < 7) && <p className="planner-warning">Each half needs at least seven available players. Current availability: Half 1 {firstHalfAvailable}, Half 2 {secondHalfAvailable}.</p>}
    {!assignments.length ? <div className="generate-bar"><div><strong>Ready to build both halves?</strong><span>The app will honor the locks, create {plannedRounds} rotations per half, and keep players in their group within each half.</span></div><button className="primary" disabled={!editable || players.length < 7 || firstHalfAvailable < 7 || secondHalfAvailable < 7} onClick={() => void generate()}>Generate lineup draft</button></div> : <>
      <div className="planner-action-bar"><div><strong>{plannedRounds} rotations · {rotationMinutes} minute target</strong><span>{publishNeedsReview ? "The cloud draft refreshed. Review it before choosing Publish latest cloud draft." : stringValue(game?.payload?.plannerNotes) || "Lineup checks are clear."}</span></div><div className="button-row"><button disabled={!editable} onClick={() => void generate(true)}>Regenerate fresh lineup</button>{publishNeedsReview && <button onClick={() => void publishLatestCloudDraft()}>Publish latest cloud draft</button>}<button onClick={() => onOpenPrint(gameId)}>Print / PDF</button><button className="primary" disabled={!editable} onClick={() => void publish()}>Publish lineup</button></div></div>
      <nav className="half-switcher" aria-label="Choose lineup half">{([1, 2] as const).map(half => <button key={half} className={activeHalf === half ? "active" : ""} onClick={() => { setActiveHalf(half); setArmedPlayerId(""); }}><span>Half {half}</span><small>{half === 1 ? firstHalfAvailable : secondHalfAvailable} available · R{selectedRounds[half]}</small></button>)}</nav>
      <LineupHalfBoard
        half={activeHalf}
        rounds={plannedRounds}
        formationType={formationType}
        players={players}
        availability={draftAvailability}
        assignments={assignments}
        extraSlots={currentHalfSlots}
        selectedRound={selectedRounds[activeHalf] ?? 1}
        armedPlayerId={armedPlayerId}
        editable={editable}
        onSelectRound={round => { setSelectedRounds(current => ({ ...current, [activeHalf]: round })); setArmedPlayerId(""); }}
        onArmPlayer={playerId => setArmedPlayerId(current => current === playerId ? "" : playerId)}
        onSetCell={(round, position, playerId) => { setArmedPlayerId(""); void setCellPlayer(activeHalf, round, position, playerId); }}
        onOpenPicker={(round, position) => armedPlayerId ? void setCellPlayer(activeHalf, round, position, armedPlayerId).then(() => setArmedPlayerId("")) : setPicker({ half: activeHalf, round, position })}
        onClearExtra={(round, position) => void clearExtraCell(activeHalf, round, position)}
        onAddExtra={type => void addExtraSlot(activeHalf, type)}
        onRemoveExtra={slot => void removeExtraSlot(slot)}
      />
    </>}

    {picker && <PlayerPicker
      half={picker.half}
      round={picker.round}
      position={picker.position}
      players={players}
      availability={draftAvailability}
      assignments={assignments}
      extra={picker.position.startsWith("EXTRA_")}
      onChoose={playerId => { void setCellPlayer(picker.half, picker.round, picker.position, playerId); setPicker(null); }}
      onClear={() => { void clearExtraCell(picker.half, picker.round, picker.position); setPicker(null); }}
      onClose={() => setPicker(null)}
    />}
  </section>;
}

function ManualLocksEditor({ half, formationType, players, availability, locks, disabled, onToggle }: {
  half: 1 | 2;
  formationType: WebFormationType;
  players: CloudEntity[];
  availability: Record<string, { first: boolean; second: boolean }>;
  locks: WebManualLock[];
  disabled: boolean;
  onToggle: (group: string, playerId: string, position?: string) => void;
}) {
  const definitions = [
    ...FORMATIONS[formationType].groups.map(group => ({ group: group.key as string, label: groupLabel(group.key), position: undefined as string | undefined })),
    ...(formationType === "ATTACK_BACK_THREE" ? [{ group: "DEFENSE", label: "Center Defense", position: "CENTER_DEFENSE" }] : []),
    { group: "GOALIE", label: "Goalie", position: undefined as string | undefined },
  ];
  const halfLocks = locks.filter(lock => lock.halfNumber === half);
  return <section className="manual-lock-card">
    <div><span className="eyebrow">Half {half}</span><h3>Manual locks</h3><p className="muted">Lock only the players you need. Generate or regenerate applies the lock to the full half; autofill handles everyone else.</p></div>
    <div className="lock-groups">{definitions.map(definition => {
      const selected = halfLocks.find(lock => lock.positionGroup === definition.group && (lock.lockedPosition ?? undefined) === definition.position)?.playerIds ?? [];
      return <div className="lock-group" key={`${definition.group}:${definition.position ?? "group"}`}><strong>{definition.label}</strong><div className="lock-player-list">{players.map(player => {
        const available = half === 1 ? availability[player.entityId]?.first !== false : availability[player.entityId]?.second !== false;
        const lockedElsewhere = halfLocks.some(lock => lock.playerIds.includes(player.entityId) &&
          !(lock.positionGroup === definition.group && (lock.lockedPosition ?? undefined) === definition.position));
        const active = selected.includes(player.entityId);
        return <button type="button" key={player.entityId} className={`lock-player ${active ? "selected" : ""}`} disabled={disabled || !available || (lockedElsewhere && !active)} onClick={() => onToggle(definition.group, player.entityId, definition.position)}>
          {stringValue(player.payload?.name)}{lockedElsewhere && !active ? " · locked" : ""}
        </button>;
      })}</div></div>;
    })}</div>
  </section>;
}

function LineupHalfBoard({ half, rounds, formationType, players, availability, assignments, extraSlots, selectedRound, armedPlayerId, editable, onSelectRound, onArmPlayer, onSetCell, onOpenPicker, onClearExtra, onAddExtra, onRemoveExtra }: {
  half: 1 | 2;
  rounds: number;
  formationType: WebFormationType;
  players: CloudEntity[];
  availability: Record<string, { first: boolean; second: boolean }>;
  assignments: CloudEntity[];
  extraSlots: Array<Record<string, unknown>>;
  selectedRound: number;
  armedPlayerId: string;
  editable: boolean;
  onSelectRound: (round: number) => void;
  onArmPlayer: (playerId: string) => void;
  onSetCell: (round: number, position: string, playerId: string) => void;
  onOpenPicker: (round: number, position: string) => void;
  onClearExtra: (round: number, position: string) => void;
  onAddExtra: (type: string) => void;
  onRemoveExtra: (slot: Record<string, unknown>) => void;
}) {
  const playerNames = new Map(players.map(player => [player.entityId, stringValue(player.payload?.name)]));
  const extraPositions = extraSlots.map(slot => extraPosition(stringValue(slot.type)));
  const positions = [...FORMATIONS[formationType].positions, ...extraPositions.filter((position, index) => extraPositions.indexOf(position) === index)];
  const halfAssignments = assignments.filter(item => numberValue(item.payload?.halfNumber) === half);
  const selectedAssignments = halfAssignments.filter(item => numberValue(item.payload?.roundIndex) === selectedRound);
  const onFieldIds = new Set(selectedAssignments.map(item => stringValue(item.payload?.playerId)));
  const availablePlayers = players.filter(player => half === 1 ? availability[player.entityId]?.first !== false : availability[player.entityId]?.second !== false);
  const benchPlayers = availablePlayers.filter(player => !onFieldIds.has(player.entityId));
  const unavailablePlayers = players.filter(player => !availablePlayers.includes(player));
  const groupOrder = [...FORMATIONS[formationType].groups.map(group => group.key as string), "GOALIE"];
  const groupSummaries = groupOrder.map(group => ({
    group,
    players: [...new Set(halfAssignments.filter(item => stringValue(item.payload?.positionGroup) === group).map(item => playerNames.get(stringValue(item.payload?.playerId)) ?? ""))].filter(Boolean),
  }));

  return <section className="half-board-card">
    <header className="half-board-header"><div><span className="half-badge">Half {half}</span><h2>Lineup board</h2><p>Tap a rotation to update its bench. Drag a player, or tap a bench pill and then a lineup cell.</p></div><div className="extra-slot-actions"><span>Add extra slot</span>{["EXTRA_ATTACK", "EXTRA_MIDFIELD", "EXTRA_DEFENSE"].map(type => <button disabled={!editable || extraPositions.includes(extraPosition(type))} key={type} onClick={() => onAddExtra(type)}>{label(extraPosition(type)).replace("Extra ", "+")}</button>)}</div></header>
    <div className="group-summary">{groupSummaries.map(summary => <article key={summary.group}><strong>{groupLabel(summary.group)}</strong><span>{summary.players.join(", ") || "Not assigned"}</span></article>)}</div>
    <div className="board-legend" aria-label="Lineup color legend"><span><i className="legend-swatch second" />2nd straight rotation</span><span><i className="legend-swatch long" />3+ straight rotations</span><span><i className="legend-swatch changed" />Stays on, changes position</span><span><i className="legend-swatch bench" />Sat 2+ rotations</span><span><i className="legend-swatch unavailable" />Unavailable</span></div>
    <div className="board-and-bench">
      <div className="board-fit" aria-label={`Half ${half} lineup board`}>
        <div className="lineup-board-row lineup-board-head" style={{ gridTemplateColumns: `minmax(42px,.55fr) repeat(${positions.length}, minmax(0,1fr))` }}><div>Rot</div>{positions.map(position => <div key={position} title={label(position)}>{positionShort(position)}</div>)}</div>
        {Array.from({ length: rounds }, (_, index) => index + 1).map(round => <div key={round} className={`lineup-board-row ${selectedRound === round ? "selected" : ""}`} style={{ gridTemplateColumns: `minmax(42px,.55fr) repeat(${positions.length}, minmax(0,1fr))` }}>
          <button className="round-selector" onClick={() => onSelectRound(round)}>R{round}</button>
          {positions.map(position => {
            const assignment = halfAssignments.find(item => numberValue(item.payload?.roundIndex) === round && stringValue(item.payload?.position) === position);
            const playerId = stringValue(assignment?.payload?.playerId);
            const run = playerId ? consecutivePlayed(halfAssignments, playerId, round) : 0;
            const changed = assignment ? changedPosition(halfAssignments, playerId, round, position) : false;
            const tone = run >= 3 ? "long-run" : run === 2 ? "second-run" : "normal";
            return <button
              key={position}
              className={`lineup-cell ${tone} ${changed ? "position-change" : ""} ${!assignment ? "empty" : ""}`}
              disabled={!editable}
              draggable={editable && Boolean(playerId)}
              onDragStart={event => { event.dataTransfer.effectAllowed = "copyMove"; event.dataTransfer.setData("text/player-id", playerId); }}
              onDragOver={event => { if (editable) event.preventDefault(); }}
              onDrop={event => { event.preventDefault(); const dropped = event.dataTransfer.getData("text/player-id"); if (dropped) onSetCell(round, position, dropped); }}
              onClick={() => { onSelectRound(round); onOpenPicker(round, position); }}
              title={`${label(position)} · Rotation ${round}${assignment ? ` · ${playerNames.get(playerId)}` : " · Empty"}`}
            ><span>{assignment ? playerNames.get(playerId) : position.startsWith("EXTRA_") ? "Empty / N/A" : "Empty"}</span></button>;
          })}
        </div>)}
      </div>
      <aside className="bench-panel"><div><span className="eyebrow">Selected row</span><h3>Bench · R{selectedRound}</h3><p>Choose another row in the board to update this list.</p></div>
        <div className="bench-pills">{benchPlayers.length ? benchPlayers.map(player => {
          const sat = consecutiveBench(halfAssignments, player.entityId, selectedRound);
          const group = primaryGroup(halfAssignments, player.entityId);
          return <button key={player.entityId} draggable={editable} className={`bench-pill ${sat >= 2 ? "bench-wait" : ""} ${armedPlayerId === player.entityId ? "armed" : ""}`} onDragStart={event => event.dataTransfer.setData("text/player-id", player.entityId)} onClick={() => onArmPlayer(player.entityId)}>
            <strong>{stringValue(player.payload?.name)}</strong><small>{groupLabel(group) || "No group"}{sat >= 2 ? ` · sat ${sat}` : ""}</small>
          </button>;
        }) : <p className="muted">No eligible bench players.</p>}</div>
        {armedPlayerId && <p className="armed-help">Now tap the position that should receive {playerNames.get(armedPlayerId)}.</p>}
        {unavailablePlayers.length > 0 && <><hr /><span className="eyebrow">Unavailable</span><div className="bench-pills">{unavailablePlayers.map(player => <span className="bench-pill unavailable" key={player.entityId}><strong>{stringValue(player.payload?.name)}</strong><small>Half {half}</small></span>)}</div></>}
      </aside>
    </div>
    {extraSlots.length > 0 && <div className="extra-slot-list">{extraSlots.map(slot => <div key={stringValue(slot.slotId)}><span>{label(extraPosition(stringValue(slot.type)))} · full half</span><button disabled={!editable} onClick={() => onRemoveExtra(slot)}>Remove</button></div>)}</div>}
    {extraPositions.length > 0 && <p className="muted extra-help">Extra slots may be left Empty / N/A in any rotation. Open an extra cell to clear it.</p>}
  </section>;
}

function PlayerPicker({ half, round, position, players, availability, assignments, extra, onChoose, onClear, onClose }: {
  half: number;
  round: number;
  position: string;
  players: CloudEntity[];
  availability: Record<string, { first: boolean; second: boolean }>;
  assignments: CloudEntity[];
  extra: boolean;
  onChoose: (playerId: string) => void;
  onClear: () => void;
  onClose: () => void;
}) {
  const row = assignments.filter(item => numberValue(item.payload?.halfNumber) === half && numberValue(item.payload?.roundIndex) === round);
  const current = row.find(item => stringValue(item.payload?.position) === position);
  return <div className="dialog-backdrop" role="dialog" aria-modal="true"><section className="dialog-card player-picker"><span className="eyebrow">Half {half} · Rotation {round}</span><h2>Set {label(position)}</h2><p>Players already in this rotation will swap positions. Unavailable players remain visible for context.</p>
    {extra && <button onClick={onClear}>Empty / N/A</button>}
    <div className="picker-list">{players.map(player => {
      const available = half === 1 ? availability[player.entityId]?.first !== false : availability[player.entityId]?.second !== false;
      const rowAssignment = row.find(item => stringValue(item.payload?.playerId) === player.entityId);
      const isCurrent = stringValue(current?.payload?.playerId) === player.entityId;
      const duplicateIntoEmptyExtra = extra && !current && Boolean(rowAssignment);
      return <button disabled={!available || duplicateIntoEmptyExtra} key={player.entityId} onClick={() => onChoose(player.entityId)}><strong>{stringValue(player.payload?.name)}</strong><span>{isCurrent ? "Current" : rowAssignment ? `Swap from ${positionShort(stringValue(rowAssignment.payload?.position))}` : available ? "Bench" : "Unavailable this half"}</span></button>;
    })}</div><button onClick={onClose}>Close</button>
  </section></div>;
}

function consecutivePlayed(assignments: CloudEntity[], playerId: string, round: number): number {
  let count = 0;
  for (let index = round; index >= 1 && assignments.some(item => numberValue(item.payload?.roundIndex) === index && stringValue(item.payload?.playerId) === playerId); index -= 1) count += 1;
  return count;
}

function consecutiveBench(assignments: CloudEntity[], playerId: string, round: number): number {
  let count = 0;
  for (let index = round - 1; index >= 1 && !assignments.some(item => numberValue(item.payload?.roundIndex) === index && stringValue(item.payload?.playerId) === playerId); index -= 1) count += 1;
  return count;
}

function changedPosition(assignments: CloudEntity[], playerId: string, round: number, position: string): boolean {
  const previous = assignments.find(item => numberValue(item.payload?.roundIndex) === round - 1 && stringValue(item.payload?.playerId) === playerId);
  return Boolean(previous && stringValue(previous.payload?.position) !== position);
}

function primaryGroup(assignments: CloudEntity[], playerId: string): string {
  const counts = new Map<string, number>();
  assignments.filter(item => stringValue(item.payload?.playerId) === playerId).forEach(item => {
    const group = stringValue(item.payload?.positionGroup);
    counts.set(group, (counts.get(group) ?? 0) + 1);
  });
  return [...counts.entries()].sort((left, right) => right[1] - left[1])[0]?.[0] ?? "";
}

function groupForPosition(formationType: WebFormationType, position: string): string {
  if (position === "GOALIE") return "GOALIE";
  if (position === "EXTRA_ATTACK") return "ATTACK";
  if (position === "EXTRA_MIDFIELD") return "LR_MID";
  if (position === "EXTRA_DEFENSE") return "DEFENSE";
  return FORMATIONS[formationType].groups.find(group => (group.positions as readonly string[]).includes(position))?.key ?? "";
}

function extraPosition(type: string): string {
  return ({ EXTRA_ATTACK: "EXTRA_ATTACK", EXTRA_MIDFIELD: "EXTRA_MIDFIELD", EXTRA_DEFENSE: "EXTRA_DEFENSE" } as Record<string, string>)[type] ?? type;
}

function positionShort(position: string): string {
  return ({ LEFT_DEFENSE: "LD", CENTER_DEFENSE: "CD", RIGHT_DEFENSE: "RD", LEFT_MIDFIELDER: "LM", CENTER_MIDFIELDER: "CM", RIGHT_MIDFIELDER: "RM", STRIKER: "ST", GOALIE: "GK", EXTRA_ATTACK: "+A", EXTRA_MIDFIELD: "+M", EXTRA_DEFENSE: "+D" } as Record<string, string>)[position] ?? position;
}

function groupLabel(group: string): string {
  return ({ ATTACK: "Attack", DEFENSE: "Defense", LR_MID: "L/R Mid", CM_STRIKER: "CM/Striker", GOALIE: "Goalie" } as Record<string, string>)[group] ?? label(group);
}

function keeperPlanningInsight(snapshot: TeamSnapshot, currentGameId: string, players: CloudEntity[]): { recent: string; counts: Array<{ name: string; count: number }> } {
  const playerNames = new Map(players.map(player => [player.entityId, stringValue(player.payload?.name)]));
  const finalGames = entities(snapshot, "game").filter(item => item.entityId !== currentGameId && stringValue(item.payload?.status) === "FINAL")
    .sort((left, right) => numberValue(right.payload?.scheduledAt) - numberValue(left.payload?.scheduledAt));
  const finalIds = new Set(finalGames.map(item => item.entityId));
  const keeperAssignments = entities(snapshot, "assignment").filter(item => finalIds.has(stringValue(item.payload?.gameId)) && stringValue(item.payload?.position) === "GOALIE");
  const halfKeys = new Set<string>();
  const counts = new Map<string, number>();
  for (const assignment of keeperAssignments) {
    const playerId = stringValue(assignment.payload?.playerId);
    const key = `${stringValue(assignment.payload?.gameId)}:${numberValue(assignment.payload?.halfNumber)}:${playerId}`;
    if (halfKeys.has(key)) continue;
    halfKeys.add(key);
    counts.set(playerId, (counts.get(playerId) ?? 0) + 1);
  }
  const recentId = finalGames[0]?.entityId;
  const recent = [...new Set(keeperAssignments.filter(item => stringValue(item.payload?.gameId) === recentId).map(item => playerNames.get(stringValue(item.payload?.playerId)) ?? ""))].filter(Boolean).join(" and ");
  return { recent, counts: players.map(player => ({ name: stringValue(player.payload?.name), count: counts.get(player.entityId) ?? 0 })).sort((left, right) => left.count - right.count || left.name.localeCompare(right.name)) };
}

function ConflictDialog({ conflict, subject, onKeepMine, onUseCloud }: {
  conflict: SyncConflict;
  subject: string;
  onKeepMine: () => Promise<void>;
  onUseCloud: () => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const resolve = async (action: () => Promise<void>) => {
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (reason) {
      setError(readableError(reason));
    } finally {
      setBusy(false);
    }
  };
  return <div className="dialog-backdrop" role="dialog" aria-modal="true" aria-labelledby="conflict-title">
    <section className="dialog-card"><span className="eyebrow">Safe collaboration</span><h2 id="conflict-title">Choose which version to keep</h2>
      <p>Both the web app and another device changed <strong>{subject}</strong>.</p>
      <p><strong>Keep web change</strong> overwrites only this cloud item. <strong>Use cloud change</strong> discards the web edit and refreshes the planner.</p>
      <p className="muted">Web version {conflict.expectedVersion} · Current cloud version {conflict.actualVersion}</p>
      {error && <p className="planner-warning">{error}</p>}
      <div className="button-row"><button className="primary" disabled={busy} onClick={() => void resolve(onKeepMine)}>{busy ? "Resolving..." : "Keep web change"}</button><button disabled={busy} onClick={() => void resolve(onUseCloud)}>Use cloud change</button></div>
    </section>
  </div>;
}

function conflictSubject(conflict: SyncConflict, snapshot: TeamSnapshot | null): string {
  const snapshotEntities = (type: string) => snapshot ? entities(snapshot, type) : [];
  const playerName = (playerId: string) => stringValue(
    snapshotEntities("player").find(player => player.entityId === playerId)?.payload?.name,
  );
  if (conflict.entityType === "availability") {
    const playerId = conflict.entityId.split(":").at(-1) ?? "";
    return playerName(playerId) ? `${playerName(playerId)}'s availability` : "this player's availability";
  }
  if (conflict.entityType === "assignment") {
    const assignment = conflict.serverEntity ?? snapshotEntities("assignment").find(item => item.entityId === conflict.entityId);
    const position = stringValue(assignment?.payload?.position);
    return position ? `the ${label(position)} lineup assignment` : "this lineup assignment";
  }
  if (conflict.entityType === "game") {
    const game = conflict.serverEntity ?? snapshotEntities("game").find(item => item.entityId === conflict.entityId);
    const opponent = stringValue(game?.payload?.opponent);
    return opponent ? `the game against ${opponent}` : "these game settings";
  }
  return `this ${label(conflict.entityType)} item`;
}

function LiveObserver({ snapshot }: { snapshot: TeamSnapshot }) {
  const live = entities(snapshot, "game").find(game => stringValue(game.payload?.status) === "LIVE");
  if (!live) return <Empty text="No live game. The Android match controller remains authoritative during play." />;
  const goals = entities(snapshot, "goal").filter(goal => stringValue(goal.payload?.gameId) === live.entityId);
  const teamGoals = goals.filter(goal => stringValue(goal.payload?.scoredBy) === "TEAM").length;
  return <section className="panel"><span className="eyebrow">Read-only observer</span><h2>{stringValue(live.payload?.opponent)}</h2>
    <div className="score">{teamGoals} <span>–</span> {goals.length - teamGoals}</div>
    <p>Half {numberValue(live.payload?.currentHalf)} · Sub round {numberValue(live.payload?.currentRound)}</p>
    <p className="muted">Live changes are controlled by the paired Android tablet and synchronize whenever it is connected.</p>
  </section>;
}

function History({ snapshot }: { snapshot: TeamSnapshot }) {
  const games = entities(snapshot, "game").filter(game => stringValue(game.payload?.status) === "FINAL");
  const goals = entities(snapshot, "goal");
  return <div className="dashboard-grid"><Stat label="Final games" value={games.length} /><Stat label="Goals recorded" value={goals.length} />
    <section className="panel wide"><h2>Recent games</h2>{games.map(game => <article className="history-row" key={game.entityId}><strong>vs {stringValue(game.payload?.opponent)}</strong><span>{new Date(numberValue(game.payload?.scheduledAt)).toLocaleDateString()}</span></article>)}</section>
  </div>;
}

function Report({ snapshot, teamName, initialGameId }: { snapshot: TeamSnapshot; teamName: string; initialGameId: string }) {
  const games = entities(snapshot, "game").sort((left, right) => numberValue(right.payload?.scheduledAt) - numberValue(left.payload?.scheduledAt));
  const [gameId, setGameId] = useState(initialGameId || games[0]?.entityId || "");
  useEffect(() => {
    if (initialGameId && games.some(game => game.entityId === initialGameId)) setGameId(initialGameId);
  }, [initialGameId]);
  useEffect(() => {
    if (!games.some(game => game.entityId === gameId)) setGameId(games[0]?.entityId ?? "");
  }, [games.length, gameId]);
  const model = buildPrintReportModel(snapshot, teamName, gameId);

  if (!games.length) return <Empty text="Create a game before opening a printable lineup report." />;
  return <div className="report-page">
    <section className="panel print-report-controls">
      <div className="panel-heading"><div><span className="eyebrow">Match plan and report</span><h2>Print lineup</h2><p className="muted">The compact landscape sheet matches the Android report layout and can be printed or saved as a PDF.</p></div><div className="button-row">
        <select aria-label="Game to print" value={gameId} onChange={event => setGameId(event.target.value)}>{games.map(game => <option key={game.entityId} value={game.entityId}>{new Date(numberValue(game.payload?.scheduledAt)).toLocaleDateString()} · {stringValue(game.payload?.opponent)}</option>)}</select>
        <button className="primary" disabled={!model} onClick={() => window.print()}>Print / Save PDF</button>
      </div></div>
    </section>
    {model ? <PrintableGameReport model={model} /> : <Empty text="This game is not available for printing." />}
    <section className="panel published-history"><span className="eyebrow">Published history</span><h2>Lineup versions</h2>
      {snapshot.publishedLineups.length ? snapshot.publishedLineups.map(report => <article className="report-row" key={`${report.gameId}:${report.publishedVersion}`}><strong>{report.lineupName || `Lineup v${report.publishedVersion}`} · Game {report.gameId.slice(0, 8)}</strong><span>v{report.publishedVersion} · {report.publishedByUser} · {report.publishedFromDeviceName} · {new Date(report.publishedAt).toLocaleString()}</span></article>) : <Empty text="Published lineups will appear here." />}
    </section>
  </div>;
}

async function copyText(value: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return;
  }
  const textarea = document.createElement("textarea");
  textarea.value = value;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand("copy");
  textarea.remove();
  if (!copied) throw new Error("Clipboard access was unavailable");
}

function AccessPanel({ teamId, teamName, user, onMessage }: { teamId: string; teamName: string; user: AuthUser; onMessage: (message: string) => void }) {
  const [members, setMembers] = useState<Array<{ email: string; role: MemberRole }>>([]);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<MemberRole>("COACH");
  const [pairingCode, setPairingCode] = useState("");
  const [inviting, setInviting] = useState<"email" | "copy" | null>(null);
  const refresh = () => cloudApi.members(teamId).then(result => setMembers(result.members));
  useEffect(() => { void refresh(); }, [teamId]);

  const copyInvite = async (invitedEmail: string, invitedRole: MemberRole) => {
    const message = buildManualInviteMessage({
      appUrl: window.location.origin,
      invitedEmail,
      inviterName: user.displayName,
      role: invitedRole,
      teamName,
    });
    await copyText(message);
  };

  const invite = async (sendEmail: boolean) => {
    const invitedEmail = email.trim().toLowerCase();
    setInviting(sendEmail ? "email" : "copy");
    try {
      const result = await cloudApi.invite(teamId, invitedEmail, role, sendEmail);
      let copied = true;
      if (!sendEmail) {
        try {
          await copyInvite(invitedEmail, role);
        } catch {
          copied = false;
        }
      }
      setEmail("");
      await refresh();
      onMessage(sendEmail
        ? result.emailSent
          ? `Invitation email sent to ${invitedEmail}. You can also copy it from the member list.`
          : result.deliveryMessage ?? `Access added for ${invitedEmail}, but the email was not sent.`
        : copied
          ? `Access added for ${invitedEmail}. The invitation message and link are copied.`
          : `Access added for ${invitedEmail}, but the browser blocked copying. Use “Copy invite” beside the member to try again.`);
    } catch (error) {
      onMessage(`Invitation was not added: ${readableError(error)}`);
    } finally {
      setInviting(null);
    }
  };

  return <div className="two-column">
    <section className="panel"><h2>Coach access</h2><p className="muted">Invite the exact email the coach will use to activate a Soccer Manager account. Send through the app, or copy a ready-to-send invitation for your own email.</p>
      <div className="form-row access-invite-row">
        <input type="email" aria-label="Coach email" placeholder="coach@example.com" value={email} onChange={event => setEmail(event.target.value)} />
        <select aria-label="Coach role" value={role} onChange={event => setRole(event.target.value as MemberRole)}><option value="COACH">Coach</option><option value="VIEWER">Viewer</option><option value="OWNER">Owner</option></select>
        <button className="primary" disabled={inviting !== null || !email.trim()} onClick={() => void invite(true)}>{inviting === "email" ? "Sending..." : "Invite & email"}</button>
        <button disabled={inviting !== null || !email.trim()} onClick={() => void invite(false)}>{inviting === "copy" ? "Copying..." : "Add access & copy"}</button>
      </div>
      <div className="manual-invite-note"><strong>Prefer your own email?</strong><span>“Add access & copy” puts the full invitation and app link on your clipboard without sending an automated email.</span></div>
      <div className="member-list">
        {members.map(member => <article className="history-row member-row" key={member.email}><div><strong>{member.email}</strong><span>{member.role}</span></div><button onClick={async () => {
          try {
            await copyInvite(member.email, member.role);
            onMessage(`Invitation for ${member.email} copied.`);
          } catch (error) {
            onMessage(`Invitation could not be copied: ${readableError(error)}`);
          }
        }}>Copy invite</button></article>)}
      </div>
    </section>
    <section className="panel"><h2>Download to Android tablet</h2><p className="muted">After building the team and roster here, generate a one-time code and choose “Download cloud team” in Android Setup. The code expires after 10 minutes.</p>
      <button className="primary" onClick={async () => { const result = await cloudApi.pairing(teamId); setPairingCode(result.code); onMessage("Pairing code created."); }}>Generate code</button>
      {pairingCode && <div className="pairing-code">{pairingCode.slice(0, 4)} {pairingCode.slice(4)}</div>}
    </section>
  </div>;
}

function Stat({ label: statLabel, value }: { label: string; value: string | number }) { return <section className="stat-card"><span>{statLabel}</span><strong>{value}</strong></section>; }
function Empty({ text }: { text: string }) { return <div className="empty"><strong>Nothing to show yet</strong><p>{text}</p></div>; }
function LoadingScreen({ message }: { message: string }) { return <main className="welcome"><section className="welcome-card auth-card"><span className="eyebrow">Soccer Game Manager Cloud</span><h1>Loading</h1><p>{message}</p></section></main>; }

function AuthScreen({ registrationOpen, onAuthenticated }: { registrationOpen: boolean; onAuthenticated: (user: AuthUser) => void }) {
  const [mode, setMode] = useState<"LOGIN" | "REGISTER">(registrationOpen ? "REGISTER" : "LOGIN");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [bootstrapCode, setBootstrapCode] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (mode === "REGISTER" && password !== confirmation) {
      setMessage("Passwords do not match.");
      return;
    }
    setSubmitting(true);
    setMessage("");
    try {
      const result = mode === "LOGIN"
        ? await cloudApi.login(identifier, password)
        : await cloudApi.register(username, email, password, registrationOpen ? bootstrapCode : undefined);
      onAuthenticated(result.user);
    } catch (error) {
      setMessage(readableError(error));
    } finally {
      setSubmitting(false);
    }
  };

  return <main className="welcome auth-welcome">
    <section className="welcome-card auth-card">
      <div className="auth-mark" aria-hidden="true">SGM</div>
      <span className="eyebrow">Soccer Game Manager Cloud</span>
      <h1>{mode === "LOGIN" ? "Welcome back" : registrationOpen ? "Create the owner account" : "Activate your invitation"}</h1>
      <p className="muted">{mode === "LOGIN" ? "Sign in to plan and review games with your coaching team." : registrationOpen ? "This first account will create and own the shared team." : "Your team owner must invite your email before you can register."}</p>
      <div className="auth-switch" role="tablist" aria-label="Account access">
        <button type="button" className={mode === "LOGIN" ? "active" : ""} onClick={() => { setMode("LOGIN"); setMessage(""); }}>Sign in</button>
        <button type="button" className={mode === "REGISTER" ? "active" : ""} onClick={() => { setMode("REGISTER"); setMessage(""); }}>{registrationOpen ? "Create account" : "Activate invite"}</button>
      </div>
      <form className="auth-form" onSubmit={event => void submit(event)}>
        {mode === "REGISTER" ? <>
          <label>Username<input required minLength={3} maxLength={30} autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} placeholder="coach-andy" /></label>
          <label>Email<input required type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="coach@example.com" /></label>
          {registrationOpen && <label>Owner setup code<input required autoComplete="one-time-code" value={bootstrapCode} onChange={event => setBootstrapCode(event.target.value)} placeholder="One-time deployment code" /></label>}
        </> : <label>Username or email<input required autoComplete="username" value={identifier} onChange={event => setIdentifier(event.target.value)} placeholder="coach-andy or coach@example.com" /></label>}
        <label>Password<input required type="password" minLength={10} maxLength={128} autoComplete={mode === "LOGIN" ? "current-password" : "new-password"} value={password} onChange={event => setPassword(event.target.value)} /></label>
        {mode === "REGISTER" && <label>Confirm password<input required type="password" minLength={10} maxLength={128} autoComplete="new-password" value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>}
        {message && <p className="auth-error" role="alert">{message}</p>}
        <button className="primary auth-submit" disabled={submitting}>{submitting ? "Please wait..." : mode === "LOGIN" ? "Sign in" : "Create account"}</button>
      </form>
    </section>
  </main>;
}

function entities(snapshot: TeamSnapshot, type: string) { return snapshot.entities.filter(entity => entity.entityType === type && !entity.deletedAt); }
function stringValue(value: unknown) { return typeof value === "string" ? value : ""; }
function numberValue(value: unknown) { return typeof value === "number" ? value : 0; }
function formatDateTimeLocal(timestamp: number) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
function positiveNumber(value: unknown, fallback: number) {
  const parsed = typeof value === "number" ? value : typeof value === "string" ? Number(value) : Number.NaN;
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}
function parseJsonObject(value: string): Json {
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as Json : {};
  } catch {
    return {};
  }
}
function parseJsonArray(value: string): unknown[] {
  try {
    const parsed = JSON.parse(value) as unknown;
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}
function isFormationType(value: unknown): value is WebFormationType { return value === "CLASSIC_U9" || value === "ATTACK_BACK_THREE"; }
function lineupHistory(snapshot: TeamSnapshot, currentGameId: string): Record<string, WebPlayerHistory> {
  const finalGames = entities(snapshot, "game").filter(game => game.entityId !== currentGameId && stringValue(game.payload?.status) === "FINAL");
  const finalGameIds = new Set(finalGames.map(game => game.entityId));
  const gameMinutesPerRound = new Map(finalGames.map(game => {
    const template = parseJsonObject(stringValue(game.payload?.templateJson));
    const halfMinutes = positiveNumber(template.halfDurationMinutes, 25);
    const rotationMinutes = positiveNumber(template.substitutionWindowMinutes, 4);
    const rounds = positiveNumber(template.plannedRoundsPerHalf, Math.ceil(halfMinutes / rotationMinutes) + 1);
    return [game.entityId, halfMinutes / rounds];
  }));
  const history: Record<string, WebPlayerHistory> = {};
  for (const assignment of entities(snapshot, "assignment").filter(item => finalGameIds.has(stringValue(item.payload?.gameId)))) {
    const playerId = stringValue(assignment.payload?.playerId);
    if (!playerId) continue;
    const current = history[playerId] ?? { keeperAssignments: 0, minutesPlayed: 0, groupCounts: {}, positionCounts: {}, totalAssignments: 0 };
    const group = stringValue(assignment.payload?.positionGroup);
    const position = stringValue(assignment.payload?.position);
    current.totalAssignments += 1;
    current.minutesPlayed = (current.minutesPlayed ?? 0) + (gameMinutesPerRound.get(stringValue(assignment.payload?.gameId)) ?? 0);
    current.groupCounts[group] = (current.groupCounts[group] ?? 0) + 1;
    current.positionCounts[position] = (current.positionCounts[position] ?? 0) + 1;
    if (position === "GOALIE" && numberValue(assignment.payload?.roundIndex) === 1) current.keeperAssignments += 1;
    history[playerId] = current;
  }
  return history;
}
function label(value: string) { return value.toLowerCase().split("_").map(part => part[0]?.toUpperCase() + part.slice(1)).join(" "); }
function readableError(error: unknown) { return (error instanceof Error ? error.message : String(error)).replace(/^(UNAUTHORIZED|FORBIDDEN|RATE_LIMITED):/, ""); }

createRoot(document.getElementById("root")!).render(<React.StrictMode><App /></React.StrictMode>);
if ("serviceWorker" in navigator && import.meta.env.PROD) void navigator.serviceWorker.register("/sw.js");
