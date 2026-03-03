package dev.oakheart.oaktags.util;

import dev.oakheart.oaktags.model.TagDefinition;
import dev.oakheart.oaktags.model.UnlockType;
import dev.oakheart.oaktags.model.VoucherConfig;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TagsYamlWriter {
    private final File tagsFile;
    private final Logger logger;

    public TagsYamlWriter(File tagsFile, Logger logger) {
        this.tagsFile = tagsFile;
        this.logger = logger;
    }

    public synchronized boolean writeNewTag(TagDefinition tag) {
        try {
            List<String> lines = Files.readAllLines(tagsFile.toPath(), StandardCharsets.UTF_8);

            // Ensure blank line before new tag block
            if (!lines.isEmpty() && !lines.getLast().isEmpty()) {
                lines.add("");
            }

            lines.addAll(generateTagLines(tag));

            atomicWrite(lines);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to write new tag '" + tag.getId() + "' to tags.yml", e);
            return false;
        }
    }

    public synchronized boolean replaceTagBlock(TagDefinition tag) {
        try {
            List<String> lines = Files.readAllLines(tagsFile.toPath(), StandardCharsets.UTF_8);
            int[] block = findTagBlock(lines, tag.getId());
            if (block == null) {
                logger.warning("Could not find tag block '" + tag.getId() + "' in tags.yml for replacement");
                return false;
            }

            // Remove old block
            for (int i = block[1] - 1; i >= block[0]; i--) {
                lines.remove(i);
            }

            // Insert new block at the same position
            List<String> newLines = generateTagLines(tag);
            lines.addAll(block[0], newLines);

            atomicWrite(lines);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to replace tag block '" + tag.getId() + "' in tags.yml", e);
            return false;
        }
    }

    public synchronized boolean deleteTag(String id) {
        try {
            List<String> lines = Files.readAllLines(tagsFile.toPath(), StandardCharsets.UTF_8);
            int[] block = findTagBlock(lines, id);
            if (block == null) {
                logger.warning("Could not find tag block '" + id + "' in tags.yml for deletion");
                return false;
            }

            // Also remove preceding blank line if present
            int start = block[0];
            if (start > 0 && lines.get(start - 1).isEmpty()) {
                start--;
            }

            for (int i = block[1] - 1; i >= start; i--) {
                lines.remove(i);
            }

            atomicWrite(lines);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to delete tag '" + id + "' from tags.yml", e);
            return false;
        }
    }

    private void atomicWrite(List<String> lines) throws IOException {
        Path target = tagsFile.toPath();
        Path temp = target.resolveSibling(tagsFile.getName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Finds the line range [startLine, endLine) for a tag block by its ID.
     * A tag block starts with an unindented "id:" line and ends at the next
     * unindented non-blank, non-comment line or EOF.
     */
    int[] findTagBlock(List<String> lines, String id) {
        String target = id + ":";
        int startLine = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // Match unindented tag key (not indented, not a comment)
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0))
                    && !line.startsWith("#") && line.startsWith(target)) {
                // Ensure exact match: line is either "id:" or "id: ..."
                if (line.length() == target.length() || line.charAt(target.length()) == ' ') {
                    startLine = i;
                    break;
                }
            }
        }

        if (startLine == -1) return null;

        // Scan forward to find end of block
        int endLine = lines.size();
        for (int i = startLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) continue;
            // Another unindented key means end of this block
            if (!Character.isWhitespace(line.charAt(0))) {
                endLine = i;
                break;
            }
        }

        return new int[]{startLine, endLine};
    }

    List<String> generateTagLines(TagDefinition tag) {
        List<String> lines = new ArrayList<>();

        lines.add(tag.getId() + ":");
        lines.add("  display: '" + escapeYamlSingleQuote(tag.getDisplay()) + "'");
        lines.add("  category: " + tag.getCategory());
        lines.add("  unlock-type: " + tag.getUnlockType().name().toLowerCase());

        if (tag.getUnlockType() == UnlockType.PERMISSION) {
            lines.add("  unlock-permission: " + tag.getUnlockPermission());
        }

        if (tag.getMaterial() != Material.NAME_TAG) {
            lines.add("  material: " + tag.getMaterial().name());
        }

        if (tag.isHidden()) {
            lines.add("  hidden: true");
        }

        if (tag.getLore() != null && !tag.getLore().isEmpty()) {
            lines.add("  lore:");
            for (String loreLine : tag.getLore()) {
                lines.add("    - '" + escapeYamlSingleQuote(loreLine) + "'");
            }
        }

        VoucherConfig vc = tag.getVoucherConfig();
        if (vc != null) {
            lines.add("  voucher:");
            lines.add("    material: " + vc.getMaterial().name());
            lines.add("    name: '" + escapeYamlSingleQuote(vc.getName()) + "'");
            if (vc.getLore() != null && !vc.getLore().isEmpty()) {
                lines.add("    lore:");
                for (String loreLine : vc.getLore()) {
                    lines.add("      - '" + escapeYamlSingleQuote(loreLine) + "'");
                }
            }
            lines.add("    glow: " + vc.isGlow());
        }

        return lines;
    }

    private String escapeYamlSingleQuote(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }
}
