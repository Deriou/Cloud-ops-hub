#!/usr/bin/env node

import { createHash } from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";
import process from "node:process";

function parseArgs(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith("--")) {
      continue;
    }

    const key = token.slice(2);
    const next = argv[index + 1];
    if (!next || next.startsWith("--")) {
      args[key] = true;
      continue;
    }

    args[key] = next;
    index += 1;
  }
  return args;
}

function splitFrontmatter(rawContent) {
  const match = rawContent.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (!match) {
    return { frontmatter: {}, body: rawContent };
  }

  return {
    frontmatter: parseFrontmatterBlock(match[1]),
    body: rawContent.slice(match[0].length)
  };
}

function parseFrontmatterBlock(block) {
  const frontmatter = {};
  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }

    const separator = line.indexOf(":");
    if (separator < 0) {
      continue;
    }

    const key = line.slice(0, separator).trim();
    const value = line.slice(separator + 1).trim();
    frontmatter[key] = parseFrontmatterValue(value);
  }
  return frontmatter;
}

function parseFrontmatterValue(rawValue) {
  if (rawValue === "true") {
    return true;
  }
  if (rawValue === "false") {
    return false;
  }
  if (rawValue.startsWith("[") && rawValue.endsWith("]")) {
    const inner = rawValue.slice(1, -1).trim();
    if (!inner) {
      return [];
    }
    return inner.split(",").map((item) => stripQuotes(item.trim())).filter(Boolean);
  }
  return stripQuotes(rawValue);
}

function stripQuotes(value) {
  if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.slice(1, -1);
  }
  return value;
}

function arrayify(value) {
  if (Array.isArray(value)) {
    return value.filter(Boolean).map((item) => String(item).trim()).filter(Boolean);
  }
  if (typeof value === "string" && value.trim()) {
    return [value.trim()];
  }
  return [];
}

async function walkMarkdownFiles(rootDir) {
  const results = [];
  async function walk(currentDir) {
    const entries = await fs.readdir(currentDir, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.name === ".obsidian" || entry.name === ".git" || entry.name === "node_modules") {
        continue;
      }

      const absolutePath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        await walk(absolutePath);
        continue;
      }

      if (entry.isFile() && entry.name.endsWith(".md") && !entry.name.endsWith(".excalidraw.md")) {
        results.push(absolutePath);
      }
    }
  }

  await walk(rootDir);
  return results;
}

async function buildFileIndex(rootDir) {
  const index = new Map();

  async function walk(currentDir) {
    const entries = await fs.readdir(currentDir, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.name === ".obsidian" || entry.name === ".git" || entry.name === "node_modules") {
        continue;
      }

      const absolutePath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        await walk(absolutePath);
        continue;
      }

      const bucket = index.get(entry.name) ?? [];
      bucket.push(absolutePath);
      index.set(entry.name, bucket);
    }
  }

  await walk(rootDir);
  return index;
}

function toRelativePath(rootDir, absolutePath) {
  return path.relative(rootDir, absolutePath).split(path.sep).join("/");
}

async function resolveLinkedFile({ vaultRoot, noteDir, imageRef, fileIndex }) {
  const normalizedRef = imageRef.replace(/\\/g, "/");
  if (normalizedRef.includes("/")) {
    const candidates = [
      path.resolve(noteDir, normalizedRef),
      path.resolve(vaultRoot, normalizedRef)
    ];

    for (const candidate of candidates) {
      try {
        const stat = await fs.stat(candidate);
        if (stat.isFile()) {
          return candidate;
        }
      } catch {
        // Try next candidate.
      }
    }
  }

  const matches = fileIndex.get(path.basename(normalizedRef)) ?? [];
  if (matches.length === 1) {
    return matches[0];
  }
  if (matches.length > 1) {
    throw new Error(`图片引用 ${imageRef} 命中多个文件，请改成唯一文件名或路径引用`);
  }
  throw new Error(`未找到图片文件：${imageRef}`);
}

async function uploadImage({ baseUrl, opsKey, absolutePath, uploadCache }) {
  if (uploadCache.has(absolutePath)) {
    return uploadCache.get(absolutePath);
  }

  const fileBuffer = await fs.readFile(absolutePath);
  const filename = path.basename(absolutePath);
  const extension = path.extname(filename).toLowerCase();
  const contentType = extension === ".png"
    ? "image/png"
    : extension === ".jpg" || extension === ".jpeg"
      ? "image/jpeg"
      : extension === ".gif"
        ? "image/gif"
        : extension === ".webp"
          ? "image/webp"
          : extension === ".svg"
            ? "image/svg+xml"
            : "application/octet-stream";

  const formData = new FormData();
  formData.append("file", new Blob([fileBuffer], { type: contentType }), filename);

  const response = await fetch(new URL("/api/v1/blog/assets/images", baseUrl), {
    method: "POST",
    headers: {
      "X-Ops-Key": opsKey
    },
    body: formData
  });

  const payload = await response.json();
  if (!response.ok || payload.code !== "OK") {
    throw new Error(payload.message ?? "图片上传失败");
  }

  const assetUrl = new URL(payload.data.path, baseUrl).toString();
  uploadCache.set(absolutePath, assetUrl);
  return assetUrl;
}

async function replaceObsidianImages({ markdown, vaultRoot, notePath, fileIndex, baseUrl, opsKey, uploadCache }) {
  const pattern = /!\[\[([^\]]+)\]\]/g;
  const matches = [...markdown.matchAll(pattern)];
  if (matches.length === 0) {
    return markdown;
  }

  let output = markdown;
  for (const match of matches) {
    const whole = match[0];
    const rawTarget = match[1];
    const [imageRef] = rawTarget.split("|").map((part) => part.trim());
    const absolutePath = await resolveLinkedFile({
      vaultRoot,
      noteDir: path.dirname(notePath),
      imageRef,
      fileIndex
    });
    const imageUrl = await uploadImage({ baseUrl, opsKey, absolutePath, uploadCache });
    const altText = path.basename(imageRef, path.extname(imageRef));
    output = output.replace(whole, `![${altText}](${imageUrl})`);
  }
  return output;
}

function buildContentHash(note) {
  const stablePayload = JSON.stringify({
    noteId: note.noteId,
    title: note.title,
    summary: note.summary ?? null,
    markdownContent: note.markdownContent,
    tags: [...note.tags].sort(),
    categories: [...note.categories].sort(),
    createdAt: note.createdAt ?? null,
    publish: note.publish
  });

  return `sha256:${createHash("sha256").update(stablePayload).digest("hex")}`;
}

async function collectNotes({ vaultRoot, file, baseUrl, opsKey }) {
  const fileIndex = await buildFileIndex(vaultRoot);
  const uploadCache = new Map();
  const markdownFiles = file
    ? [path.resolve(file)]
    : await walkMarkdownFiles(vaultRoot);

  const notes = [];
  for (const notePath of markdownFiles) {
    const rawContent = await fs.readFile(notePath, "utf8");
    const { frontmatter, body } = splitFrontmatter(rawContent);
    if (!frontmatter.noteId) {
      continue;
    }

    const publish = frontmatter.publish === true;
    const title = typeof frontmatter.title === "string" && frontmatter.title.trim()
      ? frontmatter.title.trim()
      : path.basename(notePath, ".md");
    const summary = typeof frontmatter.summary === "string" ? frontmatter.summary.trim() : null;
    const transformedMarkdown = await replaceObsidianImages({
      markdown: body,
      vaultRoot,
      notePath,
      fileIndex,
      baseUrl,
      opsKey,
      uploadCache
    });

    const note = {
      noteId: String(frontmatter.noteId).trim(),
      sourcePath: toRelativePath(vaultRoot, notePath),
      title,
      summary,
      markdownContent: transformedMarkdown,
      tags: arrayify(frontmatter.tags),
      categories: arrayify(frontmatter.categories),
      createdAt: typeof frontmatter.createdAt === "string" && frontmatter.createdAt.trim()
        ? frontmatter.createdAt.trim()
        : null,
      publish
    };
    note.contentHash = buildContentHash(note);
    notes.push(note);
  }

  return notes;
}

async function importNotes({ baseUrl, opsKey, notes }) {
  const response = await fetch(new URL("/api/v1/blog/import/notes:batch", baseUrl), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Ops-Key": opsKey,
      "Accept": "application/json"
    },
    body: JSON.stringify({ notes })
  });

  const payload = await response.json();
  if (!response.ok || payload.code !== "OK") {
    throw new Error(payload.message ?? "笔记导入失败");
  }
  return payload.data;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const vaultRoot = args.vault ? path.resolve(args.vault) : null;
  const baseUrl = args["base-url"] ?? process.env.BLOG_PUBLISH_BASE_URL;
  const opsKey = args["ops-key"] ?? process.env.OPS_AUTH_MASTER_KEY;
  const file = args.file ? path.resolve(args.file) : null;
  const dryRun = args["dry-run"] === true;

  if (!vaultRoot || !baseUrl || !opsKey) {
    console.error("用法: node scripts/obsidian-publish.mjs --vault <obsidian根目录> --base-url <站点地址> --ops-key <主密钥> [--file <单篇笔记>] [--dry-run]");
    process.exit(1);
  }

  const notes = await collectNotes({ vaultRoot, file, baseUrl, opsKey });
  if (notes.length === 0) {
    console.log("未找到可同步的笔记。");
    return;
  }

  if (dryRun) {
    console.log(JSON.stringify({ notes }, null, 2));
    return;
  }

  const result = await importNotes({ baseUrl, opsKey, notes });
  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
