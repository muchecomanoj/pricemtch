-- V23 covered seven sections and missed two: the "One workspace" band and the
-- "AI recommendations" band. Both were still written into the React source, so
-- the page would have been half editable — the worst of both, since nobody can
-- remember which half.
--
-- Only the headings and bullet lists are stored. The product table and the
-- recommendation card inside these sections stay in the JSX: they are drawn
-- examples, the same as the dashboard picture in the hero, and an editor that
-- let someone change "Echo Dot" would imply those figures mean something.

INSERT INTO landing_content (section, payload, active, created_at, created_by) VALUES

('ONE_WORKSPACE', '{
  "eyebrow": "ONE WORKSPACE",
  "title": "Your whole pricing operation, in one place",
  "subtitle": "Catalog, competitors, costs and recommendations share one source of truth — so analysts, managers and finance finally work from the same numbers.",
  "bullets": [
    "Role-based access for admins, managers, analysts & finance",
    "Import your catalog by CSV or API in minutes",
    "Every number carries an auditable data-status tag"
  ],
  "cta": "Explore the platform"
}', TRUE, now(), 'V24'),

('AI_RECOMMENDATIONS', '{
  "eyebrow": "AI RECOMMENDATIONS",
  "title": "AI that explains the price, not just recommends it",
  "subtitle": "Every recommendation shows the reasoning, the confidence score and the projected margin impact — so your team can trust it and act, instead of second-guessing a black box.",
  "bullets": [
    "Every move shows its reasoning and confidence score",
    "Projected margin impact before you commit",
    "Stays inside the competitive range you set"
  ],
  "cta": "See AI recommendations"
}', TRUE, now(), 'V24');
