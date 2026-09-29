// Fallback email templates + the merge variables each supports. These mirror the
// live /admin/email-templates contract (same keys + variables) and are used only
// when TEMPLATES_LIVE is off or the endpoint is unreachable — the real list is
// rendered dynamically from the API, so new templates appear automatically.

export const EMAIL_TEMPLATES = [
  {
    key: 'CLIENT_ACTIVATION',
    name: 'Client activation invite',
    description: 'Sent when a super admin creates a client — carries the activation link + code.',
    subject: 'You’re invited to {{appName}}',
    body: [
      'Hi {{contactPerson}},',
      '',
      '{{companyName}} has been set up on {{appName}}. Click below to verify your email, create a password and activate your subscription.',
      '',
      'Activate your account: {{activationLink}}',
      'Verification code: {{verificationCode}}',
      'Company code: {{companyCode}}',
      '',
      'This secure link expires in {{expiryHours}} hours. If you didn’t expect this, you can ignore this email.',
    ].join('\n'),
    variables: ['appName', 'contactPerson', 'companyName', 'activationLink', 'verificationCode', 'expiryHours', 'companyCode'],
    edited: false,
  },
  {
    key: 'VERIFICATION_CODE',
    name: 'Verification code (self-registration)',
    description: 'The email verification code sent during public self-registration.',
    subject: 'Your {{appName}} verification code',
    body: [
      'Hi {{contactPerson}},',
      '',
      'Use this code to verify your email for {{companyName}} on {{appName}}:',
      '',
      '{{verificationCode}}',
      '',
      'This code expires in {{expiryMinutes}} minutes.',
    ].join('\n'),
    variables: ['appName', 'contactPerson', 'companyName', 'verificationCode', 'expiryMinutes'],
    edited: false,
  },
  {
    key: 'PASSWORD_RESET',
    name: 'Password reset code',
    description: 'The reset code email from the forgot-password flow.',
    subject: 'Your {{appName}} password reset code',
    body: [
      'Hi,',
      '',
      'Use this code to reset your {{appName}} password:',
      '',
      '{{code}}',
      '',
      'This code expires in {{expiryMinutes}} minutes. If you didn’t request a reset, you can ignore this email.',
    ].join('\n'),
    variables: ['appName', 'code', 'expiryMinutes'],
    edited: false,
  },
  {
    key: 'WELCOME',
    name: 'Welcome / payment confirmation',
    description: 'Sent after a client’s payment succeeds and their subscription activates.',
    subject: 'Welcome to {{appName}} — your {{planName}} plan is active',
    body: [
      'Hi {{contactPerson}},',
      '',
      'Your payment was successful and {{companyName}} ({{companyCode}}) is now on the {{planName}} plan ({{billingCycle}}).',
      'Status: {{status}}',
      '{{trialLine}}',
      '',
      'Sign in to your workspace: {{loginUrl}}',
      '',
      'Thanks for choosing {{appName}}.',
    ].join('\n'),
    variables: ['appName', 'contactPerson', 'companyName', 'companyCode', 'planName', 'billingCycle', 'status', 'trialLine', 'loginUrl'],
    edited: false,
  },
  {
    key: 'PLAN_CHANGE_PAYLINK',
    name: 'Plan change pay-link',
    description: 'The Stripe pay-link email for a plan change — the plan activates once paid.',
    subject: 'Complete your change to {{planName}}',
    body: [
      'Hi {{contactPerson}},',
      '',
      'You’re changing {{companyName}} to the {{planName}} plan ({{billingCycle}}) for {{amount}}.',
      '',
      'Complete your payment to activate the new plan: {{payLink}}',
      '',
      'Your current plan stays active until payment is confirmed.',
    ].join('\n'),
    variables: ['appName', 'contactPerson', 'companyName', 'planName', 'billingCycle', 'amount', 'payLink'],
    edited: false,
  },
]

// Sample values used only for the CLIENT-SIDE preview of unsaved edits (the
// backend preview endpoint supplies its own realistic sample data).
export const TEMPLATE_SAMPLE = {
  appName: 'Price Intelligence',
  companyName: 'Acme Corp',
  companyCode: 'ACME-1042',
  contactPerson: 'Jane Doe',
  activationLink: 'https://app.example.com/activate?token=…',
  verificationCode: '482913',
  expiryHours: '48',
  expiryMinutes: '15',
  planName: 'Professional',
  billingCycle: 'Yearly',
  status: 'ACTIVE',
  trialLine: 'Your trial runs until Aug 21, 2026.',
  loginUrl: 'https://app.example.com/login',
  amount: '$1,430.40',
  payLink: 'https://checkout.stripe.com/c/pay/cs_test_…',
  code: '482913',
  // SUBSCRIPTION_EXPIRING / SUBSCRIPTION_EXPIRED. Without these the editor's
  // preview rendered "ends on ()" — the server-side preview had them, this
  // live one did not.
  paidThrough: '29 Sep 2026',
  endsIn: 'in 7 days',
  daysLeft: '7',
  graceDays: '3',
  accessEndsOn: '2 Oct 2026',
  renewLink: 'https://app.example.com/my-subscription',
}
