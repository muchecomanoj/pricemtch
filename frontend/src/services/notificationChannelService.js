import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Slack / Teams delivery for alerts — GET/PUT /notification-channels,
// POST /notification-channels/test.
//
// Webhook URLs are credentials: anyone holding one can post into that channel,
// so the API never returns them. A read gives { enabled, configured,
// webhookUrlMasked } per channel, and a write may omit the URL to keep the
// stored one. That shapes the whole form — see the comments on update().

const E = ENDPOINTS.notificationChannels
const emptyChannel = { enabled: false, configured: false, webhookUrlMasked: null }

export const notificationChannelService = {
  async get() {
    if (USE_MOCK) return { slack: emptyChannel, teams: emptyChannel }
    const { data } = await api.get(E.root)
    return {
      slack: data?.slack ?? emptyChannel,
      teams: data?.teams ?? emptyChannel,
    }
  },

  // Three distinct intentions the form has to express, and the difference
  // between them is what the URL field contains:
  //   undefined  leave the stored webhook exactly as it is
  //   ""         clear it
  //   "https://" replace it
  //
  // Sending `null` for an untouched field would clear it, so untouched fields
  // are dropped from the payload entirely rather than sent as empty.
  async update({ slack, teams }) {
    if (USE_MOCK) return { slack: emptyChannel, teams: emptyChannel }
    const body = {
      slackEnabled: slack.enabled,
      teamsEnabled: teams.enabled,
    }
    if (slack.webhookUrl !== undefined) body.slackWebhookUrl = slack.webhookUrl
    if (teams.webhookUrl !== undefined) body.teamsWebhookUrl = teams.webhookUrl
    const { data } = await api.put(E.root, body)
    return data
  },

  // Proves the URL works while the user is still on the screen. Without it a
  // mistyped webhook fails silently at 3am and nobody finds out until they ask
  // why no alert arrived.
  async test(channel) {
    if (USE_MOCK) return { ok: true }
    return (await api.post(E.test, { channel })).data
  },
}
