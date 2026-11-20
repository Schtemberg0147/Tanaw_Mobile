---
name: laws-of-ux
description: Apply Jon Yablonski's 30 Laws of UX (cognitive psychology, Gestalt grouping, interaction ergonomics, and behavioral biases) to design, architect, or audit user interfaces. Use when planning user flows, structuring layouts, designing forms/navigation/onboarding, optimizing perceived performance, or conducting a UX review. Do not use for backend APIs, database schemas, pure color/font palette selection, or non-UI scripts.
---

# Laws of UX Engineering & Audit Skill

Ground UI/UX decisions in empirical human psychology from `lawsofux.com` rather than arbitrary visual guesswork.

## When to Use

- Designing new screens, dashboards, forms, navigation systems, or onboarding flows
- Auditing existing frontend components for cognitive friction, clutter, or poor ergonomics
- Translating UX principles into concrete engineering constraints (latency budgets, target sizes, grouping, progressive disclosure)
- Backing up design decisions with explicit psychological rationale alongside `ui-ux-pro-max` or `antislop-ui`

## Reference Lookup

Before designing or auditing an interface, read `references/laws-catalog.md` (or grep relevant sections) to pull the exact psychological laws, quantitative thresholds, and anti-patterns that apply to the task.

## Workflow

### Mode A: Designing / Building New UI

1. **Map User Intent & Mental Model:** Apply **Jakob's Law**, **Mental Model**, and **Paradox of the Active User**—use familiar platform conventions and make the primary action self-evident without onboarding manuals.
2. **Structure Information Architecture:** Apply **Miller's Law**, **Chunking**, **Hick's Law**, and **Choice Overload**—group related data into 3–5 scannable chunks and hide secondary controls behind progressive disclosure.
3. **Establish Visual Hierarchy (Gestalt):** Apply **Law of Common Region**, **Law of Proximity**, **Law of Similarity**, **Law of Uniform Connectedness**, and **Von Restorff Effect**—create clear visual boundaries for groups and reserve high contrast for the single primary CTA.
4. **Enforce Interaction & Latency Budgets:**
    - **Fitts's Law:** Size primary interactive targets at `>= 44x44px` (mobile) or `>= 32x32px` (desktop), keep related triggers close, and place destructive actions far from primary confirmations.
    - **Doherty Threshold:** Keep UI feedback under `400ms` using optimistic updates, instant hover/active states, and skeleton placeholders.
    - **Postel's Law:** Accept flexible user input formats (phone numbers, dates, whitespace) while normalizing output strictly.
5. **Design Progress & Closure:** Apply **Goal-Gradient Effect**, **Zeigarnik Effect**, and **Peak-End Rule**—show real progress bars in multi-step flows and polish completion/empty/error states.

### Mode B: Auditing Existing UI

1. Inspect the target layout, component hierarchy, form inputs, and async states.
2. Load `references/laws-catalog.md` and evaluate the interface across all four categories:
    - Attention, Memory & Cognitive Load
    - Perception & Visual Grouping (Gestalt)
    - Interaction, Performance & Complexity
    - Motivation, Value & Decision Biases
3. Output a structured **Laws of UX Audit Report**:
    - **Violations Found:** Cite the specific law (e.g., *Hick's Law*), the offending component/file, why it creates user friction, and the exact code fix.
    - **Well-Applied Laws:** Briefly note what already works so refactoring preserves strengths.
    - **Prioritized Fixes (Pareto 80/20):** Rank the top 20% of changes that resolve 80% of user friction.

## Gotchas

- **Do not apply every law mechanically:** Laws operate in tension (e.g., **Tesler's Law** warns against oversimplifying an interface to the point that essential power-user capability is lost). Balance simplicity (**Occam's Razor**) with task power.
- **Keep `SKILL.md` decoupled from brand styling:** Use this skill for structure, ergonomics, and behavior; let `DESIGN.md` or `ui-ux-pro-max` govern brand colors and typography.