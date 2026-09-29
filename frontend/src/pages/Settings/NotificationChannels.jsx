import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiSend, FiCheckCircle, FiInfo, FiSlack } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import { notificationChannelService } from '../../services/notificationChannelService'
import { useNotification } from '../../context/NotificationContext'

// Each channel carries its own example URL — a Teams field showing a
// hooks.slack.com address tells the user to paste the wrong thing.
// Each channel carries its own example URL — a Teams field showing a
// hooks.slack.com address tells the user to paste the wrong thing.
//
// Both sets of instructions describe the CURRENT provider UI. Slack renamed
// "From scratch" to "Blank app", and Microsoft has retired the Office 365
// connector route Teams used to use, replacing it with Power Automate.
const CHANNELS = [
  {
    key: 'slack', code: 'SLACK', label: 'Slack', icon: FiSlack,
    example: 'https://hooks.slack.com/services/T00/B00/xxxx',
    help: 'At api.slack.com/apps: Create an App → Blank app → Incoming Webhooks → '
      + 'Activate → Add New Webhook to Workspace → pick a channel → copy the URL.',
  },
  {
    key: 'teams', code: 'TEAMS', label: 'Microsoft Teams', icon: FiSend,
    example: 'https://prod-00.westus.logic.azure.com:443/workflows/…',
    help: 'In Teams: channel ⋯ → Workflows → "Post to a channel when a webhook request '
      + 'is received" → Create → copy the HTTP POST URL. The older Connectors route has been retired.',
  },
]

// Where alerts are delivered, beyond the in-app bell.
//
// The URL is never returned by the API, so the field cannot be pre-filled. It
// shows configured state instead and treats an untouched field as "leave it
// alone" — typing is the only thing that changes a stored webhook.
export default function NotificationChannels() {
  // Viewable while the plan is expired; nothing here can be changed until it is renewed.
  const { readOnly } = useAuth()
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()

  const { data, isLoading } = useQuery({
    queryKey: ['notificationChannels'],
    queryFn: notificationChannelService.get,
  })

  // Local edits. `url` stays undefined until the user types, which is what
  // tells the service to omit it and keep whatever is stored.
  const [draft, setDraft] = useState({ slack: {}, teams: {} })

  useEffect(() => {
    if (data) setDraft({ slack: { enabled: data.slack.enabled }, teams: { enabled: data.teams.enabled } })
  }, [data])

  const save = useMutation({
    mutationFn: () => notificationChannelService.update({
      slack: { enabled: !!draft.slack.enabled, webhookUrl: draft.slack.url },
      teams: { enabled: !!draft.teams.enabled, webhookUrl: draft.teams.url },
    }),
    onSuccess: () => {
      notify.success('Notification channels saved')
      qc.invalidateQueries({ queryKey: ['notificationChannels'] })
    },
    // Enabling a channel with no URL returns 400 with a usable message.
    onError: (err) => notify.error(err?.message || 'Could not save the channels.'),
  })

  const sendTest = useMutation({
    mutationFn: (code) => notificationChannelService.test(code),
    onSuccess: (_d, code) => notify.success(`Test message sent to ${code === 'SLACK' ? 'Slack' : 'Teams'}`),
    onError: (err) => notify.error(err?.message || 'Could not send the test message.'),
  })

  const setChannel = (key, patch) =>
    setDraft((d) => ({ ...d, [key]: { ...d[key], ...patch } }))

  const clearWebhook = async (key, label) => {
    const ok = await confirm({
      title: `Remove the ${label} webhook?`,
      text: `Alerts will stop being delivered to ${label} until a new URL is saved.`,
      confirmText: 'Remove',
    })
    // "" is the explicit clear; undefined would mean "leave it alone".
    if (ok) setChannel(key, { url: '', enabled: false })
  }

  if (isLoading) return <Card><div className="text-muted small">Loading channels…</div></Card>

  return (
    <>
      <Card title="Alert delivery"
        subtitle="Where alerts go beyond the in-app bell. Choose channels per rule on the Alerts page.">
        <div className="d-flex flex-column gap-4">
          {CHANNELS.map(({ key, code, label, icon: Icon, help, example }) => {
            const stored = data?.[key] ?? {}
            const local = draft[key] ?? {}
            const typed = local.url !== undefined
            // Testing hits the stored webhook, so it only makes sense once one
            // is saved — and unsaved typing would not be what gets tested.
            const canTest = stored.configured && !typed

            return (
              <div key={key}>
                <div className="d-flex align-items-center justify-content-between gap-3 mb-2">
                  <label className="d-flex align-items-center gap-2 fw-semibold mb-0" htmlFor={`${key}-enabled`}>
                    <Icon size={16} /> {label}
                  </label>
                  <div className="form-check form-switch mb-0">
                    <input id={`${key}-enabled`} className="form-check-input" type="checkbox" role="switch"
                      checked={!!local.enabled}
                      onChange={(e) => setChannel(key, { enabled: e.target.checked })} />
                  </div>
                </div>

                <label className="form-label" htmlFor={`${key}-url`}>Webhook URL</label>
                <input id={`${key}-url`} type="url" className="form-control"
                  placeholder={stored.configured
                    ? (stored.webhookUrlMasked || 'A webhook is saved — type to replace it')
                    : example}
                  value={local.url ?? ''}
                  onChange={(e) => setChannel(key, { url: e.target.value })} />

                <div className="d-flex flex-wrap align-items-center gap-2 mt-2">
                  {stored.configured && !typed && (
                    <span className="d-inline-flex align-items-center gap-1 text-success small fw-semibold">
                      <FiCheckCircle size={13} /> Webhook saved
                    </span>
                  )}
                  {typed && local.url === '' && (
                    <span className="text-danger small fw-semibold">Will be removed when you save</span>
                  )}
                  {/* Why the test is unavailable, stated on the page rather
                      than hidden in a hover tooltip nobody waits for. */}
                  {!canTest && !(typed && local.url === '') && (
                    <span className="text-muted small">
                      {typed
                        ? 'Save changes to enable the test.'
                        : 'Paste a webhook URL and save to enable the test.'}
                    </span>
                  )}
                  <span className="flex-grow-1" />
                  {stored.configured && !typed && (
                    <Button variant="light" disabled={readOnly} onClick={() => clearWebhook(key, label)}>Remove</Button>
                  )}
                  <Button variant="light" icon={FiSend} disabled={!canTest || readOnly}
                    loading={sendTest.isPending && sendTest.variables === code}
                    onClick={() => sendTest.mutate(code)}>
                    Send test
                  </Button>
                </div>

                <div className="form-text">{help}</div>
              </div>
            )
          })}
        </div>

        <div className="d-flex justify-content-end pt-3 mt-3 border-top-soft">
          <Button loading={save.isPending} disabled={readOnly} title={readOnly ? 'Renew to use this' : undefined} onClick={() => save.mutate()}>Save changes</Button>
        </div>
      </Card>

      <div className="field-note mt-3" style={{ maxWidth: 'none' }}>
        <FiInfo size={15} />
        <span>
          A webhook URL lets anyone holding it post into that channel, so it is stored
          encrypted and never shown again after saving. Use <strong>Send test</strong> to
          confirm it works — a mistyped URL fails silently when a real alert fires.
        </span>
      </div>
    </>
  )
}
