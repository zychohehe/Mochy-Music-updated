// Mochy listen together relay.
//
// Rooms live in memory: a host creates one and gets a 6-character code; listeners join with it.
// The server forwards the host's playback snapshots to everyone in the room. It stores nothing
// on disk and never sees audio, only track ids, titles and positions.

'use strict';

const { WebSocketServer, WebSocket } = require('ws');

const PORT = Number(process.env.PORT) || 8080;
const PATH = process.env.WS_PATH || '/ws';
const MAX_MESSAGE_BYTES = 4 * 1024;
const MIN_STATE_INTERVAL_MS = 400; // a host can't flood listeners faster than this
const EMPTY_ROOM_TTL_MS = 60 * 1000;
// no vowels and no look-alike characters, so codes read out loud or typed in are unambiguous
const CODE_ALPHABET = 'BCDFGHJKLMNPQRSTVWXYZ23456789';

/** @type {Map<string, {host: WebSocket, guests: Set<WebSocket>, lastState: object|null, lastStateAt: number, emptyTimer: NodeJS.Timeout|null}>} */
const rooms = new Map();

function makeCode() {
  let code;
  do {
    code = '';
    for (let i = 0; i < 6; i++) {
      code += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
    }
  } while (rooms.has(code));
  return code;
}

function send(ws, payload) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(payload));
}

function membersOf(room) {
  return 1 + room.guests.size;
}

function broadcastMembers(room) {
  const count = membersOf(room);
  send(room.host, { type: 'members', count });
  for (const guest of room.guests) send(guest, { type: 'members', count });
}

function leaveRoom(ws) {
  const code = ws.roomCode;
  if (!code) return;
  const room = rooms.get(code);
  ws.roomCode = null;
  ws.role = null;
  if (!room) return;

  if (room.host === ws) {
    // the host leaving ends the room for everyone
    for (const guest of room.guests) {
      guest.roomCode = null;
      guest.role = null;
      send(guest, { type: 'closed' });
    }
    rooms.delete(code);
    return;
  }

  room.guests.delete(ws);
  broadcastMembers(room);
  if (room.guests.size === 0) {
    // a host alone in the room can come back to it for a while before it's dropped
    room.emptyTimer = setTimeout(() => {
      if (rooms.get(code) === room && room.guests.size === 0) rooms.delete(code);
    }, EMPTY_ROOM_TTL_MS);
  }
}

function parseSafe(raw) {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function sanitizeTrack(track) {
  if (!track || typeof track !== 'object') return null;
  if (typeof track.id !== 'string' || track.id.length === 0 || track.id.length > 64) return null;
  const str = (v, max) => (typeof v === 'string' ? v.slice(0, max) : '');
  return {
    id: track.id,
    title: str(track.title, 200),
    artist: str(track.artist, 200),
    thumbUrl: typeof track.thumbUrl === 'string' && track.thumbUrl.startsWith('https://') ? track.thumbUrl.slice(0, 500) : null,
    durationSec: Number.isFinite(track.durationSec) ? Math.max(0, Math.min(86400, Math.floor(track.durationSec))) : 0,
  };
}

const wss = new WebSocketServer({ port: PORT, path: PATH, maxPayload: MAX_MESSAGE_BYTES });

wss.on('connection', (ws) => {
  ws.roomCode = null;
  ws.role = null;

  ws.on('message', (raw) => {
    const msg = parseSafe(raw.toString());
    if (!msg || typeof msg.type !== 'string') return;

    switch (msg.type) {
      case 'create': {
        leaveRoom(ws);
        const code = makeCode();
        rooms.set(code, { host: ws, guests: new Set(), lastState: null, lastStateAt: 0, emptyTimer: null });
        ws.roomCode = code;
        ws.role = 'host';
        send(ws, { type: 'room', code, role: 'host', members: 1 });
        return;
      }

      case 'join': {
        leaveRoom(ws);
        const code = typeof msg.code === 'string' ? msg.code.trim().toUpperCase() : '';
        const room = rooms.get(code);
        if (!room) {
          send(ws, { type: 'error', reason: 'not_found' });
          return;
        }
        if (room.emptyTimer) {
          clearTimeout(room.emptyTimer);
          room.emptyTimer = null;
        }
        room.guests.add(ws);
        ws.roomCode = code;
        ws.role = 'guest';
        send(ws, { type: 'room', code, role: 'guest', members: membersOf(room) });
        if (room.lastState) send(ws, room.lastState);
        broadcastMembers(room);
        return;
      }

      case 'leave': {
        leaveRoom(ws);
        return;
      }

      case 'state': {
        const room = ws.roomCode && rooms.get(ws.roomCode);
        if (!room || room.host !== ws) return; // only the host drives playback
        const now = Date.now();
        if (now - room.lastStateAt < MIN_STATE_INTERVAL_MS) return;
        room.lastStateAt = now;

        const snapshot = {
          type: 'state',
          track: sanitizeTrack(msg.track),
          positionMs: Number.isFinite(msg.positionMs) ? Math.max(0, Math.floor(msg.positionMs)) : 0,
          playing: msg.playing === true,
          sentAt: Number.isFinite(msg.sentAt) ? Math.floor(msg.sentAt) : now,
        };
        room.lastState = snapshot;
        for (const guest of room.guests) send(guest, snapshot);
        return;
      }

      case 'request_state': {
        const room = ws.roomCode && rooms.get(ws.roomCode);
        if (!room || room.host === ws) return;
        send(room.host, { type: 'request_state' });
        return;
      }

      default:
        return;
    }
  });

  ws.on('close', () => leaveRoom(ws));
  ws.on('error', () => leaveRoom(ws));
});

wss.on('listening', () => {
  console.log(`Mochy listen together relay on ws://0.0.0.0:${PORT}${PATH}`);
});
