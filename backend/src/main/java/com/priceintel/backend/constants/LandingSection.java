package com.priceintel.backend.constants;

/**
 * The editable sections of the public landing page.
 *
 * <p>A fixed set rather than a free-text key: the page renders each section
 * differently, so inventing a new name in the admin screen would store content
 * nothing displays.</p>
 *
 * <p>Declared in the order they appear down the page — the public endpoint
 * sorts by this, so the frontend can render whatever it is given without
 * knowing the running order itself.</p>
 */
public enum LandingSection {

    /** Headline, sub-headline and the two buttons above the fold. */
    HERO,

    /** "Everything you need to price competitively" — the six cards. */
    FEATURES,

    /** "From catalog to confident pricing in three steps". */
    HOW_IT_WORKS,

    /** "Your whole pricing operation, in one place". */
    ONE_WORKSPACE,

    /** The four-figure band — listings tracked, margin lift, and so on. */
    STATS,

    /** "AI that explains the price, not just recommends it". */
    AI_RECOMMENDATIONS,

    /** Customer quotes. */
    TESTIMONIALS,

    /** Questions and answers. */
    FAQS,

    /** The closing call to action above the footer. */
    CTA
}
