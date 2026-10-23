import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, Empty, Input, List, Spin, message as toast } from 'antd';
import { SendOutlined, UserOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { adminSupportChatApi, ChatMessageItem, ChatRoomSummary } from '../../api';
import i18n, { dateLocale } from '../../i18n';
import { appendUnique, connectSupportChat } from '../../utils/supportChatSocket';
import { getErrorMessage } from '../../utils/errors';

// Must match SupportChatService.MAX_MESSAGE_LENGTH on the backend.
const MAX_MESSAGE_LENGTH = 2000;

const formatTime = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleString(dateLocale(), { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' }) : '';

const AdminSupportChat: React.FC = () => {
  const { t } = useTranslation();
  const [rooms, setRooms] = useState<ChatRoomSummary[]>([]);
  const [loadingRooms, setLoadingRooms] = useState(false);
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const selectedRef = useRef<number | null>(null);
  const roomsRef = useRef<ChatRoomSummary[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);

  // The socket callback is created once, so it reads the latest selection and list through refs.
  selectedRef.current = selectedRoomId;
  roomsRef.current = rooms;

  const loadRooms = useCallback(async () => {
    setLoadingRooms(true);
    try {
      setRooms(await adminSupportChatApi.rooms());
    } catch (err) {
      // i18n.t keeps this callback stable, so switching language does not reconnect the socket.
      toast.error(getErrorMessage(err, i18n.t('admin.chat.roomsLoadFailed')));
    } finally {
      setLoadingRooms(false);
    }
  }, []);

  const handleIncoming = useCallback((incoming: ChatMessageItem) => {
    const isOpenRoom = incoming.roomId === selectedRef.current;
    if (isOpenRoom) {
      setMessages(prev => appendUnique(prev, incoming));
      if (!incoming.fromAdmin) {
        adminSupportChatApi.markRead(incoming.roomId).catch(err => console.error('Failed to mark chat read', err));
      }
    }
    if (!roomsRef.current.some(r => r.roomId === incoming.roomId)) {
      // A guest started a new conversation: reload so it appears with its username.
      loadRooms();
      return;
    }
    setRooms(prev => {
      const known = prev.find(r => r.roomId === incoming.roomId);
      if (!known) return prev;
      const updated: ChatRoomSummary = {
        ...known,
        lastMessageAt: incoming.createdAt,
        lastMessagePreview: incoming.content,
        unreadCount: !incoming.fromAdmin && !isOpenRoom ? known.unreadCount + 1 : known.unreadCount,
      };
      return [updated, ...prev.filter(r => r.roomId !== incoming.roomId)];
    });
  }, [loadRooms]);

  // Messages pushed while the socket was down never arrive: reload the list and the open conversation.
  const handleReconnect = useCallback(() => {
    loadRooms();
    const roomId = selectedRef.current;
    if (roomId === null) return;
    adminSupportChatApi.messages(roomId)
      .then(list => { if (selectedRef.current === roomId) setMessages(list); })
      .catch(err => console.error('Failed to reload chat messages', err));
  }, [loadRooms]);

  useEffect(() => {
    loadRooms();
    return connectSupportChat(handleIncoming, handleReconnect);
  }, [loadRooms, handleIncoming, handleReconnect]);

  useEffect(() => {
    if (selectedRoomId === null) return;
    setLoadingMessages(true);
    adminSupportChatApi.messages(selectedRoomId)
      .then(setMessages)
      .catch(err => toast.error(getErrorMessage(err, i18n.t('admin.chat.messagesLoadFailed'))))
      .finally(() => setLoadingMessages(false));
    adminSupportChatApi.markRead(selectedRoomId)
      .then(() => setRooms(prev => prev.map(r => (r.roomId === selectedRoomId ? { ...r, unreadCount: 0 } : r))))
      .catch(err => console.error('Failed to mark chat read', err));
  }, [selectedRoomId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || selectedRoomId === null || sending) return;
    setSending(true);
    try {
      const sent = await adminSupportChatApi.reply(selectedRoomId, text);
      setMessages(prev => appendUnique(prev, sent));
      setInput('');
    } catch (err) {
      toast.error(getErrorMessage(err, t('supportChat.sendFailed')));
    } finally {
      setSending(false);
    }
  };

  const selectedRoom = rooms.find(r => r.roomId === selectedRoomId);

  return (
    <div>
      <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 20, color: '#1a1a1a' }}>{t('admin.menu.chat')}</h2>
      <div style={{ display: 'flex', height: 'calc(100vh - 200px)', minHeight: 420, background: '#fff', borderRadius: 8, overflow: 'hidden', border: '1px solid #f0f0f0' }}>
        <div style={{ width: 280, borderRight: '1px solid #f0f0f0', overflowY: 'auto' }}>
          <List
            loading={loadingRooms}
            dataSource={rooms}
            locale={{ emptyText: t('admin.chat.empty') }}
            renderItem={room => (
              <List.Item
                onClick={() => setSelectedRoomId(room.roomId)}
                style={{ cursor: 'pointer', padding: '12px 16px', background: room.roomId === selectedRoomId ? '#e6f4ff' : undefined }}
              >
                <List.Item.Meta
                  avatar={<Badge count={room.unreadCount} size="small"><UserOutlined style={{ fontSize: 20, color: '#003580' }} /></Badge>}
                  title={<span style={{ fontWeight: room.unreadCount > 0 ? 700 : 500 }}>{room.username}</span>}
                  description={
                    <div style={{ fontSize: 12 }}>
                      <div style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{room.lastMessagePreview}</div>
                      <div style={{ color: '#bfbfbf' }}>{formatTime(room.lastMessageAt)}</div>
                    </div>
                  }
                />
              </List.Item>
            )}
          />
        </div>

        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          {selectedRoomId === null ? (
            <div style={{ margin: 'auto' }}><Empty description={t('admin.chat.selectRoom')} /></div>
          ) : (
            <>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', fontWeight: 700 }}>
                {selectedRoom?.username}
              </div>
              <div style={{ flex: 1, overflowY: 'auto', padding: 16, background: '#f5f7fa' }}>
                {loadingMessages ? (
                  <div style={{ textAlign: 'center', paddingTop: 40 }}><Spin /></div>
                ) : (
                  messages.map(m => (
                    <div key={m.id} style={{ display: 'flex', justifyContent: m.fromAdmin ? 'flex-end' : 'flex-start', marginBottom: 8 }}>
                      <div style={{
                        maxWidth: '70%', padding: '8px 12px', borderRadius: 12, fontSize: 14, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                        background: m.fromAdmin ? '#006ce4' : '#fff', color: m.fromAdmin ? '#fff' : '#1a1a1a',
                        border: m.fromAdmin ? 'none' : '1px solid #e8e8e8',
                      }}>
                        {m.fromAdmin && <div style={{ fontSize: 11, opacity: 0.8 }}>{m.senderUsername}</div>}
                        {m.content}
                        <div style={{ fontSize: 11, opacity: 0.7, marginTop: 2, textAlign: 'right' }}>{formatTime(m.createdAt)}</div>
                      </div>
                    </div>
                  ))
                )}
                <div ref={bottomRef} />
              </div>
              <div style={{ display: 'flex', gap: 8, padding: 12, borderTop: '1px solid #f0f0f0' }}>
                <Input.TextArea
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  onPressEnter={e => { if (!e.shiftKey) { e.preventDefault(); handleSend(); } }}
                  autoSize={{ minRows: 1, maxRows: 4 }}
                  maxLength={MAX_MESSAGE_LENGTH}
                  placeholder={t('admin.chat.placeholder')}
                />
                <Button type="primary" icon={<SendOutlined />} loading={sending} onClick={handleSend}>{t('admin.chat.send')}</Button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default AdminSupportChat;
