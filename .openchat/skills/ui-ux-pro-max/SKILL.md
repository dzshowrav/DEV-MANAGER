---
name: ui-ux-pro-max
description: "UI/UX design intelligence for web and mobile. Searchable local database with 84 styles, 192 color palettes, 74 font pairings, 192 product types, 98 UX guidelines, 104 icon entries, 16 GSAP motion presets, and 25 chart types across 22 stacks. Use when designing, building, or reviewing UI: pages, components, color schemes, typography, layout, accessibility, animation, or data visualization."
license: MIT
metadata:
  author: nextlevelbuilder
  version: "2.0.0"
  category: design
  tags: ["ui", "ux", "design", "design-system", "colors", "typography", "accessibility"]
---

# UI/UX Pro Max - Design Intelligence

Searchable database of UI/UX design rules with priority-based recommendations: 84 styles, 192 color palettes, 74 font pairings, 192 product types with reasoning rules, 98 UX guidelines, and 25 chart types across 22 technology stacks.

## When to Apply

Use when the task involves **UI structure, visual design decisions, interaction patterns, or user experience quality control**.

## Rule Categories by Priority

| Priority | Category | Impact | Key Checks | Anti-Patterns |
|----------|----------|--------|------------|---------------|
| 1 | Accessibility | CRITICAL | Contrast 4.5:1, Alt text, Keyboard nav | Removing focus rings, Icon-only buttons without labels |
| 2 | Touch & Interaction | CRITICAL | Min size 44×44px, 8px+ spacing | Reliance on hover only, Instant state changes |
| 3 | Performance | HIGH | WebP/AVIF, Lazy loading, CLS < 0.1 | Layout thrashing, Cumulative Layout Shift |
| 4 | Style Selection | HIGH | Match product type, SVG icons | Mixing flat & skeuomorphic, Emoji as icons |
| 5 | Layout & Responsive | HIGH | Mobile-first, Viewport meta | Horizontal scroll, Fixed px widths |
| 6 | Typography & Color | MEDIUM | Base 16px, Line-height 1.5 | Text < 12px body, Gray-on-gray |
| 7 | Animation | MEDIUM | Duration 150-300ms, Purposeful motion | Decorative-only animation, No reduced-motion |
| 8 | Forms & Feedback | MEDIUM | Visible labels, Error near field | Placeholder-only label, Errors only at top |
| 9 | Navigation | HIGH | Predictable back, Bottom nav ≤5 | Overloaded nav, Broken back behavior |
| 10 | Charts & Data | LOW | Legends, Tooltips | Relying on color alone |

## Workflow

### Step 1: Analyze Requirements
Extract product type, target audience, style keywords, and detect stack from project files.

### Step 2: Generate Design System
```bash
python scripts/search.py "<product_type> <industry> <keywords>" --design-system -p "Project Name"
```

### Step 3: Domain Searches
```bash
python scripts/search.py "<keyword>" --domain <domain>
```

### Step 4: Stack Guidelines
```bash
python scripts/search.py "<keyword>" --stack <stack>
```

## Supported Stacks

React, Next.js, Vue, Nuxt, Svelte, Astro, SwiftUI, React Native, Flutter, Tailwind, shadcn/ui, Jetpack Compose, Angular, Laravel, Three.js, HTML/CSS, and more.

## Tips

- Use multi-dimensional keywords: combine product + industry + tone + density
- Try different phrasings: `"playful neon"` -> `"vibrant dark"` -> `"content-first minimal"`
- Always use `--design-system` first, then `--domain` for deep-dives
