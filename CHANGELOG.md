# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Unified chat tag system with GUI, vouchers, and PlaceholderAPI integration
- Tag favorites — right-click tags in the GUI to favorite, use the filter button to show only favorites
- Template-based tag lore layout — server owners can fully customize the order and content of tag lore lines in the GUI via `gui.tag-lore.layout` and `admin-gui.tag-lore.layout`
- Define tags in `tags.yml` across customizable categories
- Dual unlock system: permission-based (from rank plugins) and grant-based (via commands/vouchers)
- Paginated dynamic-size tag selector GUI with 5 sort modes and category/status filtering
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
