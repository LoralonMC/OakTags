# OakTags

Unified chat tag system with GUI, vouchers, and PlaceholderAPI integration.

## Features

- Define tags in `tags.yml` across customizable categories
- Dual unlock system: permission-based tags (from rank plugins) and grant-based tags (via commands/vouchers)
- Paginated, dynamic-size tag selector GUI with sort and filter
- In-game Tag Editor GUI for creating and editing tags with live preview
- Physical voucher items that players right-click to redeem tags, with confirmation GUI
- Per-tag voucher customization (material, name, lore, glow)
- Admin GUI for viewing and managing any player's tags (online or offline)
- PlaceholderAPI placeholders for chat formatting and scoreboards
- Tag favorites — right-click any tag to favorite it, then filter to show only favorites
- Per-player sort/filter preferences saved across sessions
- Claim count tracking per tag (visible in GUI lore and as a placeholder)
- All messages fully configurable with MiniMessage format

## Requirements

- Paper 1.21.8+
- Java 21
- PlaceholderAPI (optional, for placeholders)

## Installation

1. Drop the JAR into your `plugins/` folder
2. Restart the server
3. Edit `plugins/OakTags/config.yml` for plugin settings
4. Edit `plugins/OakTags/tags.yml` to add, remove, or modify tag definitions

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/tags` | Open the tag selector GUI | `oaktags.use` |
| `/tags help` | Show help | `oaktags.use` |
| `/tags reload` | Reload configuration | `oaktags.reload` |
| `/tags give <player> <tag>` | Grant a tag to a player | `oaktags.give` |
| `/tags revoke <player> <tag>` | Revoke a tag from a player | `oaktags.revoke` |
| `/tags create <id>` | Create a tag (opens editor GUI) | `oaktags.create` |
| `/tags edit <id>` | Edit a tag (opens editor GUI) | `oaktags.create` |
| `/tags delete <id>` | Delete a tag | `oaktags.delete` |
| `/tags voucher <tag> [player] [amount]` | Give tag voucher items | `oaktags.voucher` |
| `/tags list [player]` | List unlocked tags | `oaktags.list` |
| `/tags <player>` | Open admin tag management GUI | `oaktags.admin` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `oaktags.*` | All OakTags permissions | op |
| `oaktags.use` | Open the tags GUI and equip tags | true |
| `oaktags.reload` | Reload plugin configuration | op |
| `oaktags.give` | Grant tags to players | op |
| `oaktags.revoke` | Revoke tags from players | op |
| `oaktags.create` | Create, edit, and delete tags | op |
| `oaktags.delete` | Delete tags | op |
| `oaktags.voucher` | Give tag voucher items | op |
| `oaktags.list` | List unlocked tags for any player | op |
| `oaktags.admin` | Open admin tag management GUI | op |
| `oaktags.stack` | Show both rank prefix and tag in `%oaktags_prefix%` | op |

## Configuration

Tags are defined in `tags.yml` with the following structure:

```yaml
tag-id:
  display: '<MiniMessage display string>'
  category: general
  unlock-type: granted
  material: NAME_TAG
  lore:
    - '<#6C757D>Obtained from source.'
    - '<#6C757D>Tags claimed: <#FCD472><count>'
  voucher:              # Optional per-tag voucher customization
    material: NAME_TAG
    name: '<tag> <#f2ebd7>Chat Tag'
    lore:
      - '<#6C757D>Right-click to redeem.'
    glow: true
```

Categories, GUI settings, voucher defaults, and all messages are configured in `config.yml`.

## Placeholders

Requires PlaceholderAPI.

| Placeholder | Description |
|-------------|-------------|
| `%oaktags_prefix%` | Chat prefix (tag if equipped, rank prefix fallback if not) |
| `%oaktags_prefix+%` | Same as above, with automatic trailing space (empty when blank) |
| `%oaktags_tag%` | Active tag display (MiniMessage string) or empty |
| `%oaktags_tag_name%` | Active tag ID or empty |
| `%oaktags_unlocked%` | Number of unlocked tags |
| `%oaktags_total%` | Total number of non-hidden tags |
| `%oaktags_has_<tagid>%` | Whether the player has a specific tag (`true`/`false`) |
| `%oaktags_count_<tagid>%` | Number of players who have claimed a specific tag |
