# Laws of UX — Complete 30-Law Engineering Reference

Source: [lawsofux.com](https://lawsofux.com/) (Jon Yablonski)

---

## 1. Attention, Memory & Cognitive Load

### Cognitive Load
- **Principle:** The total amount of mental resources needed to understand and interact with an interface.
- **Engineering Rule:** Remove redundant labels, visual noise, and unnecessary decisions; default smart values wherever possible.
- **Anti-Pattern:** Dense walls of controls, competing banners, and unexplained jargon on first view.

### Working Memory
- **Principle:** A cognitive system that temporarily holds and manipulates limited information needed to complete active tasks.
- **Engineering Rule:** Never force users to remember values across screens, steps, or modals—keep reference context visible inline.
- **Anti-Pattern:** Hiding validation rules until after form submission, or making users memorize an ID from page A to type into page B.

### Miller's Law
- **Principle:** The average person can hold roughly 7 (plus or minus 2) items in working memory.
- **Engineering Rule:** Cap top-level navigation items, tab bars, and ungrouped lists at 5–7 items; organize larger sets into labeled categories.
- **Anti-Pattern:** 14 top-level navbar links or flat, ungrouped dropdown menus with 25 items.

### Chunking
- **Principle:** Breaking individual pieces of information down and grouping them into meaningful wholes.
- **Engineering Rule:** Format long strings (`4111 1111 1111 1111`, `+1 (555) 234-5678`), split long forms into logical sections, and use clear visual section headers.
- **Anti-Pattern:** A single 20-field vertical form with no section dividers or auto-formatting.

### Selective Attention
- **Principle:** Users focus attention only on stimuli relevant to their current goal and filter out the rest.
- **Engineering Rule:** Align visual emphasis, microcopy, and motion strictly with the user's active task; silence non-essential badges and popups.
- **Anti-Pattern:** Auto-playing carousels, blinking badges, or newsletter modals interrupting an active checkout or search flow.

### Hick's Law
- **Principle:** Decision time increases logarithmically with the number and complexity of choices.
- **Engineering Rule:** Reduce simultaneous options, highlight a recommended default, and reveal advanced settings progressively.
- **Anti-Pattern:** Presenting 12 equal-weight action buttons on a single toolbar or card.

### Choice Overload
- **Principle:** People become overwhelmed and defer decisions when presented with too many options (paradox of choice).
- **Engineering Rule:** Limit pricing/plan tiers to 3–4 options, provide comparison filters, and pre-select the most common choice.
- **Anti-Pattern:** An unfiltered grid of 50 templates or plans with no "Recommended" or "Most Popular" anchor.

### Serial Position Effect
- **Principle:** Users best remember the first (primacy) and last (recency) items in a series.
- **Engineering Rule:** Place the most critical navigation links or actions at the far left/top and far right/bottom of bars and lists.
- **Anti-Pattern:** Burying the primary navigation item or key summary metric in the middle of a 7-item bar.

### Von Restorff Effect (Isolation Effect)
- **Principle:** When multiple similar objects are present, the one that differs from the rest is most likely to be remembered.
- **Engineering Rule:** Give the primary CTA a distinct visual treatment (solid fill/accent) while keeping secondary actions neutral/ghost.
- **Anti-Pattern:** Making every button on the screen the same bright accent color, which eliminates focal hierarchy.

---

## 2. Perception & Visual Grouping (Gestalt)

### Law of Prägnanz (Simplicity)
- **Principle:** People perceive and interpret ambiguous or complex visuals as the simplest form possible to minimize cognitive effort.
- **Engineering Rule:** Use clean geometric grids, clear icon silhouettes, and unambiguous visual states rather than ornate layouts.
- **Anti-Pattern:** Overly abstract icons without labels or overlapping decorative shapes that obscure layout structure.

### Law of Proximity
- **Principle:** Objects near each other are perceived as a related group.
- **Engineering Rule:** Maintain tight spacing within a component/group (`4px–12px`) and noticeably larger spacing between distinct groups (`24px–48px`).
- **Anti-Pattern:** Equal vertical spacing (`16px`) between a form label, its input, and the next field's label, making ownership ambiguous.

### Law of Common Region
- **Principle:** Elements sharing an area with a clearly defined boundary are perceived as grouped together.
- **Engineering Rule:** Use subtle background fills, borders, or cards to enclose distinct functional modules (e.g., summary panels, filter groups).
- **Anti-Pattern:** Floating unrelated widgets on an undifferentiated canvas without clear containers or alignment boundaries.

### Law of Similarity
- **Principle:** Elements that share visual characteristics (color, shape, size, typography) are perceived as related or functionally identical.
- **Engineering Rule:** Keep all interactive links/buttons consistent in style, and never style non-interactive badges to look like clickable buttons.
- **Anti-Pattern:** Static status pills styled identically to primary action buttons.

### Law of Uniform Connectedness
- **Principle:** Elements visually connected by lines, arrows, or shared tracks are perceived as more related than unconnected elements.
- **Engineering Rule:** Use connector lines, stepper tracks, or tree guides for multi-step workflows, timelines, and parent-child hierarchies.
- **Anti-Pattern:** Disconnected step numbers floating above separate columns with no visual progression link.

---

## 3. Interaction, Performance & Complexity

### Fitts's Law
- **Principle:** The time to acquire a target is a function of the distance to and size of the target.
- **Engineering Rule:** Make frequent/primary targets large (`>= 44x44px` touch, `>= 32x32px` pointer), expand clickable label areas (`<label for="...">`), and separate destructive buttons from safe actions.
- **Anti-Pattern:** Tiny `12px` icon-only close/delete buttons placed immediately adjacent to "Save" or "Confirm".

### Doherty Threshold
- **Principle:** Productivity soars when computer and user interact at a pace (`< 400ms`) that ensures neither waits on the other.
- **Engineering Rule:** Render optimistic UI updates immediately, respond to input within `100ms`, and display skeleton loaders if network calls exceed `400ms`.
- **Anti-Pattern:** Frozen buttons with no loading state during a 2-second API request.

### Jakob's Law
- **Principle:** Users spend most of their time on other sites and expect your interface to work the same way as familiar patterns.
- **Engineering Rule:** Follow standard placement and mechanics for search bars, shopping carts, settings icons, keyboard shortcuts (`Cmd+K`, `Esc`), and form controls.
- **Anti-Pattern:** Reinventing standard scroll behavior, custom non-standard checkboxes, or hiding navigation behind experimental gestures.

### Mental Model
- **Principle:** A user's internal compressed model of how a system works based on prior experience.
- **Engineering Rule:** Match UI structure, terminology, and state transitions to how users conceptualize their domain (e.g., folders/tags, draft/published).
- **Anti-Pattern:** Exposing internal database table names or backend state-machine jargon directly in user-facing UI.

### Tesler's Law (Conservation of Complexity)
- **Principle:** Every system has an irreducible amount of complexity; if you strip it from the UI improperly, you shift the burden onto the user (or vice versa).
- **Engineering Rule:** Absorb complexity in code (auto-detection, smart defaults, batch actions) rather than forcing manual user configuration—while preserving essential controls for complex tasks.
- **Anti-Pattern:** Over-minimalist "clean" UIs that hide critical filters or force users to perform 10 manual clicks to achieve a routine task.

### Postel's Law (Robustness Principle)
- **Principle:** Be liberal in what you accept, and conservative in what you send.
- **Engineering Rule:** Accept flexible user inputs (spaces/dashes in cards, case-insensitive emails, relative dates) and sanitize/standardize data reliably before storage or display.
- **Anti-Pattern:** Rejecting a valid phone number or credit card because the user typed spaces or hyphens.

### Paradox of the Active User
- **Principle:** Users rarely read manuals or tutorials; they start interacting with software immediately.
- **Engineering Rule:** Replace upfront product tours with contextual empty states, inline placeholders, and just-in-time tooltips triggered at the moment of need.
- **Anti-Pattern:** A 6-step blocking onboarding carousel before the user can see or touch the workspace.

### Flow
- **Principle:** The mental state of energized focus and full involvement when challenge matches skill with clear goals and immediate feedback.
- **Engineering Rule:** Eliminate modal interruptions, preserve scroll/edit state across transitions, and support keyboard-first power workflows.
- **Anti-Pattern:** Full-page reloads, lost form drafts on navigation, or unprompted popups breaking deep work.

---

## 4. Motivation, Value & Decision Biases

### Aesthetic-Usability Effect
- **Principle:** Users perceive aesthetically pleasing designs as easier to use and are more tolerant of minor usability issues.
- **Engineering Rule:** Invest in crisp typography, consistent spacing rhythm, balanced contrast, and refined micro-interactions to build immediate user trust.
- **Anti-Pattern:** Misaligned baselines, inconsistent border radii, and raw unstyled browser defaults in user-facing workflows.

### Peak-End Rule
- **Principle:** People judge an experience largely based on how they felt at its most intense moment (peak) and at its end, rather than the average of every moment.
- **Engineering Rule:** Craft high-clarity confirmation screens, celebratory/reassuring completion states, and graceful, helpful error recovery paths.
- **Anti-Pattern:** Abruptly dumping users onto a blank screen after completing a complex workflow, or showing a raw `500 Internal Server Error`.

### Goal-Gradient Effect
- **Principle:** The tendency to approach a goal increases as users get closer to completing it.
- **Engineering Rule:** Start multi-step progress indicators with initial momentum (e.g., Step 1 of 4 already active or "20% complete" after account creation) and show clear remaining steps.
- **Anti-Pattern:** Indeterminate multi-page wizards with no step counter or progress bar.

### Zeigarnik Effect
- **Principle:** People remember uncompleted or interrupted tasks better than completed tasks.
- **Engineering Rule:** Use subtle, non-intrusive completion trackers (e.g., "Profile 75% complete — add recovery email") and auto-save drafts so users can resume effortlessly.
- **Anti-Pattern:** Discarding incomplete user progress on session timeout without saving state.

### Occam's Razor
- **Principle:** Among competing designs that solve a problem equally well, the one with the fewest assumptions and moving parts should be selected.
- **Engineering Rule:** Cut decorative wrappers, redundant icons, and speculative UI controls that do not directly serve a validated user task.
- **Anti-Pattern:** Adding charts, metrics, and secondary panels to a view "just in case" without a clear user decision attached.

### Pareto Principle (80/20 Rule)
- **Principle:** Roughly 80% of outcomes come from 20% of causes or features.
- **Engineering Rule:** Surface the top 20% most-used actions directly in the primary workspace and move the remaining 80% edge-case tools into secondary menus.
- **Anti-Pattern:** Giving every rare administrative setting the same screen real estate as the core daily workflow.

### Parkinson's Law
- **Principle:** Any task will inflate until all of the available time (or space) is spent.
- **Engineering Rule:** Constrain form widths (`max-width: 60ch–72ch` for reading/inputs), set sensible defaults, and streamline checkout/creation flows to minimize time-to-value.
- **Anti-Pattern:** Full-bleed 1400px-wide form inputs and sprawling multi-page workflows for simple tasks.

### Cognitive Bias
- **Principle:** Systematic patterns of deviation from rationality in judgment that shape how users perceive options, defaults, and risk.
- **Engineering Rule:** Use ethical defaults, clear framing (show total price upfront rather than hidden fees), and transparent social proof without dark patterns.
- **Anti-Pattern:** Manipulative confirmshaming copy, pre-checked paid add-ons, or obscured cancellation flows.