# Adult-image sources in Asterion Downloader

Supported **API-based image sources**:

- Rule34.xxx — `index.php?page=dapi&s=post&q=index`, JSON, tag searches.
- Gelbooru — same documented DAPI interface; some API requests require the site's own API key. Unauthorized or rate-limited responses are shown as errors, not bypassed.
- Danbooru — `/posts.json`, documented tags and metadata.
- e621 — `/posts.json`; a distinct User-Agent and throttling are used.

These adapters are **read-only**, fetch up to 8 pages × 75 posts during a backfill, and omit non-image files. Searches can be opened by pasting the site's tag-search page URL, or by selecting a preset and optionally editing the URL to include a `tags` parameter. A source entry is tracked independently from other sites.

**Experimental HTML presets:** ImageFap, Erome, and PornPics use the bounded generic public-HTML adapter. Photo extraction can fail if the site uses JavaScript, authentication, anti-bot protection or nonstandard paging. No claims of full-gallery support are made for these.

## Filtering and storage

- Kyora's global blocked tags still work.
- New **site-specific blocked tags** apply to just the selected website.
- Dedicated booru adapters check tags on **each picture**; banned pictures are skipped before downloading, other pictures in that result page can still download.
- Non-booru adapters retain gallery-level tag exclusions.
- Everything stays in the unified application's existing flat `Downloads/GalleryFlow/` archive without per-cosplay folders, and retains Round Sync TeraBox COPY-task handoff. Kuroha/Pixiv has separate filters and storage.
- Tag filters do not delete previously downloaded content.

API evidence: https://rule34.xxx/?page=help&topic=dapi ; https://gelbooru.com/index.php?id=18780&page=wiki&s=view ; https://shima.donmai.us/wiki_pages/api%3Aposts ; https://e621.net/help/api .

## Limitations

The initial image-source backfill is bounded (600 posts per added booru query per run). A recurring Live check tracks newer results; adding a tag query and running Backfill is the reliable initial ingestion path. Cloud upload completion, real-world site permissions, site API availability, and device authentication require actual account/device testing. Download only media you are authorized to save, from adult-only services; this feature does not access restricted or private content.
