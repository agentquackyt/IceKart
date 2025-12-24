# IceKart WebSocket API

**Base URL**: `ws://localhost:3000/ws`

## Server -> Client Events

### `init`
Sent immediately upon connection.
```json
{
  "type": "init",
  "status": "idle", // "idle" | "racing" | "stopped"
  "startTime": null, // timestamp (ms) or null
  "totalLaps": 15,
  "racers": [
    {
      "id": "string",
      "name": "string",
      "avatar": "string",
      "laps": 0,
      "bestLap": null, // number (ms) or null
      "totalTime": 0, // cumulative time from race start (ms)
      "disqualified": false,
      "checkpoints": 0 // checkpoint count within current lap
    }
  ]
}
```

### `update`
Sent when racer data changes (laps, times, checkpoints, disqualification).
```json
{
  "type": "update",
  "racers": [ ... ] // Same structure as init
}
```

### `status`
Sent when the global game status changes.
```json
{
  "type": "status",
  "status": "racing" // "idle" | "racing" | "stopped"
}
```

## Client -> Server Events

### `action`
Control the race state.
```json
{
  "type": "action",
  "payload": "start" // "start" | "stop" | "reset"
}
```

### `lap`
Trigger a lap completion for a specific racer. Updates totalTime, laps, and resets checkpoints.
```json
{
  "type": "lap",
  "racerId": "string"
}
```

### `checkpoint`
Trigger a checkpoint for a specific racer. Updates totalTime and position but NOT the lap count.
```json
{
  "type": "checkpoint",
  "racerId": "string"
}
```

### `disqualify`
Toggle disqualification status for a specific racer.
```json
{
  "type": "disqualify",
  "racerId": "string"
}
```

### `register`
Register a new racer.
```json
{
  "type": "register",
  "name": "string"
}
```

### `remove`
Remove a racer from the game by name.
```json
{
  "type": "remove",
  "name": "string"
}
```

## Display Logic

### Leaderboard Time Display
- **Leader**: Shows "Leader"
- **Same lap as leader**: Shows "+X.XX" (time difference from leader)
- **Behind by laps**: Shows "+N lap(s)" with a blue badge
- **Disqualified**: Shows "Disqualified" with a gray badge (appears at bottom of list)
