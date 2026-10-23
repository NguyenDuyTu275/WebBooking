import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, Input, Spin, message as toast } from 'antd';
import { CloseOutlined, CustomerServiceOutlined, SendOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { supportChatApi, ChatMessageItem } from '../api';
import { appendUnique, connectSupportChat } from '../utils/supportChatSocket';
import { AUTH_CHANGED_EVENT } from '../utils/auth';
import { getErrorMessage } from '../utils/errors';
import { dateLocale } from '../i18n';

// Must match SupportChatService.MAX_MESSAGE_LENGTH on the backend.
const MAX_MESSAGE_LENGTH = 2000;

const isGuestLoggedIn = () => !!localStorage.getItem('token') && localStorage.getItem('role') !== 'ADMIN';

const formatTime = (iso: string) =>
  new Date(iso).toLocaleTimeString(dateLocale(), { hour: '2-digit', minute: '2-digit' });

/** Floating chat with the hotel staff for logged-in guests (bottom-left; the AI assistant is bottom-right). */
const SupportChatWidget: React.FC = () => {
  const { t } = useTranslation();
  const [enabled, setEnabled] = useState(isGuestLoggedIn);
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [unread, setUnread] = useState(0);
  const openRef = useRef(open);
  const bottomRef = useRef<HTMLDivElement>(null);

  openRef.current = open;

  useEffect(() => {
    const onAuthChanged = () => setEnabled(isGuestLoggedIn());
    window.addEventListener(AUTH_CHANGED_EVENT, onAuthChanged);
    return () => window.removeEventListener(AUTH_CHANGED_EVENT, onAuthChanged);
  }, []);

  const handleIncoming = useCallback((incoming: ChatMessageItem) => {
    setMessages(prev => appendUnique(prev, incoming));
    if (!incoming.fromAdmin) return;
    if (openRef.current) {
      supportChatApi.markRead().catch(err => console.error('Failed to mark chat read', err));
    } else {
      setUnread(count => count + 1);
    }
  }, []);

  // Messages pushed while the socket was down never arrive: reload what the widget shows.
  const handleReconnect = useCallback(() => {
    if (openRef.current) {
      supportChatApi.getMine()
        .then(history => setMessages(history.messages))
        .catch(err => console.error('Failed to reload support chat', err));
    } else {
      supportChatApi.unreadCount()
        .then(res => setUnread(res.count))
        .catch(err => console.error('Failed to load unread chat count', err));
    }
  }, []);

  useEffect(() => {
    if (!enabled) {
      setOpen(false);
      setMessages([]);
      setUnread(0);
      return;
    }
    supportChatApi.unreadCount()
      .then(res => setUnread(res.count))
      .catch(err => console.error('Failed to load unread chat count', err));
    return connectSupportChat(handleIncoming, handleReconnect);
  }, [enabled, handleIncoming, handleReconnect]);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    supportChatApi.getMine()
      .then(history => setMessages(history.messages))
      .catch(err => toast.error(getErrorMessage(err, t('supportChat.loadFailed'))))
      .finally(() => setLoading(false));
    supportChatApi.markRead()
      .then(() => setUnread(0))
      .catch(err => console.error('Failed to mark chat read', err));
  }, [open, t]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, open]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || sending) return;
    setSending(true);
    try {
      const sent = await supportChatApi.send(text);
      setMessages(prev => appendUnique(prev, sent));
      setInput('');
    } catch (err) {
      toast.error(getErrorMessage(err, t('supportChat.sendFailed')));
    } finally {
      setSending(false);
    }
  };

  if (!enabled) return null;

  return (
    <>
      <div style={{ position: 'fixed', left: 28, bottom: 28, zIndex: 1000 }}>
        <Badge count={unread} offset={[-4, 4]}>
          <Button
            type="primary"
            shape="circle"
            size="large"
            icon={open ? <CloseOutlined /> : <CustomerServiceOutlined />}
            onClick={() => setOpen(o => !o)}
            style={{ width: 56, height: 56, boxShadow: '0 4px 16px rgba(0,53,128,0.35)' }}
            aria-label={t('supportChat.openLabel')}
          />
        </Badge>
      </div>

      {open && (
        <div style={{
          position: 'fixed', left: 28, bottom: 96, width: 340, maxWidth: 'calc(100vw - 32px)', height: 460,
          background: '#fff', borderRadius: 12, boxShadow: '0 8px 32px rgba(0,0,0,0.18)', zIndex: 1000,
          display: 'flex', flexDirection: 'column', overflow: 'hidden',
        }}>
          <div style={{ background: '#003580', color: '#fff', padding: '12px 16px' }}>
            <div style={{ fontWeight: 700 }}>{t('supportChat.title')}</div>
            <div style={{ fontSize: 12, opacity: 0.85 }}>{t('supportChat.subtitle')}</div>
          </div>

          <div style={{ flex: 1, overflowY: 'auto', padding: 12, background: '#f5f7fa' }}>
            {loading ? (
              <div style={{ textAlign: 'center', paddingTop: 40 }}><Spin /></div>
            ) : messages.length === 0 ? (
              <div style={{ textAlign: 'center', color: '#8c8c8c', marginTop: 40, fontSize: 13 }}>
                {t('supportChat.empty')}
              </div>
            ) : (
              messages.map(m => (
                <div key={m.id} style={{ display: 'flex', justifyContent: m.fromAdmin ? 'flex-start' : 'flex-end', marginBottom: 8 }}>
                  <div style={{
                    maxWidth: '80%', padding: '8px 12px', borderRadius: 12, fontSize: 14, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                    background: m.fromAdmin ? '#fff' : '#006ce4', color: m.fromAdmin ? '#1a1a1a' : '#fff',
                    border: m.fromAdmin ? '1px solid #e8e8e8' : 'none',
                  }}>
                    {m.content}
                    <div style={{ fontSize: 11, opacity: 0.7, marginTop: 2, textAlign: 'right' }}>{formatTime(m.createdAt)}</div>
                  </div>
                </div>
              ))
            )}
            <div ref={bottomRef} />
          </div>

          <div style={{ display: 'flex', gap: 8, padding: 10, borderTop: '1px solid #f0f0f0' }}>
            <Input.TextArea
              value={input}
              onChange={e => setInput(e.target.value)}
              onPressEnter={e => { if (!e.shiftKey) { e.preventDefault(); handleSend(); } }}
              autoSize={{ minRows: 1, maxRows: 4 }}
              maxLength={MAX_MESSAGE_LENGTH}
              placeholder={t('supportChat.placeholder')}
            />
            <Button type="primary" icon={<SendOutlined />} loading={sending} onClick={handleSend} />
          </div>
        </div>
      )}
    </>
  );
};

export default SupportChatWidget;
