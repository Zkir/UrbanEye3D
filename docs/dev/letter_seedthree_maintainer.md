# Letter to SeedThree maintainer — DRAFT (not sent)

<!-- Проверить перед отправкой: подпись, адресата (SkyeShark), ветку-ссылку на report (blob/HEAD). -->

---

Subject: Thank you — and a coverage request from the UrbanEye3D / OpenStreetMap side

Dear SkyeShark,

First of all — thank you for SeedThree. It is a genuinely lovely piece of work, and it solved a
problem for us that fewer projects than you might think take seriously: the presets. A generator
that produces "a tree" or "an oak-ish thing" is easy to find; a generator whose presets carry
*species* — correct Latin binomials, morphologies dialed to reference photos, even real fruit on
the orchard species — is rare. The clean headless API (`createTree` + the impostor bake) also made
integrating it a weekend job rather than a fight. That engineering courtesy deserves a thank-you
of its own.

We use SeedThree to generate tree textures for **UrbanEye3D**, a 3D-viewing plugin for JOSM (the
most popular editor of OpenStreetMap). Our species-specific tree textures — 20 of them today —
are procedural renders of SeedThree's presets, credited to you (MIT) in our asset list.

- Project: <https://github.com/Zkir/UrbanEye3D>
- Coverage report with thumbnails: <https://github.com/Zkir/UrbanEye3D/blob/HEAD/docs/seedthree_coverage.md>

At this stage we deliberately limit ourselves to **pre-baked billboard textures**: our 3D engine's
performance does not yet allow more. In the future we hope to move to polygonal tree models and
eventually on-the-fly generation — if we manage the engine optimizations that would make that
affordable.

The one problem where we genuinely need your project's help is **coverage**. Twenty presets is more
than some comparable generators offer, but for OpenStreetMap it is still very little: the species
most present in OSM — the European urban flora — mostly have no preset at all. In our ranking of
species by real OSM tag occurrences (whole planet), the *first* species SeedThree can draw already
sits at rank **26**. Our top ten, by tagged trees in OSM:

| # | species | OSM occurrences |
|---:|---|---:|
| 1 | *Acer platanoides* (Norway maple) | 112,252 |
| 2 | *Quercus robur* (English oak) | 84,802 |
| 3 | *Tilia cordata* (small-leaved lime) | 75,048 |
| 4 | *Aesculus hippocastanum* (horse-chestnut) | 68,069 |
| 5 | *Tilia × europaea* (large-leaved lime) | 60,777 |
| 6 | *Platanus × hispanica* (London plane) | 52,030 |
| 7 | *Fraxinus excelsior* (European ash) | 50,543 |
| 8 | *Acer pseudoplatanus* (sycamore maple) | 46,278 |
| 9 | *Juglans regia* (walnut) | 38,233 |
| 10 | *Olea europaea* (olive) | 37,472 |

Just these ten species account for more than 625,000 tagged trees in OSM — and none of them has a
preset today.

We would be thrilled if SeedThree's species list could grow, and we hope that at least these ten
would find their way in. We believe the best outcome is that *you and SeedThree's contributors*
make them: you understand tree morphology far better than we do, and a species preset born inside
the project will look right in a way our borrowed approximations never would.

In parallel we plan to raise this request on the OpenStreetMap forum — our community maps these
trees every day and would love to render them correctly. If there is any guidance you could point
us and them to for authoring species presets (your README's agent-workflow section is already a
great starting point — perhaps a "how to make a good preset" note, or what quality bar a preset
should pass to be accepted upstream), we would be very grateful, and we will gladly relay it.

Thank you again for SeedThree — our forest outside the window already looks noticeably more
truthful because of it, and it will only improve. We would be happy to stay in touch, report how
the textures behave in real maps, and help with OSM-side data (species distributions, frequency
statistics) whenever that might be useful to you.

With warm regards and respect,

Zkir
UrbanEye3D maintainer

---

# OSM forum post -- DRAFT (not posted)

## Trees with names: species textures in UrbanEye3D -- and an invitation to grow them

Hi everyone,

good news first: the 3D window in **UrbanEye3D** (the JOSM plugin) now renders trees by species --
procedurally, openly, for free. Every texture is a render of a preset from
[SeedThree](https://github.com/SkyeShark/SeedThree), a gorgeous MIT-licensed procedural plant
generator whose presets are *actual species*, with Latin names and morphologies dialed to
reference photos. Tag a tree `species=Prunus avium` and you will see a sweet cherry, not a generic
green blob; thanks to POWO synonyms, `species=Cerasus avium` works too. You can browse all the
textures here: <https://github.com/Zkir/UrbanEye3D/blob/HEAD/docs/seedthree_coverage.md>

Now the bad news, and the reason for this post: SeedThree knows exactly **20 species, all North
American**. The trees that we actually map are largely absent. We mined the planet file for real
`species=*` tag frequencies; in our top-150 ranking the first species SeedThree can draw sits at
rank 26, and our entire top ten has nothing:

| # | species | tagged trees in OSM |
|---:|---|---:|
| 1 | *Acer platanoides* (Norway maple) | 112,252 |
| 2 | *Quercus robur* (English oak) | 84,802 |
| 3 | *Tilia cordata* (small-leaved lime) | 75,048 |
| 4 | *Aesculus hippocastanum* (horse-chestnut) | 68,069 |
| 5 | *Tilia x europaea* (large-leaved lime) | 60,777 |
| 6 | *Platanus x hispanica* (London plane) | 52,030 |
| 7 | *Fraxinus excelsior* (European ash) | 50,543 |
| 8 | *Acer pseudoplatanus* (sycamore maple) | 46,278 |
| 9 | *Juglans regia* (walnut) | 38,233 |
| 10 | *Olea europaea* (olive) | 37,472 |

Just these ten species account for over 625,000 mapped trees -- and SeedThree cannot draw a single one of them. So here is the invitation: **drop everything and go write tree
presets** :)

And I mean that as a practical proposal, not a meme -- SeedThree is built so that adding a species
touches *no engine code*: you write one preset file (a handful of Weber-Penn parameters for broad-
and conifers, an L-system for succulents), generate two images (a tileable bark, a leaf card on
magenta) with any image model, run the project's texture scripts, register the species. The README
has a whole "Adding a species (agent workflow)" section -- it is explicitly designed to be done by
a person + a coding agent + an image model.

The bottleneck is not code. It is knowing what a small-leaved lime crown actually looks like --
branch angles, leaf size, the habit -- and *that* knowledge is exactly what tree mappers in this
community have. You tag these trees; you know them better than any generator author.

So: pick your favorite tree (or just start at the table above), make a preset, send a pull request
to SeedThree. We will do the rest -- bake the textures into the plugin, wire the MapCSS species
rules, update the coverage report. If you want OSM ammunition for tuning your preset, ask us: we
can produce per-species occurrence counts by region, `diameter_crown`/`height` distributions,
whatever helps the preset match reality.

The full top-150 with every species and its current texture situation is in the coverage report
linked above -- start wherever your feet are planted.

And thank you for every tree you have already tagged. It is now starting to pay off in 3D.

Zkir
UrbanEye3D maintainer

---