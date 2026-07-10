# Motion Guideline

Use motion to preserve orientation and show state changes, not as decoration.

## Runtime

- Import `m` from `web/src/components/motion-primitives.ts`.
- Keep the root `MotionProvider`: async `LazyMotion` with `domAnimation`, strict
  mode, and `MotionConfig reducedMotion="user"`.
- Animate only opacity and transform for normal UI transitions. Use roughly
  150-200 ms ease-out for enter and a shorter exit.
- Keep dimensions stable while values, icons, and loading states change.

## Where It Belongs

- Route content, compact tabs, short lists, user-triggered inserts/removals, and
  count-up stats.
- Do not wrap long transaction tables, calendar grids, CodeMirror, or the same
  element that dnd-kit transforms while dragging.
- Leave dialogs, dropdowns, sheets, and toasts to their existing shadcn/Sonner
  CSS animation.
- A completed assistant tool workflow stays expanded by default. Completion
  must not collapse transcript height and move surrounding content.

## Verification

Check desktop and narrow mobile viewports, the OS reduced-motion preference,
stable container dimensions, console errors, and interactions after the motion
settles.
