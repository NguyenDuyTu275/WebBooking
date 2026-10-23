import { Client, IMessage } from '@stomp/stompjs';
import type { ChatMessageItem } from '../api';

const RECONNECT_DELAY_MS = 5000;
// Each user only ever receives their own conversation here; the backend refuses any other subscription.
const PERSONAL_QUEUE = '/user/queue/support-chat';

// Vercel rewrites cannot proxy WebSockets, so the deployed site sets VITE_WS_URL to the backend directly.
const resolveWsUrl = () =>
  import.meta.env.VITE_WS_URL || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`;

/**
 * Opens an authenticated STOMP connection (reconnecting automatically) and calls onMessage for every pushed
 * chat message. Pushes sent while the connection was down are lost, so onReconnect runs after every reconnect
 * to let the caller reload. Returns a function that closes the connection.
 */
export function connectSupportChat(
  onMessage: (message: ChatMessageItem) => void,
  onReconnect?: () => void,
): () => void {
  if (!localStorage.getItem('token')) return () => undefined;

  let connectedBefore = false;
  const client = new Client({
    brokerURL: resolveWsUrl(),
    reconnectDelay: RECONNECT_DELAY_MS,
    // Read the token on every attempt, so a reconnect does not keep sending one that has since been replaced.
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${localStorage.getItem('token') ?? ''}` };
    },
    onConnect: () => {
      client.subscribe(PERSONAL_QUEUE, (frame: IMessage) => onMessage(JSON.parse(frame.body)));
      if (connectedBefore) onReconnect?.();
      connectedBefore = true;
    },
    onStompError: frame => console.error('Support chat connection refused:', frame.headers.message),
  });
  client.activate();
  return () => { void client.deactivate(); };
}

/** Appends a message unless it is already there (the sender gets both the REST response and the push). */
export function appendUnique(messages: ChatMessageItem[], message: ChatMessageItem): ChatMessageItem[] {
  return messages.some(m => m.id === message.id) ? messages : [...messages, message];
}
