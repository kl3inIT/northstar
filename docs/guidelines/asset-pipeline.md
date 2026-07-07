# Asset Pipeline

Do not hand-author image assets such as favicons, logos, illustrations, OG
images, or decorative SVGs.

Use the image-generation pipeline for designer-owned assets and then verify the
rendered result in the app. Tiny structural SVG wrappers that a developer would
normally type by hand are fine.

For Codex work, use the available image-generation skill. For Claude Code work
without an image model, dispatch the asset task to a Codex worker through the
available orchestration flow, then verify the returned file.
