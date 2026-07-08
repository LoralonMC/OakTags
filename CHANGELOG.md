# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `silent` option for `/tags voucher` command — suppresses the notification to the receiving player

### Changed

- Updated comments in the default `config.yml` are now synced onto existing config files on plugin updates, without clobbering admin-customized comments (OakheartLib 1.3.0 comment-sync).

### Fixed

- Tag vouchers can now be redeemed by right-clicking in the air, not just against a block. Previously air right-clicks were silently ignored.
- Tag vouchers (name tags) can no longer rename mobs, animals, or other entities when right-clicked on them — the interaction is cancelled and the voucher is redeemed instead.
- Equipped permission-based tags are now auto-unequipped when a player loses access (e.g. a backing group/permission is removed). Previously the tag kept displaying in chat. Re-validated on join, on chat/placeholder display, and when opening the tags GUI.

## [1.1.0] - 2026-03-04

### Changed

- `/tags give` and `/tags revoke` now support offline players

### Fixed

- Empty lore lines in GUI button configs now render as blank spacers instead of being skipped

## [1.0.0] - 2026-03-03

### Added

- Unified chat tag system with GUI, vouchers, and PlaceholderAPI integration
- Define tags in `tags.yml` across customizable categories
- Dual unlock system: permission-based (from rank plugins) and grant-based (via commands/vouchers)
- Paginated dynamic-size tag selector GUI with 5 sort modes and category/status filtering
- Tag favorites — right-click tags in the GUI to favorite, filter to show only favorites
- Template-based tag lore layout via `gui.tag-lore.layout` and `admin-gui.tag-lore.layout`
- Per-player sort/filter preferences persisted to SQLite
- In-game Tag Editor GUI for creating and editing tags with live preview (`/tags create`, `/tags edit`)
- Line-based YAML writer that preserves comments and formatting in tags.yml
- Admin GUI for viewing and managing any player's tags (`/tags <player>`, online and offline)
- Physical voucher items with right-click redemption and confirmation GUI
- `oaktags-redeem` bridge command for ExecutableItems/DeluxeMenu voucher integration
- Per-tag voucher customization (material, name, lore, glow) with global defaults
- `%oaktags_prefix%` and `%oaktags_prefix+%` placeholders for chat formatters with rank prefix fallback
- `oaktags.stack` permission for showing both rank prefix and equipped tag
- PlaceholderAPI integration: `%oaktags_tag%`, `%oaktags_tag_name%`, `%oaktags_unlocked%`, `%oaktags_total%`, `%oaktags_has_<id>%`, `%oaktags_count_<id>%`
- All messages fully configurable with MiniMessage format
- SQLite storage with WAL mode, async batch saves, and dirty tracking
- bStats metrics integration
