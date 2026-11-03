# Font Setup

Download the following font files from Google Fonts and place them in this directory (`app/src/main/res/font/`).

All filenames must be lowercase with underscores (Android resource naming rules).

## Required files

| File name                       | Font                      | Weight    | Source                                           |
|---------------------------------|---------------------------|-----------|--------------------------------------------------|
| `playfair_display_medium.ttf`   | Playfair Display          | 500       | https://fonts.google.com/specimen/Playfair+Display |
| `playfair_display_semibold.ttf` | Playfair Display          | 600       | Same as above                                    |
| `playfair_display_bold.ttf`     | Playfair Display          | 700       | Same as above                                    |
| `inter_regular.ttf`             | Inter                     | 400       | https://fonts.google.com/specimen/Inter          |
| `inter_medium.ttf`              | Inter                     | 500       | Same as above                                    |
| `inter_semibold.ttf`            | Inter                     | 600       | Same as above                                    |

## Quick download (macOS terminal)

```bash
# Install fonttools if needed: pip3 install fonttools

# Playfair Display — download the variable font and subset if preferred
# Or download static fonts directly from the Google Fonts GitHub repo:
# https://github.com/google/fonts/tree/main/ofl/playfairdisplay/static

# Inter static fonts:
# https://github.com/google/fonts/tree/main/ofl/inter/static
```

## Alternative: Use Downloadable Fonts API

Instead of bundled fonts, you can configure downloadable fonts in `res/xml/`.
See: https://developer.android.com/develop/ui/views/text/downloadable-fonts

For offline-first requirements, bundled fonts are recommended.
